import { Injectable, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { ChatService } from './chat-service';
import { ChatMessage, ChatMessageStatus, Conversation } from '../models/chat.model';

const TITLE_LENGTH = 48;

/**
 * Conversations of the current session. The backend keeps no chat history yet, so they live in memory: they survive
 * moving around the app but not a reload, and the shell drops them on logout.
 * TODO(backend branch): load and persist them through the history endpoint once it exists.
 *
 * Streams belong to this service rather than to the chat screen, so an answer keeps arriving while the user looks at
 * another conversation or page.
 */
@Injectable({ providedIn: 'root' })
export class ConversationService {
  private readonly chat = inject(ChatService);
  private readonly streams = new Map<string, Subscription>();
  private readonly store = signal<Conversation[]>([]);

  readonly conversations = computed(() => [...this.store()].sort((a, b) => b.updatedAt - a.updatedAt));

  find(id: string | null): Conversation | undefined {
    return id ? this.store().find(conversation => conversation.id === id) : undefined;
  }

  /** Opens a conversation titled after its first question and returns its id. */
  start(question: string): string {
    const id = newId();
    const title = question.length > TITLE_LENGTH ? `${question.slice(0, TITLE_LENGTH - 1).trimEnd()}…` : question;
    this.store.update(conversations => [{ id, title, updatedAt: Date.now(), messages: [] }, ...conversations]);
    this.send(id, question);
    return id;
  }

  send(id: string, question: string): void {
    if (this.streams.has(id)) return;
    this.update(id, conversation => ({
      ...conversation,
      messages: [...conversation.messages, message('user', question, 'done'), message('assistant', '', 'streaming')],
    }));
    this.stream(id, question);
  }

  stop(id: string): void {
    // Unsubscribing aborts the underlying fetch.
    this.streams.get(id)?.unsubscribe();
    this.streams.delete(id);
    this.patchLast(id, last => (last.status === 'streaming' ? { ...last, status: 'stopped' } : last));
  }

  /** Asks the last question again, replacing the answer it got. */
  regenerate(id: string): void {
    const conversation = this.find(id);
    if (!conversation || this.streams.has(id)) return;

    const question = [...conversation.messages].reverse().find(item => item.role === 'user')?.content;
    if (!question) return;

    this.patchLast(id, last => (last.role === 'assistant' ? { ...last, content: '', status: 'streaming', sources: [] } : last));
    this.stream(id, question);
  }

  clear(): void {
    this.streams.forEach(subscription => subscription.unsubscribe());
    this.streams.clear();
    this.store.set([]);
  }

  private stream(id: string, question: string): void {
    const subscription = this.chat.ask(question).subscribe({
      next: token => this.patchLast(id, last => ({ ...last, content: last.content + token })),
      error: () => this.finish(id, 'error'),
      complete: () => this.finish(id, 'done'),
    });
    // A stream that already finished synchronously has nothing left to stop.
    if (!subscription.closed) this.streams.set(id, subscription);
  }

  private finish(id: string, status: ChatMessageStatus): void {
    this.streams.delete(id);
    this.patchLast(id, last => ({ ...last, status }));
  }

  private patchLast(id: string, change: (last: ChatMessage) => ChatMessage): void {
    this.update(id, conversation => {
      const last = conversation.messages.at(-1);
      return last ? { ...conversation, messages: [...conversation.messages.slice(0, -1), change(last)] } : conversation;
    });
  }

  private update(id: string, change: (conversation: Conversation) => Conversation): void {
    this.store.update(conversations =>
      conversations.map(conversation => (conversation.id === id ? { ...change(conversation), updatedAt: Date.now() } : conversation))
    );
  }
}

function message(role: ChatMessage['role'], content: string, status: ChatMessageStatus): ChatMessage {
  return { id: newId(), role, content, status, sources: [] };
}

function newId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
