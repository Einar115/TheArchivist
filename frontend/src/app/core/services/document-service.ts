import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IngestResponse } from '../models/documents.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly apiUrl = `${environment.BACKEND_URL}/api/v1/documents`;

  constructor(private http: HttpClient) {}

  upload(file: File, game?: string): Observable<IngestResponse> {
    const formData = new FormData();
    formData.append('file', file);
    if (game) {
      formData.append('game', game);
    }

    return this.http.post<IngestResponse>(`${this.apiUrl}/ingest`, formData);
  }

  delete(documentId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${documentId}`);
  }
}
