import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth-service';
import { DocumentService } from '../../core/services/document-service';
import { DocumentStatus, DocumentSummary } from '../../core/models/documents.model';

type StatusFilter = 'ALL' | DocumentStatus;

interface UploadItem {
  key: number;
  name: string;
  loaded: number;
  total: number;
  state: 'uploading' | 'done' | 'error';
  error?: string;
  subscription?: Subscription;
}

interface Notice {
  kind: 'success' | 'danger';
  text: string;
}

interface Dialog {
  kind: 'preview' | 'reject' | 'delete';
  doc: DocumentSummary;
}

const PAGE_SIZE = 10;

// The formats TikaDocumentReader handles in the backend's ingest flow.
const ACCEPTED_EXTENSIONS = ['pdf', 'doc', 'docx', 'txt', 'md'];

@Component({
  imports: [NgTemplateOutlet],
  selector: 'app-documents',
  styleUrl: './documents.css',
  templateUrl: './documents.html',
  host: { '(document:keydown.escape)': 'closeDialog()' },
})
export class Documents {
  private readonly auth = inject(AuthService);
  private readonly documentService = inject(DocumentService);

  readonly accept = ACCEPTED_EXTENSIONS.map(extension => `.${extension}`).join(',');
  readonly statusLabel: Record<DocumentStatus, string> = {
    PENDING: 'Pendiente',
    APPROVED: 'Aprobado',
    REJECTED: 'Rechazado',
  };
  readonly tabs: { value: StatusFilter; label: string }[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'PENDING', label: 'Pendientes' },
    { value: 'APPROVED', label: 'Aprobados' },
    { value: 'REJECTED', label: 'Rechazados' },
  ];

  readonly isAdmin = computed(() => this.auth.user()?.roles.includes('ADMIN_DOCUMENTS') ?? false);

  readonly documents = signal<DocumentSummary[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly filter = signal<StatusFilter>('ALL');
  readonly search = signal('');
  readonly page = signal(1);
  readonly uploads = signal<UploadItem[]>([]);
  readonly dragging = signal(false);
  readonly notice = signal<Notice | null>(null);
  readonly busyId = signal<string | null>(null);
  readonly dialog = signal<Dialog | null>(null);
  readonly previewText = signal<string | null>(null);
  readonly rejectReason = signal('');

  readonly counts = computed<Record<StatusFilter, number>>(() => {
    const docs = this.documents();
    const countOf = (status: DocumentStatus) => docs.filter(doc => doc.status === status).length;
    return { ALL: docs.length, PENDING: countOf('PENDING'), APPROVED: countOf('APPROVED'), REJECTED: countOf('REJECTED') };
  });

  readonly filtered = computed(() => {
    const status = this.filter();
    const term = this.search().trim().toLowerCase();
    return this.documents().filter(
      doc =>
        (status === 'ALL' || doc.status === status) &&
        (!term || doc.filename.toLowerCase().includes(term) || doc.uploadedBy.toLowerCase().includes(term))
    );
  });

  readonly pageCount = computed(() => Math.max(1, Math.ceil(this.filtered().length / PAGE_SIZE)));
  readonly currentPage = computed(() => Math.min(this.page(), this.pageCount()));
  readonly pages = computed(() => Array.from({ length: this.pageCount() }, (_, index) => index + 1));
  readonly visible = computed(() => {
    const start = (this.currentPage() - 1) * PAGE_SIZE;
    return this.filtered().slice(start, start + PAGE_SIZE);
  });
  readonly rangeText = computed(() => `Mostrando ${this.visible().length} de ${this.filtered().length} documentos`);

  readonly emptyText = computed(() => {
    if (this.search().trim()) return 'Ningún documento coincide con la búsqueda.';
    if (!this.isAdmin()) return 'Aún no has subido documentos.';
    return this.filter() === 'PENDING' ? 'No hay documentos pendientes de revisión.' : 'No hay documentos en esta vista.';
  });

  private readonly dateFormat = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
  private readonly timeFormat = new Intl.DateTimeFormat('es-ES', { hour: '2-digit', minute: '2-digit' });
  private readonly numberFormat = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 1 });
  private nextUploadKey = 0;

  constructor() {
    this.load();
    // Leaving the page aborts uploads still in flight rather than letting them finish where nobody sees them.
    inject(DestroyRef).onDestroy(() => this.uploads().forEach(item => item.subscription?.unsubscribe()));
  }

  load(silent = false): void {
    if (!silent) this.loading.set(true);
    this.loadError.set(false);

    this.documentService.list().subscribe({
      next: docs => {
        this.documents.set(docs);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  setFilter(value: StatusFilter): void {
    this.filter.set(value);
    this.page.set(1);
  }

  onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
    this.page.set(1);
  }

  goTo(page: number): void {
    if (page >= 1 && page <= this.pageCount()) this.page.set(page);
  }

  // Uploads

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) this.uploadFiles(input.files);
    // Clearing the input lets the same file be picked again after a failure.
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  onDragLeave(event: DragEvent): void {
    // dragleave also fires when moving onto a child of the drop zone; only react when really leaving it.
    if (!(event.currentTarget as Node).contains(event.relatedTarget as Node | null)) {
      this.dragging.set(false);
    }
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    if (event.dataTransfer?.files) this.uploadFiles(event.dataTransfer.files);
  }

  uploadFiles(files: FileList | File[]): void {
    for (const file of Array.from(files)) {
      const item: UploadItem = { key: this.nextUploadKey++, name: file.name, loaded: 0, total: file.size, state: 'uploading' };
      const extension = file.name.split('.').pop()?.toLowerCase() ?? '';

      if (!ACCEPTED_EXTENSIONS.includes(extension)) {
        this.uploads.update(items => [{ ...item, state: 'error', error: 'Formato no admitido' }, ...items]);
        continue;
      }

      this.uploads.update(items => [item, ...items]);

      const subscription = this.documentService.upload(file).subscribe({
        next: event => {
          if (event.type === HttpEventType.UploadProgress) {
            this.patchUpload(item.key, { loaded: event.loaded, total: event.total ?? file.size });
          } else if (event.type === HttpEventType.Response) {
            this.patchUpload(item.key, { state: 'done' });
            this.load(true);
          }
        },
        error: (error: HttpErrorResponse) => this.patchUpload(item.key, { state: 'error', error: this.uploadErrorFor(error) }),
      });
      this.patchUpload(item.key, { subscription });
    }
  }

  removeUpload(item: UploadItem): void {
    item.subscription?.unsubscribe();
    this.uploads.update(items => items.filter(other => other.key !== item.key));
  }

  progress(item: UploadItem): number {
    if (item.state === 'done') return 100;
    return item.total ? Math.round((item.loaded / item.total) * 100) : 0;
  }

  uploadStatus(item: UploadItem): string {
    if (item.state === 'done') return 'Enviado para revisión';
    if (item.state === 'error') return item.error ?? 'No se pudo subir';
    return `${this.formatSize(item.loaded)} de ${this.formatSize(item.total)} · ${this.progress(item)} %`;
  }

  // Review actions

  openPreview(doc: DocumentSummary): void {
    this.dialog.set({ kind: 'preview', doc });
    this.previewText.set(null);
    this.documentService.preview(doc.id).subscribe({
      // Ignore answers for a dialog the user has already closed or switched away from.
      next: text => this.dialog()?.doc.id === doc.id && this.previewText.set(text),
      error: () => this.dialog()?.doc.id === doc.id && this.previewText.set('No se pudo cargar la vista previa.'),
    });
  }

  openReject(doc: DocumentSummary): void {
    this.rejectReason.set('');
    this.dialog.set({ kind: 'reject', doc });
  }

  openDelete(doc: DocumentSummary): void {
    this.dialog.set({ kind: 'delete', doc });
  }

  closeDialog(): void {
    if (!this.busyId()) this.dialog.set(null);
  }

  onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.closeDialog();
  }

  onReasonInput(event: Event): void {
    this.rejectReason.set((event.target as HTMLTextAreaElement).value);
  }

  approve(doc: DocumentSummary): void {
    this.busyId.set(doc.id);
    this.documentService.approve(doc.id).subscribe({
      next: updated => this.succeed(updated, `«${doc.filename}» aprobado. El chat ya puede usarlo.`),
      error: () => this.fail(`No se pudo aprobar «${doc.filename}».`),
    });
  }

  confirmReject(): void {
    const doc = this.dialog()?.doc;
    if (!doc) return;

    this.busyId.set(doc.id);
    this.documentService.reject(doc.id, this.rejectReason().trim() || undefined).subscribe({
      next: updated => this.succeed(updated, `«${doc.filename}» rechazado. No se añadirá al archivo.`),
      error: () => this.fail(`No se pudo rechazar «${doc.filename}».`),
    });
  }

  confirmDelete(): void {
    const doc = this.dialog()?.doc;
    if (!doc) return;

    this.busyId.set(doc.id);
    this.documentService.delete(doc.id).subscribe({
      next: () => {
        this.documents.update(docs => docs.filter(other => other.id !== doc.id));
        this.finish({ kind: 'success', text: `«${doc.filename}» eliminado del archivo.` });
      },
      error: () => this.fail(`No se pudo eliminar «${doc.filename}».`),
    });
  }

  // Formatting

  formatDate(iso: string): string {
    return this.dateFormat.format(new Date(iso));
  }

  formatTime(iso: string): string {
    return this.timeFormat.format(new Date(iso));
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${this.numberFormat.format(bytes / 1024)} KB`;
    return `${this.numberFormat.format(bytes / (1024 * 1024))} MB`;
  }

  meta(doc: DocumentSummary): string {
    const extension = doc.filename.split('.').pop()?.toUpperCase() ?? '';
    const chunks = doc.chunkCount ? ` · ${doc.chunkCount} fragmentos` : '';
    return `${extension} · ${this.formatSize(doc.fileSize)}${chunks}`;
  }

  fileIcon(filename: string): string {
    const extension = filename.split('.').pop()?.toLowerCase();
    if (extension === 'pdf') return 'bi-file-earmark-pdf';
    if (extension === 'doc' || extension === 'docx') return 'bi-file-earmark-word';
    if (extension === 'md') return 'bi-filetype-md';
    return 'bi-file-earmark-text';
  }

  initials(username: string): string {
    return username.slice(0, 2).toUpperCase();
  }

  statusDetail(doc: DocumentSummary): string {
    if (doc.status === 'PENDING') return 'En espera de revisión';
    if (doc.status === 'APPROVED') return `Disponible en el chat · ${doc.chunkCount ?? 0} fragmentos`;
    return doc.rejectionReason ? `Rechazado: ${doc.rejectionReason}` : 'Un administrador lo rechazó';
  }

  detailIcon(doc: DocumentSummary): string {
    return { PENDING: 'bi-hourglass-split', APPROVED: 'bi-chat-dots', REJECTED: 'bi-slash-circle' }[doc.status];
  }

  private patchUpload(key: number, patch: Partial<UploadItem>): void {
    this.uploads.update(items => items.map(item => (item.key === key ? { ...item, ...patch } : item)));
  }

  private uploadErrorFor(error: HttpErrorResponse): string {
    switch (error.status) {
      case 0:
        return 'Sin conexión con el servidor';
      case 401:
        return 'Tu sesión ha caducado';
      case 403:
        return 'Tu cuenta no puede subir documentos';
      case 413:
        return 'El archivo supera el tamaño máximo';
      default:
        return 'No se pudo subir el archivo';
    }
  }

  private succeed(updated: DocumentSummary, text: string): void {
    this.documents.update(docs => docs.map(doc => (doc.id === updated.id ? updated : doc)));
    this.finish({ kind: 'success', text });
  }

  private fail(text: string): void {
    this.finish({ kind: 'danger', text });
  }

  private finish(notice: Notice): void {
    this.busyId.set(null);
    this.dialog.set(null);
    this.notice.set(notice);
  }
}
