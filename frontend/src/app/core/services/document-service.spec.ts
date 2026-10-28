import { TestBed } from '@angular/core/testing';
import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DocumentService } from './document-service';
import { DocumentSummary } from '../models/documents.model';

function summary(overrides: Partial<DocumentSummary> = {}): DocumentSummary {
  return {
    id: '7c1e4a52-0d3b-4f6e-9a21-000000000001',
    filename: 'cronicas-de-hyrule.pdf',
    fileSize: 2_400_000,
    uploadedAt: '2026-09-18T10:42:00Z',
    uploadedBy: 'uploader',
    status: 'PENDING',
    chunkCount: null,
    rejectionReason: null,
    universe: 'The Legend of Zelda',
    work: 'Breath of the Wild',
    lang: 'es',
    title: null,
    canonicity: 'CANON',
    documentType: null,
    ...overrides,
  };
}

describe('DocumentService', () => {
  const baseUrl = '/api/v1/documents';
  const id = '7c1e4a52-0d3b-4f6e-9a21-000000000001';

  let service: DocumentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DocumentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists documents with the pending ones first, newest first within each group', () => {
    let result: DocumentSummary[] = [];
    service.list().subscribe(docs => (result = docs));

    httpMock.expectOne({ method: 'GET', url: baseUrl }).flush([
      summary({ id: 'a', status: 'APPROVED', uploadedAt: '2026-09-20T10:00:00Z' }),
      summary({ id: 'b', status: 'PENDING', uploadedAt: '2026-09-17T10:00:00Z' }),
      summary({ id: 'c', status: 'PENDING', uploadedAt: '2026-09-19T10:00:00Z' }),
    ]);

    expect(result.map(doc => doc.id)).toEqual(['c', 'b', 'a']);
  });

  it('returns the extracted text for the preview', () => {
    let text = '';
    service.preview(id).subscribe(content => (text = content));

    httpMock.expectOne({ method: 'GET', url: `${baseUrl}/${id}/content` }).flush({ id, content: 'Texto extraído' });

    expect(text).toBe('Texto extraído');
  });

  it('approves through the approve endpoint', () => {
    service.approve(id).subscribe();

    httpMock.expectOne({ method: 'POST', url: `${baseUrl}/${id}/approve` }).flush(null);
  });

  it('always sends a JSON body when rejecting, with a null reason when there is none', () => {
    service.reject(id).subscribe();
    service.reject(id, 'Contenido duplicado').subscribe();

    const [withoutReason, withReason] = httpMock.match({ method: 'POST', url: `${baseUrl}/${id}/reject` });
    expect(withoutReason.request.body).toEqual({ reason: null });
    expect(withReason.request.body).toEqual({ reason: 'Contenido duplicado' });
    withoutReason.flush(null);
    withReason.flush(null);
  });

  it('deletes through the document URL', () => {
    service.delete(id).subscribe();

    httpMock.expectOne({ method: 'DELETE', url: `${baseUrl}/${id}` }).flush(null);
  });

  it('submits the file and the filled metadata fields as multipart form data', () => {
    const file = new File(['texto'], 'nuevo-lore.pdf');
    let done = false;

    service
      .submit(file, { universe: 'The Witcher', lang: 'es', canonicity: 'CANON', work: '', title: 'Bestiario', originUrl: '' })
      .subscribe(event => (done = done || event.type === HttpEventType.Response));

    const request = httpMock.expectOne({ method: 'POST', url: `${baseUrl}/submit` });
    const body = request.request.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('universe')).toBe('The Witcher');
    expect(body.get('lang')).toBe('es');
    expect(body.get('canonicity')).toBe('CANON');
    expect(body.get('title')).toBe('Bestiario');
    // Empty optional fields are left out instead of being sent as "".
    expect(body.has('work')).toBe(false);
    expect(body.has('originUrl')).toBe(false);
    expect(request.request.reportProgress).toBe(true);

    request.flush({ id, filename: 'nuevo-lore.pdf', status: 'PENDING', universe: 'The Witcher', lang: 'es' });
    expect(done).toBe(true);
  });
});
