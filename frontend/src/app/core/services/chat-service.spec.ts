import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ChatService } from './chat-service';
import { ChatFilters, ChatStreamEvent } from '../models/chat.model';

describe('ChatService', () => {
  const originalFetch = globalThis.fetch;
  let service: ChatService;
  let httpMock: HttpTestingController;
  // The bodies the fake fetch received, already parsed, to check what the backend would get.
  let sentBodies: unknown[];

  // Serves the body in the given chunks, the way a real stream can split events anywhere.
  function respondWith(...chunks: string[]) {
    globalThis.fetch = async (_url, init) => {
      sentBodies.push(JSON.parse(init!.body as string));
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

  function collect(filters?: ChatFilters): Promise<ChatStreamEvent[]> {
    return new Promise((resolve, reject) => {
      const events: ChatStreamEvent[] = [];
      service
        .ask('¿Qué le pasó a Hyrule?', filters)
        .subscribe({ next: event => events.push(event), error: reject, complete: () => resolve(events) });
    });
  }

  beforeEach(() => {
    sentBodies = [];
    TestBed.configureTestingModule({
      // ask() uses fetch, but universes() goes through HttpClient, which the testing backend replaces.
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    httpMock.verify();
  });

  it('emits the sources event parsed from JSON, then the tokens', async () => {
    respondWith(
      'event: sources\ndata: [{"number":1,"documentId":"doc-a","title":"Crónicas de Hyrule",',
      '"universe":"The Legend of Zelda","work":null,"canonicity":"CANON"}]\n\n',
      'data: Hyrule\n\ndata:  cayó\n\n'
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

  it('sends the chosen filters next to the question', async () => {
    respondWith('data: ok\n\n');

    await collect({ universe: 'The Legend of Zelda', canonOnly: true });

    expect(sentBodies).toEqual([{ question: '¿Qué le pasó a Hyrule?', universe: 'The Legend of Zelda', canonOnly: true }]);
  });

  it('searches the whole archive when no filters are given', async () => {
    respondWith('data: ok\n\n');

    await collect();

    expect(sentBodies).toEqual([{ question: '¿Qué le pasó a Hyrule?', universe: null, canonOnly: false }]);
  });

  it('loads the universes the filter can choose from', () => {
    let universes: string[] = [];
    service.universes().subscribe(result => (universes = result));

    httpMock.expectOne({ method: 'GET', url: '/api/v1/chat/universes' }).flush(['Sabotage Studio Universe', 'The Legend of Zelda']);

    expect(universes).toEqual(['Sabotage Studio Universe', 'The Legend of Zelda']);
  });
});
