import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DocumentService } from '../../core/services/document-service';
import { IngestResponse } from '../../core/models/documents.model';

interface DocumentInfo {
  id: string;
  name: string;
  uploadedAt: string;
  game?: string;
}

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './documents.html',
  styleUrl: './documents.css',
})
export class Documents {
  documents = signal<DocumentInfo[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  dragover = signal(false);

  constructor(private documentService: DocumentService) {}

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragover.set(true);
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragover.set(false);
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragover.set(false);

    const files = event.dataTransfer?.files;
    if (files) {
      this.uploadFiles(files);
    }
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.uploadFiles(input.files);
    }
  }

  private uploadFiles(files: FileList) {
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      this.uploadFile(file);
    }
  }

  private uploadFile(file: File) {
    this.loading.set(true);
    this.error.set(null);

    this.documentService.upload(file).subscribe({
      next: (response: IngestResponse) => {
        const newDoc: DocumentInfo = {
          id: response.documentId,
          name: response.source,
          uploadedAt: new Date().toISOString(),
          game: response.game
        };
        this.documents.update(docs => [newDoc, ...docs]);
        this.loading.set(false);
      },
      error: (err: any) => {
        this.error.set(`Error al subir ${file.name}: ${err.error?.message || 'Error desconocido'}`);
        this.loading.set(false);
      }
    });
  }

  deleteDocument(documentId: string) {
    if (!confirm('¿Estás seguro de que deseas eliminar este documento?')) {
      return;
    }

    this.documentService.delete(documentId).subscribe({
      next: () => {
        this.documents.update(docs => docs.filter(d => d.id !== documentId));
      },
      error: (err: any) => {
        this.error.set(`Error al eliminar: ${err.error?.message || 'Error desconocido'}`);
      }
    });
  }
}
