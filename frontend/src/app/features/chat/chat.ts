import { Component, signal, effect, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../core/services/chat-service';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.html',
  styleUrl: './chat.css',
})
export class Chat implements AfterViewChecked {
  @ViewChild('messageContainer') private messageContainer!: ElementRef;

  question = signal('');
  answer = signal('');
  loading = signal(false);
  messages = signal<Message[]>([]);

  constructor(private chatService: ChatService) {
    effect(() => {
      if (this.messages().length > 0) {
        setTimeout(() => this.scrollToBottom(), 0);
      }
    });
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  private scrollToBottom() {
    const container = this.messageContainer?.nativeElement;
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  }

  async sendMessage() {
    const q = this.question().trim();
    if (!q) return;

    this.question.set('');
    this.loading.set(true);
    this.answer.set('');

    this.messages.update(msgs => [...msgs, {
      role: 'user',
      content: q,
      timestamp: new Date()
    }]);

    try {
      let fullAnswer = '';

      this.chatService.ask(q).subscribe({
        next: (token: string) => {
          fullAnswer += token;
          this.answer.set(fullAnswer);
        },
        error: (err: any) => {
          this.messages.update(msgs => [...msgs, {
            role: 'assistant',
            content: `Error: ${err.message}`,
            timestamp: new Date()
          }]);
          this.loading.set(false);
        },
        complete: () => {
          if (fullAnswer) {
            this.messages.update(msgs => [...msgs, {
              role: 'assistant',
              content: fullAnswer,
              timestamp: new Date()
            }]);
          }
          this.answer.set('');
          this.loading.set(false);
        }
      });
    } catch (err: any) {
      this.messages.update(msgs => [...msgs, {
        role: 'assistant',
        content: `Error: ${err.message}`,
        timestamp: new Date()
      }]);
      this.loading.set(false);
    }
  }

  onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }
}
