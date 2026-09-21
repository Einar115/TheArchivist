export interface ChatRequest {
    question: string;
}

// Not sent by the backend yet: answers stream as plain text. The view renders these chips once it does.
export interface ChatSource {
    documentId: string;
    filename: string;
}

export type ChatMessageStatus = 'streaming' | 'done' | 'stopped' | 'error';

export interface ChatMessage {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    status: ChatMessageStatus;
    sources: ChatSource[];
}

export interface Conversation {
    id: string;
    title: string;
    updatedAt: number;
    messages: ChatMessage[];
}
