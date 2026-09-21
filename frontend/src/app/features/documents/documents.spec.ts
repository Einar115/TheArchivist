import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Documents } from './documents';
import { AuthService } from '../../core/services/auth-service';
import { DOCUMENTS_MOCK_LATENCY } from '../../core/services/document-service';
import { AuthResponse } from '../../core/models/auth.model';

describe('Documents', () => {
  const ingestUrl = '/api/v1/documents/ingest';
  const user = signal<AuthResponse | null>(null);

  let component: Documents;
  let fixture: ComponentFixture<Documents>;
  let httpMock: HttpTestingController;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const byName = (filename: string) => component.documents().find(doc => doc.filename === filename)!;

  async function setup(current: AuthResponse) {
    user.set(current);

    await TestBed.configureTestingModule({
      imports: [Documents],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // The mocked operations then resolve synchronously, so no fake timers are needed.
        { provide: DOCUMENTS_MOCK_LATENCY, useValue: 0 },
        { provide: AuthService, useValue: { user } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Documents);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  }

  afterEach(() => {
    httpMock.verify();
  });

  describe('as ADMIN_DOCUMENTS', () => {
    beforeEach(() => setup({ username: 'admin', roles: ['ADMIN_DOCUMENTS'] }));

    it('lists every document with the review actions', () => {
      expect(component.documents().length).toBe(8);
      expect(component.counts().PENDING).toBe(3);
      expect(text()).toContain('Aprobar');
    });

    it('filters by status and by search term', () => {
      component.setFilter('APPROVED');
      expect(component.filtered().every(doc => doc.status === 'APPROVED')).toBe(true);

      component.setFilter('ALL');
      component.search.set('witcher');
      expect(component.filtered().map(doc => doc.filename)).toEqual(['compendio-the-witcher.pdf']);
    });

    it('approves a pending document and reports it', () => {
      component.approve(byName('cronicas-de-hyrule.pdf'));

      expect(byName('cronicas-de-hyrule.pdf').status).toBe('APPROVED');
      expect(byName('cronicas-de-hyrule.pdf').chunkCount).toBeGreaterThan(0);
      expect(component.notice()?.kind).toBe('success');
    });

    it('rejects with the optional reason and closes the dialog', () => {
      component.openReject(byName('bestiario-elden-ring.docx'));
      component.rejectReason.set('Contenido duplicado');

      component.confirmReject();

      expect(byName('bestiario-elden-ring.docx').status).toBe('REJECTED');
      expect(byName('bestiario-elden-ring.docx').rejectionReason).toBe('Contenido duplicado');
      expect(component.dialog()).toBeNull();
    });

    it('removes a document once the deletion is confirmed', () => {
      component.openDelete(byName('guia-mass-effect.pdf'));

      component.confirmDelete();

      expect(component.documents().some(doc => doc.filename === 'guia-mass-effect.pdf')).toBe(false);
    });

    it('uploads through the real ingest endpoint and lists the file as pending', () => {
      component.uploadFiles([new File(['texto'], 'nuevo-lore.pdf')]);

      const request = httpMock.expectOne(ingestUrl);
      expect(request.request.method).toBe('POST');
      expect(request.request.body instanceof FormData).toBe(true);
      request.flush({ documentId: 'c0ffee00-0000-4000-8000-000000000001', source: 'nuevo-lore.pdf', game: 'yes' });

      expect(component.uploads()[0].state).toBe('done');
      expect(byName('nuevo-lore.pdf').status).toBe('PENDING');
    });

    it('refuses unsupported formats without calling the backend', () => {
      component.uploadFiles([new File(['x'], 'portada.png')]);

      httpMock.expectNone(ingestUrl);
      expect(component.uploads()[0].state).toBe('error');
    });
  });

  describe('as UPLOADER', () => {
    beforeEach(() => setup({ username: 'maria.lopez', roles: ['UPLOADER'] }));

    it('only shows their own submissions, without review actions', () => {
      expect(component.documents().length).toBe(4);
      expect(component.documents().every(doc => doc.uploadedBy === 'maria.lopez')).toBe(true);
      expect(text()).toContain('Mis envíos');
      expect(text()).not.toContain('Aprobar');
    });
  });
});
