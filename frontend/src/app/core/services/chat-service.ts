import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ChatRequest } from '../models/chat.model';
import { environment } from '../../../environments/environment';
import { readCookie } from '../utils/cookie';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly apiUrl = `${environment.BACKEND_URL}/api/v1/chat`;

  ask(question: string): Observable<string> {
    return new Observable(subscriber => {
      const request: ChatRequest = { question };

      // Angular's XSRF interceptor does not apply to fetch, so the token goes in by hand.
      const csrfToken = readCookie('XSRF-TOKEN');
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (csrfToken) {
        headers['X-XSRF-TOKEN'] = csrfToken;
      }

      fetch(this.apiUrl, {
        method: 'POST',
        headers,
        credentials: 'same-origin',
        body: JSON.stringify(request)
      }).then(async response => {
        if (!response.ok) {
          subscriber.error(new Error(`HTTP ${response.status}`));
          return;
        }

        const reader = response.body!.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) {
              subscriber.complete();
              break;
            }

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');

            // Process complete lines (keep last incomplete line in buffer)
            for (let i = 0; i < lines.length - 1; i++) {
              const line = lines[i].trim();
              if (line.startsWith('data:')) {
                const content = line.substring(5).trim();
                if (content) {
                  subscriber.next(content);
                }
              }
            }

            // Keep last incomplete line for next iteration
            buffer = lines[lines.length - 1];
          }
        } catch (err) {
          subscriber.error(err);
        }
      }).catch(err => subscriber.error(err));
    });
  }
}
