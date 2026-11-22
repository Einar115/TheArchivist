import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ConversationService } from './conversation-service';
import { ChatService } from './chat-service';
import { ChatStreamEvent } from '../models/chat.model';

const token = (text: string): ChatStreamEvent => ({ type: 'token', text });

describe('ConversationService', () => {
  let answers: Subject<ChatStreamEvent>;
  let asked: string[];
  let service: ConversationService;

  const last = (id: string) => service.find(id)!.messages.at(-1)!;

  beforeEach(() => {
    asked = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: ChatService,
          useValue: {
            ask: (question: string) => {
              asked.push(question);
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

  it('forgets every conversation on clear', () => {
    service.start('Pregunta');

    service.clear();

    expect(service.conversations()).toEqual([]);
    expect(answers.observed).toBe(false);
  });
});
