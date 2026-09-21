import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { Chat } from './chat';
import { ChatService } from '../../core/services/chat-service';
import { ConversationService } from '../../core/services/conversation-service';

describe('Chat', () => {
  let answers: Subject<string>;
  let params: BehaviorSubject<ParamMap>;
  let component: Chat;
  let fixture: ComponentFixture<Chat>;

  const root = () => fixture.nativeElement as HTMLElement;

  async function render() {
    fixture = TestBed.createComponent(Chat);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }

  beforeEach(() => {
    params = new BehaviorSubject(convertToParamMap({}));
    TestBed.configureTestingModule({
      imports: [Chat],
      providers: [
        provideRouter([]),
        {
          provide: ChatService,
          useValue: {
            ask: () => {
              answers = new Subject<string>();
              return answers;
            },
          },
        },
        { provide: ActivatedRoute, useValue: { paramMap: params } },
      ],
    });
  });

  it('starts a conversation from the first question and opens it', async () => {
    const router = TestBed.inject(Router);
    let target: unknown[] = [];
    router.navigate = ((commands: unknown[]) => {
      target = commands;
      return Promise.resolve(true);
    }) as Router['navigate'];
    await render();
    expect(root().textContent).toContain('¿Qué quieres saber del archivo?');

    component.send('¿Quién custodiaba la Trifuerza?');

    const [conversation] = TestBed.inject(ConversationService).conversations();
    expect(conversation.title).toBe('¿Quién custodiaba la Trifuerza?');
    expect(target).toEqual(['/chat', conversation.id]);
  });

  it('renders the streamed answer as Markdown and can stop it', async () => {
    const service = TestBed.inject(ConversationService);
    const id = service.start('¿Quién custodiaba la Trifuerza?');
    params.next(convertToParamMap({ id }));
    await render();

    answers.next('Los **Sheikah**');
    answers.next(' custodiaban:\n\n- Sabiduría');
    fixture.detectChanges();
    await fixture.whenStable();

    const answer = root().querySelector('.answer')!.innerHTML;
    expect(answer).toContain('Los <strong>Sheikah</strong> custodiaban:');
    expect(answer).toContain('<li>Sabiduría</li>');
    expect(component.streaming()).toBe(true);

    component.stop();

    expect(answers.observed).toBe(false);
    expect(service.find(id)!.messages.at(-1)!.status).toBe('stopped');
  });

  it('explains when an opened conversation is no longer in memory', async () => {
    params.next(convertToParamMap({ id: 'from-a-previous-session' }));
    await render();

    expect(component.missing()).toBe(true);
    expect(root().textContent).toContain('solo se guardan mientras dura la sesión');
  });
});
