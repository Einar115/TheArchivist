// Mirrors DocumentEntity.DocumentStatusEnum.
export type DocumentStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

// Mirrors DocumentEntity.CanonicityEnum.
export type Canonicity = 'CANON' | 'SEMI_CANON' | 'NON_CANON' | 'SPECULATION';

// Mirrors DocumentEntity.DocumentTypeEnum.
export type DocumentType = 'OFFICIAL_GUIDE' | 'WIKI' | 'TRANSCRIPT' | 'ARTBOOK' | 'ANALYSIS' | 'OTHER';

// Mirrors DocumentMetadataRequest: sent as form fields next to the file on submit.
export interface DocumentMetadata {
    universe: string;
    work?: string;
    // ISO 639-1 code in lowercase, e.g. "es".
    lang: string;
    title?: string;
    canonicity: Canonicity;
    documentType?: DocumentType;
    author?: string;
    originUrl?: string;
}

// Mirrors SubmitResponse.
export interface SubmitResponse {
    id: string;
    filename: string;
    status: DocumentStatus;
    universe: string;
    lang: string;
}

// Mirrors DocumentContentResponse.
export interface DocumentContentResponse {
    id: string;
    content: string;
}

// Mirrors DocumentSummaryResponse, the rows of GET /api/v1/documents.
export interface DocumentSummary {
    id: string;
    filename: string;
    fileSize: number;
    uploadedAt: string;
    // Null once the uploader's account has been deleted (uploaded_by is ON DELETE SET NULL).
    uploadedBy: string | null;
    status: DocumentStatus;
    // Only known once approved: it is the number of chunks indexed into Qdrant.
    chunkCount: number | null;
    rejectionReason: string | null;
    universe: string;
    work: string | null;
    lang: string;
    title: string | null;
    canonicity: Canonicity;
    documentType: DocumentType | null;
}
