import { Injectable, InjectionToken, inject } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { MonoTypeOperatorFunction, Observable, defer, delay, identity, of, tap } from 'rxjs';
import { DocumentSummary, IngestResponse } from '../models/documents.model';
import { AuthService } from './auth-service';
import { environment } from '../../../environments/environment';

/** Simulated round trip for the operations the backend does not expose yet. Tests set it to 0. */
export const DOCUMENTS_MOCK_LATENCY = new InjectionToken<number>('DOCUMENTS_MOCK_LATENCY', {
  providedIn: 'root',
  factory: () => 400,
});

// Rough stand-in for the chunks TokenTextSplitter produces when a document is approved.
const BYTES_PER_CHUNK = 40_000;

const SEED: DocumentSummary[] = [
  seed('1', 'cronicas-de-hyrule.pdf', 2_400_000, '2026-09-18T10:42:00', 'maria.lopez', 'PENDING'),
  seed('2', 'bestiario-elden-ring.docx', 860_000, '2026-09-18T09:15:00', 'jorge.ruiz', 'PENDING'),
  seed('3', 'notas-lore-hollow-knight.md', 42_000, '2026-09-17T18:40:00', 'maria.lopez', 'PENDING'),
  seed('4', 'guia-mass-effect.pdf', 5_100_000, '2026-09-14T12:03:00', 'admin', 'APPROVED', 128),
  seed('5', 'objetos-dark-souls.txt', 310_000, '2026-09-12T16:27:00', 'jorge.ruiz', 'REJECTED'),
  seed('6', 'guia-zelda-breath-of-the-wild.pdf', 3_700_000, '2026-09-11T08:30:00', 'maria.lopez', 'APPROVED', 96),
  seed('7', 'compendio-the-witcher.pdf', 12_800_000, '2026-09-10T11:58:00', 'admin', 'APPROVED', 402),
  seed('8', 'resumen-ocarina-of-time.txt', 18_000, '2026-09-09T19:05:00', 'maria.lopez', 'REJECTED'),
];

const SAMPLE_TEXT = `Capítulo I — La caída del reino

Antes de que el Castillo de Hyrule quedara sepultado bajo la Calamidad, la familia real custodiaba tres fragmentos de la Trifuerza en santuarios distintos. Las crónicas de la época describen a los Sheikah como guardianes de una tecnología olvidada…

Los registros de la Ciudadela mencionan que el primer sello se rompió durante el eclipse de la luna escarlata, cuando los guardianes dejaron de responder a las órdenes de palacio.`;

const UNAVAILABLE_PREVIEW = 'La vista previa de este documento estará disponible cuando el backend exponga el texto extraído.';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly apiUrl = `${environment.BACKEND_URL}/api/v1/documents`;
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly latency = inject(DOCUMENTS_MOCK_LATENCY);

  // TODO(backend branch): list, preview, approve, reject and delete run against this in-memory store until the
  // documents API exposes them. Only upload reaches the real backend today, so the swap stays inside this file.
  private store: DocumentSummary[] = SEED.map(doc => ({ ...doc }));
  private readonly seeded = new Set(SEED.map(doc => doc.id));

  list(): Observable<DocumentSummary[]> {
    return this.mock(() => {
      const user = this.auth.user();
      const isAdmin = user?.roles.includes('ADMIN_DOCUMENTS') ?? false;
      // The real endpoint will scope this server-side: an UPLOADER only gets their own submissions.
      const visible = isAdmin ? this.store : this.store.filter(doc => doc.uploadedBy === user?.username);
      return [...visible].sort(pendingFirstThenNewest);
    });
  }

  preview(id: string): Observable<string> {
    return this.mock(() => (this.seeded.has(this.find(id).id) ? SAMPLE_TEXT : UNAVAILABLE_PREVIEW));
  }

  approve(id: string): Observable<DocumentSummary> {
    return this.mock(() =>
      this.change(id, doc => ({
        ...doc,
        status: 'APPROVED',
        chunkCount: Math.max(1, Math.round(doc.fileSize / BYTES_PER_CHUNK)),
      }))
    );
  }

  reject(id: string, reason?: string): Observable<DocumentSummary> {
    return this.mock(() => this.change(id, doc => ({ ...doc, status: 'REJECTED', rejectionReason: reason ?? null })));
  }

  delete(id: string): Observable<void> {
    return this.mock(() => {
      this.find(id);
      this.store = this.store.filter(doc => doc.id !== id);
    });
  }

  upload(file: File): Observable<HttpEvent<IngestResponse>> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http
      .post<IngestResponse>(`${this.apiUrl}/ingest`, formData, { reportProgress: true, observe: 'events' })
      .pipe(
        tap(event => {
          if (event.type !== HttpEventType.Response || !event.body) return;
          // The backend stored it as PENDING; mirror that so the mocked list shows it the way the real one will.
          this.store = [
            {
              id: event.body.documentId,
              filename: file.name,
              fileSize: file.size,
              uploadedAt: new Date().toISOString(),
              uploadedBy: this.auth.user()?.username ?? '',
              status: 'PENDING',
              chunkCount: null,
              rejectionReason: null,
            },
            ...this.store,
          ];
        })
      );
  }

  private mock<T>(work: () => T): Observable<T> {
    const wait: MonoTypeOperatorFunction<T> = this.latency > 0 ? delay(this.latency) : identity;
    return defer(() => of(work())).pipe(wait);
  }

  private find(id: string): DocumentSummary {
    const doc = this.store.find(item => item.id === id);
    if (!doc) throw new Error(`Document not found: ${id}`);
    return doc;
  }

  private change(id: string, update: (doc: DocumentSummary) => DocumentSummary): DocumentSummary {
    const updated = update(this.find(id));
    this.store = this.store.map(doc => (doc.id === id ? updated : doc));
    return updated;
  }
}

function seed(
  suffix: string,
  filename: string,
  fileSize: number,
  uploadedAt: string,
  uploadedBy: string,
  status: DocumentSummary['status'],
  chunkCount: number | null = null
): DocumentSummary {
  return {
    id: `7c1e4a52-0d3b-4f6e-9a21-00000000000${suffix}`,
    filename,
    fileSize,
    uploadedAt,
    uploadedBy,
    status,
    chunkCount,
    rejectionReason: null,
  };
}

function pendingFirstThenNewest(a: DocumentSummary, b: DocumentSummary): number {
  if ((a.status === 'PENDING') !== (b.status === 'PENDING')) return a.status === 'PENDING' ? -1 : 1;
  return Date.parse(b.uploadedAt) - Date.parse(a.uploadedAt);
}
