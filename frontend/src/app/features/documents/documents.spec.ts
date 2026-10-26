import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Documents } from './documents';
import { AuthService } from '../../core/services/auth-service';
import { AuthResponse } from '../../core/models/auth.model';
import { DocumentSummary } from '../../core/models/documents.model';

function summary(overrides: Partial<DocumentSummary>): DocumentSummary {
  return {
    id: '7c1e4a52-0d3b-4f6e-9a21-000000000000',
    filename: 'documento.pdf',
    fileSize: 1_000_000,
    uploadedAt: '2026-09-18T10:42:00Z',
    uploadedBy: 'uploader',
    status: 'PENDING',
    chunkCount: null,
    rejectionReason: null,
    universe: 'The Legend of Zelda',
    work: null,
    lang: 'es',
    title: null,
    canonicity: 'CANON',
    documentType: null,
    ...overrides,
  };
}

const HYRULE = summary({ id: 'id-hyrule', filename: 'cronicas-de-hyrule.pdf', uploadedAt: '2026-09-18T10:42:00Z' });
const ELDEN = summary({ id: 'id-elden', filename: 'bestiario-elden-ring.docx', uploadedBy: 'jorge.ruiz', universe: 'Elden Ring' });
const WITCHER = summary({
  id: 'id-witcher',
  filename: 'compendio-the-witcher.pdf',
  uploadedBy: 'admin',
  status: 'APPROVED',
  chunkCount: 402,
  universe: 'The Witcher',
  uploadedAt: '2026-09-10T11:58:00Z',
});
const ORPHAN = summary({ id: 'id-orphan', filename: 'objetos-dark-souls.txt', uploadedBy: null, status: 'REJECTED', universe: 'Dark Souls' });

