import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ChatFilters, ChatRequest, ChatSource, ChatStreamEvent, NO_FILTERS } from '../models/chat.model';
import { environment } from '../../../environments/environment';
import { readCookie } from '../utils/cookie';
import { SseEvent, SseParser } from '../utils/sse-parser';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly apiUrl = `${environment.BACKEND_URL}/api/v1/chat`;
  private readonly http = inject(HttpClient);

  universes(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/universes`);
  }

  /**
   * Streams the sources the answer is built from, then the answer token by token. Unsubscribing aborts the request,
   * which is how the UI stops an answer.
   */
  ask(question: string, filters: ChatFilters = NO_FILTERS): Observable<ChatStreamEvent> {
    return new Observable<ChatStreamEvent>(subscriber => {
      const controller = new AbortController();
      const request: ChatRequest = { question, ...filters };

      // Angular's XSRF interceptor does not apply to fetch, so the token goes in by hand.
      const csrfToken = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (csrfToken) {
        headers['X-XSRF-TOKEN'] = csrfToken;
      }

      const stream = async () => {
        const response = await fetch(this.apiUrl, {
          method: 'POST',
          headers,
          credentials: 'same-origin',
          body: JSON.stringify(request),
          signal: controller.signal,
        });
        if (!response.ok || !response.body) {
          throw new Error(`HTTP ${response.status}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        const parser = new SseParser();

        const emit = (event: SseEvent) => subscriber.next(toStreamEvent(event));

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          parser.push(decoder.decode(value, { stream: true })).forEach(emit);
        }
        [...parser.push(decoder.decode()), ...parser.end()].forEach(emit);
        subscriber.complete();
      };

      stream().catch(error => {
        // An abort means the subscriber left (the stop button): there is nobody to report it to.
        if (!controller.signal.aborted) subscriber.error(error);
      });

      return () => controller.abort();
    });
  }
}

function toStreamEvent(event: SseEvent): ChatStreamEvent {
  return event.event === 'sources'
    ? { type: 'sources', sources: JSON.parse(event.data) as ChatSource[] }
    : { type: 'token', text: event.data };
}
