import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ChatRequest } from '../models/chat.model';
import { environment } from '../../../environments/environment';
import { readCookie } from '../utils/cookie';
import { SseParser } from '../utils/sse-parser';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly apiUrl = `${environment.BACKEND_URL}/api/v1/chat`;

  /** Streams the answer token by token. Unsubscribing aborts the request, which is how the UI stops an answer. */
  ask(question: string): Observable<string> {
    return new Observable<string>(subscriber => {
      const controller = new AbortController();
      const request: ChatRequest = { question };

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

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          parser.push(decoder.decode(value, { stream: true })).forEach(token => subscriber.next(token));
        }
        [...parser.push(decoder.decode()), ...parser.end()].forEach(token => subscriber.next(token));
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
