import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ConversationService } from './conversation-service';
import { ChatService } from './chat-service';

describe('ConversationService', () => {
  let answers: Subject<string>;
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
              answers = new Subject<string>();
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

    answers.next('La familia');
    answers.next(' real');
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

  it('forgets every conversation on clear', () => {
    service.start('Pregunta');

    service.clear();

    expect(service.conversations()).toEqual([]);
    expect(answers.observed).toBe(false);
  });
});
