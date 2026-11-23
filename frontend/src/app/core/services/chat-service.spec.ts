import { TestBed } from '@angular/core/testing';

import { ChatService } from './chat-service';
import { ChatStreamEvent } from '../models/chat.model';

describe('ChatService', () => {
  const originalFetch = globalThis.fetch;
  let service: ChatService;

  // Serves the body in the given chunks, the way a real stream can split events anywhere.
  function respondWith(...chunks: string[]) {
    globalThis.fetch = async () => {
      const encoder = new TextEncoder();
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          chunks.forEach(chunk => controller.enqueue(encoder.encode(chunk)));
          controller.close();
        },
      });
      return new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } });
    };
  }

  function collect(): Promise<ChatStreamEvent[]> {
    return new Promise((resolve, reject) => {
      const events: ChatStreamEvent[] = [];
      service.ask('¿Qué le pasó a Hyrule?').subscribe({ next: event => events.push(event), error: reject, complete: () => resolve(events) });
    });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChatService);
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('emits the sources event parsed from JSON, then the tokens', async () => {
    respondWith(
      'event:sources\ndata:[{"number":1,"documentId":"doc-a","title":"Crónicas de Hyrule",',
      '"universe":"The Legend of Zelda","work":null,"canonicity":"CANON"}]\n\n',
      'data:Hyrule\n\ndata: cayó\n\n'
    );

    expect(await collect()).toEqual([
      {
        type: 'sources',
        sources: [{ number: 1, documentId: 'doc-a', title: 'Crónicas de Hyrule', universe: 'The Legend of Zelda', work: null, canonicity: 'CANON' }],
      },
      { type: 'token', text: 'Hyrule' },
      { type: 'token', text: ' cayó' },
    ]);
  });

  it('fails when the backend answers with an error status', async () => {
    globalThis.fetch = async () => new Response(null, { status: 400 });

    await expect(collect()).rejects.toThrow('HTTP 400');
  });
});
