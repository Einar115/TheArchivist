import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ConversationService } from './conversation-service';
import { ChatService } from './chat-service';
import { ChatFilters, ChatStreamEvent, NO_FILTERS } from '../models/chat.model';

const token = (text: string): ChatStreamEvent => ({ type: 'token', text });
const ZELDA_CANON: ChatFilters = { universe: 'The Legend of Zelda', canonOnly: true };

describe('ConversationService', () => {
  let answers: Subject<ChatStreamEvent>;
  let asked: string[];
  let askedFilters: ChatFilters[];
  let service: ConversationService;

  const last = (id: string) => service.find(id)!.messages.at(-1)!;

  beforeEach(() => {
    asked = [];
    askedFilters = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: ChatService,
          useValue: {
            ask: (question: string, filters: ChatFilters) => {
              asked.push(question);
              askedFilters.push(filters);
              answers = new Subject<ChatStreamEvent>();
              return answers;
            },
          },
        },
      ],
    });
    service = TestBed.inject(ConversationService);
  });

  it('titles a new conversation after its question and streams the answer into it', () => {
    const id = service.start('¿Quién custodiaba la Trifuerza antes de que el castillo de Hyrule quedara sepultado?');

    expect(service.find(id)!.title.endsWith('…')).toBe(true);
    expect(service.find(id)!.title.length).toBeLessThanOrEqual(48);

    answers.next(token('La familia'));
    answers.next(token(' real'));
    expect(last(id).content).toBe('La familia real');
    expect(last(id).status).toBe('streaming');

    answers.complete();
    expect(last(id).status).toBe('done');
  });

  it('stops an answer by unsubscribing from its stream', () => {
    const id = service.start('Pregunta');

    service.stop(id);

    expect(answers.observed).toBe(false);
    expect(last(id).status).toBe('stopped');
  });

  it('marks a failed answer and regenerates it from the same question', () => {
    const id = service.start('Pregunta');
    answers.error(new Error('HTTP 500'));
    expect(last(id).status).toBe('error');

    service.regenerate(id);

    expect(asked).toEqual(['Pregunta', 'Pregunta']);
    expect(service.find(id)!.messages.length).toBe(2);
    expect(last(id).status).toBe('streaming');
  });

  it('attaches the sources to the answer they arrive with', () => {
    const id = service.start('Pregunta');
    const source = { number: 1, documentId: 'doc-a', title: 'Crónicas de Hyrule', universe: 'The Legend of Zelda', work: null, canonicity: 'CANON' as const };

    answers.next({ type: 'sources', sources: [source] });
    answers.next(token('Hyrule cayó [1].'));

    expect(last(id).sources).toEqual([source]);
    expect(last(id).content).toBe('Hyrule cayó [1].');
  });

  it('clears the old sources when an answer is regenerated', () => {
    const id = service.start('Pregunta');
    answers.next({ type: 'sources', sources: [{ number: 1, documentId: 'doc-a', title: 'A', universe: null, work: null, canonicity: null }] });
    answers.complete();

    service.regenerate(id);

    expect(last(id).sources).toEqual([]);
  });

  it('keeps the filters a conversation started with and asks with them', () => {
    const id = service.start('¿Quién es Link?', ZELDA_CANON);

    expect(service.find(id)!.filters).toEqual(ZELDA_CANON);
    expect(askedFilters).toEqual([ZELDA_CANON]);
  });

  it('searches the whole archive when a conversation starts without filters', () => {
    const id = service.start('¿Quién es Link?');

    expect(service.find(id)!.filters).toEqual(NO_FILTERS);
    expect(askedFilters).toEqual([NO_FILTERS]);
  });

  it('asks follow-up questions with the filters of their conversation', () => {
    const id = service.start('¿Quién es Link?', ZELDA_CANON);
    answers.complete();

    service.send(id, '¿Y Zelda?');

    expect(askedFilters).toEqual([ZELDA_CANON, ZELDA_CANON]);
  });

  it('uses changed filters for the next question and for regenerating', () => {
    const id = service.start('¿Quién es Link?');
    answers.complete();

    service.setFilters(id, ZELDA_CANON);
    service.send(id, '¿Y Zelda?');
    answers.complete();
    service.regenerate(id);

    expect(askedFilters).toEqual([NO_FILTERS, ZELDA_CANON, ZELDA_CANON]);
  });

  it('does not move a conversation up the list when only its filters change', () => {
    // Fake clock: every conversation gets a different updatedAt, so a change to it would show.
    vi.useFakeTimers();
    try {
      vi.setSystemTime(1_000);
      const older = service.start('Primera');
      answers.complete();
      vi.setSystemTime(2_000);
      const newer = service.start('Segunda');
      answers.complete();

      vi.setSystemTime(3_000);
      service.setFilters(older, ZELDA_CANON);

      expect(service.find(older)!.updatedAt).toBe(1_000);
      expect(service.conversations().map(conversation => conversation.id)).toEqual([newer, older]);
    } finally {
      vi.useRealTimers();
    }
  });

  it('forgets every conversation on clear', () => {
    service.start('Pregunta');

    service.clear();

    expect(service.conversations()).toEqual([]);
    expect(answers.observed).toBe(false);
  });
});
