import { Canonicity } from './documents.model';

export interface ChatRequest {
    question: string;
}

// Mirrors ChatSourceResponse: a document the answer was built from, sent before the tokens.
// `number` is the [N] the answer uses to cite it.
export interface ChatSource {
    number: number;
    documentId: string;
    title: string;
    universe: string | null;
    work: string | null;
    canonicity: Canonicity | null;
}

// What ChatService.ask emits: the sources once, at the start, then the answer token by token.
export type ChatStreamEvent = { type: 'sources'; sources: ChatSource[] } | { type: 'token'; text: string };

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
