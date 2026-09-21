import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DOCUMENTS_MOCK_LATENCY, DocumentService } from './document-service';
import { AuthService } from './auth-service';
import { AuthResponse } from '../models/auth.model';
import { DocumentSummary } from '../models/documents.model';

describe('DocumentService', () => {
  const user = signal<AuthResponse | null>(null);
  let service: DocumentService;

  // With the latency at 0 the mocked operations emit synchronously.
  const listNow = () => {
    let result: DocumentSummary[] = [];
    service.list().subscribe(docs => (result = docs));
    return result;
  };

  beforeEach(() => {
    user.set({ username: 'admin', roles: ['ADMIN_DOCUMENTS'] });

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DOCUMENTS_MOCK_LATENCY, useValue: 0 },
        { provide: AuthService, useValue: { user } },
      ],
    });
    service = TestBed.inject(DocumentService);
  });

  it('gives an admin every document, pending ones first', () => {
    const docs = listNow();

    expect(docs.length).toBe(8);
    expect(docs.slice(0, 3).every(doc => doc.status === 'PENDING')).toBe(true);
  });

  it('scopes the list to the uploader when the user is not an admin', () => {
    user.set({ username: 'jorge.ruiz', roles: ['UPLOADER'] });

    expect(listNow().map(doc => doc.uploadedBy)).toEqual(['jorge.ruiz', 'jorge.ruiz']);
  });

  it('fails when acting on an unknown document', () => {
    let failed = false;

    service.approve('missing').subscribe({ error: () => (failed = true) });

    expect(failed).toBe(true);
  });
});
