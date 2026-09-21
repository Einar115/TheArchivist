// Mirrors DocumentHistoryEntity.DocumentStatusEnum.
export type DocumentStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface IngestResponse {
    documentId: string;
    source: string;
    game: string;
}

// What the documents view expects from the list endpoint the backend branch will add.
export interface DocumentSummary {
    id: string;
    filename: string;
    fileSize: number;
    uploadedAt: string;
    uploadedBy: string;
    status: DocumentStatus;
    // Only known once approved: it is the number of chunks indexed into Qdrant.
    chunkCount: number | null;
    // Not stored by the backend yet; the reject dialog already collects it.
    rejectionReason: string | null;
}