describe('Documents', () => {
  const baseUrl = '/api/v1/documents';
  const user = signal<AuthResponse | null>(null);

  let component: Documents;
  let fixture: ComponentFixture<Documents>;
  let httpMock: HttpTestingController;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const byName = (filename: string) => component.documents().find(doc => doc.filename === filename)!;
  const flushList = (docs: DocumentSummary[]) => httpMock.expectOne({ method: 'GET', url: baseUrl }).flush(docs);

  async function setup(current: AuthResponse, docs: DocumentSummary[]) {
    user.set(current);

    await TestBed.configureTestingModule({
      imports: [Documents],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { user } }],
    }).compileComponents();

    fixture = TestBed.createComponent(Documents);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    flushList(docs);
    await fixture.whenStable();
  }

  function fillMetadata() {
    component.metadataForm.patchValue({ universe: 'The Witcher', lang: 'es', canonicity: 'CANON' });
  }

  afterEach(() => {
    httpMock.verify();
  });

  describe('as ADMIN_DOCUMENTS', () => {
    beforeEach(() => setup({ username: 'admin', roles: ['ADMIN_DOCUMENTS'] }, [HYRULE, ELDEN, WITCHER, ORPHAN]));

    it('lists every document with the review actions', () => {
      expect(component.documents().length).toBe(4);
      expect(component.counts().PENDING).toBe(2);
      expect(text()).toContain('Aprobar');
    });

    it('shows a placeholder for documents whose uploader was deleted', () => {
      expect(text()).toContain('Usuario eliminado');
    });

    it('filters by status and by search term, including the universe', () => {
      component.setFilter('APPROVED');
      expect(component.filtered().every(doc => doc.status === 'APPROVED')).toBe(true);

      component.setFilter('ALL');
      component.search.set('elden');
      expect(component.filtered().map(doc => doc.filename)).toEqual(['bestiario-elden-ring.docx']);
    });

    it('loads the extracted text into the preview', () => {
      component.openPreview(HYRULE);

      httpMock.expectOne(`${baseUrl}/id-hyrule/content`).flush({ id: 'id-hyrule', content: 'Capítulo I' });

      expect(component.previewText()).toBe('Capítulo I');
    });

    it('approves a pending document and reloads the list to pick up the chunk count', () => {
      component.approve(HYRULE);

      httpMock.expectOne({ method: 'POST', url: `${baseUrl}/id-hyrule/approve` }).flush(null);
      flushList([{ ...HYRULE, status: 'APPROVED', chunkCount: 12 }, ELDEN, WITCHER, ORPHAN]);

      expect(byName('cronicas-de-hyrule.pdf').status).toBe('APPROVED');
      expect(byName('cronicas-de-hyrule.pdf').chunkCount).toBe(12);
      expect(component.notice()?.kind).toBe('success');
      expect(component.busyId()).toBeNull();
    });

    it('reports a document another admin already reviewed and refreshes the list', () => {
      component.approve(HYRULE);

      httpMock
        .expectOne(`${baseUrl}/id-hyrule/approve`)
        .flush({ message: 'Document is APPROVED' }, { status: 409, statusText: 'Conflict' });
      flushList([{ ...HYRULE, status: 'APPROVED' }, ELDEN, WITCHER, ORPHAN]);

      expect(component.notice()?.kind).toBe('danger');
      expect(component.notice()?.text).toContain('Otro administrador ya lo ha revisado');
      expect(byName('cronicas-de-hyrule.pdf').status).toBe('APPROVED');
    });

    it('rejects with the optional reason and closes the dialog', () => {
      component.openReject(ELDEN);
      component.rejectReason.set('  Contenido duplicado  ');

      component.confirmReject();

      const request = httpMock.expectOne({ method: 'POST', url: `${baseUrl}/id-elden/reject` });
      expect(request.request.body).toEqual({ reason: 'Contenido duplicado' });
      request.flush(null);
      flushList([HYRULE, { ...ELDEN, status: 'REJECTED', rejectionReason: 'Contenido duplicado' }, WITCHER, ORPHAN]);

      expect(byName('bestiario-elden-ring.docx').status).toBe('REJECTED');
      expect(component.dialog()).toBeNull();
    });

    it('removes a document once the deletion is confirmed', () => {
      component.openDelete(WITCHER);

      component.confirmDelete();
      httpMock.expectOne({ method: 'DELETE', url: `${baseUrl}/id-witcher` }).flush(null);

      expect(component.documents().some(doc => doc.id === 'id-witcher')).toBe(false);
      expect(component.dialog()).toBeNull();
    });

    it('asks for the metadata before uploading and submits it with the file', () => {
      component.uploadFiles([new File(['texto'], 'nuevo-lore.pdf')]);

      // Nothing is sent until the metadata dialog is submitted.
      httpMock.expectNone(`${baseUrl}/submit`);
      expect(component.pendingFiles()?.map(file => file.name)).toEqual(['nuevo-lore.pdf']);

      fillMetadata();
      component.metadataForm.controls.title.setValue('Bestiario completo');
      component.submitMetadata();

      const request = httpMock.expectOne({ method: 'POST', url: `${baseUrl}/submit` });
      const body = request.request.body as FormData;
      expect(body.get('universe')).toBe('The Witcher');
      expect(body.get('canonicity')).toBe('CANON');
      expect(body.get('title')).toBe('Bestiario completo');
      request.flush({ id: 'id-new', filename: 'nuevo-lore.pdf', status: 'PENDING', universe: 'The Witcher', lang: 'es' });
      flushList([summary({ id: 'id-new', filename: 'nuevo-lore.pdf' }), HYRULE, ELDEN, WITCHER, ORPHAN]);

      expect(component.pendingFiles()).toBeNull();
      expect(component.uploads()[0].state).toBe('done');
      expect(byName('nuevo-lore.pdf').status).toBe('PENDING');
    });

    it('keeps the dialog open while the required metadata is missing', () => {
      component.uploadFiles([new File(['texto'], 'nuevo-lore.pdf')]);

      component.submitMetadata();

      httpMock.expectNone(`${baseUrl}/submit`);
      expect(component.pendingFiles()).not.toBeNull();
      expect(component.isMetadataInvalid('universe')).toBe(true);
      expect(component.isMetadataInvalid('canonicity')).toBe(true);
    });

    it('applies the metadata to every file in a batch, without a shared title', () => {
      component.uploadFiles([new File(['a'], 'uno.pdf'), new File(['b'], 'dos.md')]);
      fillMetadata();
      component.metadataForm.controls.title.setValue('Título que no debe enviarse');

      component.submitMetadata();

      const requests = httpMock.match({ method: 'POST', url: `${baseUrl}/submit` });
      expect(requests.length).toBe(2);
      expect(requests.every(request => !(request.request.body as FormData).has('title'))).toBe(true);
      requests.forEach(request => request.flush({ id: 'x', filename: 'x', status: 'PENDING', universe: 'The Witcher', lang: 'es' }));
      httpMock.match({ method: 'GET', url: baseUrl }).forEach(request => request.flush([HYRULE]));
    });

    it('reports a duplicate upload', () => {
      component.uploadFiles([new File(['texto'], 'repetido.pdf')]);
      fillMetadata();
      component.submitMetadata();

      httpMock.expectOne(`${baseUrl}/submit`).flush({ message: 'Duplicate' }, { status: 409, statusText: 'Conflict' });

      expect(component.uploads()[0].state).toBe('error');
      expect(component.uploads()[0].error).toBe('Este documento ya está en el archivo');
    });

    it('explains why the backend refused the file', () => {
      component.uploadFiles([new File(['x'], 'escaneado.pdf'), new File(['y'], 'renombrado.txt'), new File([''], 'vacio.md')]);
      fillMetadata();
      component.submitMetadata();

      const [scanned, renamed, empty] = httpMock.match(`${baseUrl}/submit`);
      scanned.flush({ message: 'No text' }, { status: 422, statusText: 'Unprocessable Content' });
      renamed.flush({ message: 'Unsupported type' }, { status: 415, statusText: 'Unsupported Media Type' });
      empty.flush({ message: 'The file is empty' }, { status: 400, statusText: 'Bad Request' });

      const errorOf = (name: string) => component.uploads().find(item => item.name === name)?.error;
      expect(errorOf('escaneado.pdf')).toBe('No se pudo extraer texto del archivo');
      expect(errorOf('renombrado.txt')).toBe('Formato no admitido');
      expect(errorOf('vacio.md')).toBe('El archivo está vacío o su nombre no es válido');
    });

    it('refuses unsupported formats without asking for metadata', () => {
      component.uploadFiles([new File(['x'], 'portada.png')]);

      expect(component.pendingFiles()).toBeNull();
      expect(component.uploads()[0].state).toBe('error');
    });
  });

  describe('as UPLOADER', () => {
    beforeEach(() => setup({ username: 'uploader', roles: ['UPLOADER'] }, [HYRULE]));

    it('shows their submissions without review actions', () => {
      expect(component.documents().length).toBe(1);
      expect(text()).toContain('Mis envíos');
      expect(text()).not.toContain('Aprobar');
    });
  });
});
