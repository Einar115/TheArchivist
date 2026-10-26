import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { DocumentContentResponse, DocumentMetadata, DocumentSummary, SubmitResponse } from '../models/documents.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly apiUrl = `${environment.BACKEND_URL}/api/v1/documents`;
  private readonly http = inject(HttpClient);

  // The backend scopes the list by role: an UPLOADER only gets their own submissions, an admin gets everything.
  list(): Observable<DocumentSummary[]> {
    return this.http.get<DocumentSummary[]>(this.apiUrl).pipe(map(docs => [...docs].sort(pendingFirstThenNewest)));
  }

  preview(id: string): Observable<string> {
    return this.http.get<DocumentContentResponse>(`${this.apiUrl}/${id}/content`).pipe(map(response => response.content));
  }

  approve(id: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/approve`, null);
  }

  reject(id: string, reason?: string): Observable<void> {
    // Always a JSON body: the endpoint answers 400 when it is missing, even if there is no reason.
    return this.http.post<void>(`${this.apiUrl}/${id}/reject`, { reason: reason ?? null });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  submit(file: File, metadata: DocumentMetadata): Observable<HttpEvent<SubmitResponse>> {
    const formData = new FormData();
    formData.append('file', file);
    // Bound by @ModelAttribute on the backend: one form field per DocumentMetadataRequest component.
    // Empty optional fields are left out rather than sent as "".
    for (const [name, value] of Object.entries(metadata)) {
      if (value) formData.append(name, value);
    }

    return this.http.post<SubmitResponse>(`${this.apiUrl}/submit`, formData, { reportProgress: true, observe: 'events' });
  }
}

function pendingFirstThenNewest(a: DocumentSummary, b: DocumentSummary): number {
  if ((a.status === 'PENDING') !== (b.status === 'PENDING')) return a.status === 'PENDING' ? -1 : 1;
  return Date.parse(b.uploadedAt) - Date.parse(a.uploadedAt);
}
