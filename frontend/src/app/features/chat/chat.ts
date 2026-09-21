import { Component, ElementRef, afterNextRender, afterRenderEffect, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';
import { ConversationService } from '../../core/services/conversation-service';
import { ChatMessage } from '../../core/models/chat.model';
import { renderMarkdown } from '../../core/utils/markdown';

// How close to the bottom still counts as "following" the answer while it streams.
const STICKY_SCROLL_THRESHOLD = 80;

@Component({
  selector: 'app-chat',
  styleUrl: './chat.css',
  templateUrl: './chat.html',
})
export class Chat {
  private readonly conversations = inject(ConversationService);
  private readonly router = inject(Router);

  private readonly conversationId = toSignal(
    inject(ActivatedRoute).paramMap.pipe(map(params => params.get('id'))),
    { initialValue: null }
  );

  readonly conversation = computed(() => this.conversations.find(this.conversationId()));
  // An id that is not in memory: conversations do not survive a reload yet.
  readonly missing = computed(() => this.conversationId() !== null && !this.conversation());
  readonly streaming = computed(() => this.conversation()?.messages.at(-1)?.status === 'streaming');

  readonly draft = signal('');
  readonly copiedId = signal<string | null>(null);

  private readonly thread = viewChild<ElementRef<HTMLElement>>('thread');
  private readonly input = viewChild<ElementRef<HTMLTextAreaElement>>('input');
  private readonly rendered = new WeakMap<ChatMessage, string>();
  private followAnswer = true;

  constructor() {
    // Keeps the newest text in view while it streams, unless the user scrolled up to read something.
    afterRenderEffect(() => {
      this.conversation();
      const element = this.thread()?.nativeElement;
      if (element && this.followAnswer) element.scrollTop = element.scrollHeight;
    });

    // On touch screens focusing would pop the keyboard over the page, so only do it on desktop.
    afterNextRender(() => {
      if (globalThis.matchMedia?.('(min-width: 992px)').matches) this.input()?.nativeElement.focus();
    });
  }

  send(text = this.draft()): void {
    const question = text.trim();
    if (!question || this.streaming()) return;

    this.draft.set('');
    this.resizeInput();
    this.followAnswer = true;

    const current = this.conversation();
    if (current) {
      this.conversations.send(current.id, question);
    } else {
      const id = this.conversations.start(question);
      this.router.navigate(['/chat', id]);
    }
  }

  stop(): void {
    const current = this.conversation();
    if (current) this.conversations.stop(current.id);
  }

  regenerate(): void {
    const current = this.conversation();
    if (!current) return;
    this.followAnswer = true;
    this.conversations.regenerate(current.id);
  }

  async copy(message: ChatMessage): Promise<void> {
    try {
      await navigator.clipboard.writeText(message.content);
      this.copiedId.set(message.id);
      setTimeout(() => this.copiedId() === message.id && this.copiedId.set(null), 2000);
    } catch {
      // The Clipboard API needs a secure context (https or localhost); without it there is nothing to fall back to.
    }
  }

  html(message: ChatMessage): string {
    // Messages are replaced, never mutated, on every token, so the object itself is a safe cache key.
    let html = this.rendered.get(message);
    if (html === undefined) {
      html = renderMarkdown(message.content);
      this.rendered.set(message, html);
    }
    return html;
  }

  onInput(event: Event): void {
    const element = event.target as HTMLTextAreaElement;
    this.draft.set(element.value);
    this.resizeInput(element);
  }

  onKeydown(event: KeyboardEvent): void {
    // Enter sends, Shift+Enter breaks the line; isComposing leaves IME input (accents, CJK) alone.
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.send();
    }
  }

  onThreadScroll(): void {
    const element = this.thread()?.nativeElement;
    if (element) {
      this.followAnswer = element.scrollHeight - element.scrollTop - element.clientHeight < STICKY_SCROLL_THRESHOLD;
    }
  }

  private resizeInput(element = this.input()?.nativeElement): void {
    if (!element) return;
    element.style.height = 'auto';
    element.style.height = `${element.scrollHeight}px`;
  }
}
