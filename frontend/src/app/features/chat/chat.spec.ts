import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { Chat } from './chat';
import { ChatService } from '../../core/services/chat-service';
import { ConversationService } from '../../core/services/conversation-service';
import { ChatFilters, ChatStreamEvent, NO_FILTERS } from '../../core/models/chat.model';

const token = (text: string): ChatStreamEvent => ({ type: 'token', text });

describe('Chat', () => {
  let answers: Subject<ChatStreamEvent>;
  let askedFilters: ChatFilters[];
  let universes: Observable<string[]>;
  let params: BehaviorSubject<ParamMap>;
  let component: Chat;
  let fixture: ComponentFixture<Chat>;

  const root = () => fixture.nativeElement as HTMLElement;
  const universeSelect = () => root().querySelector<HTMLSelectElement>('.filter-select')!;
  const canonSwitch = () => root().querySelector<HTMLInputElement>('.canon-filter input')!;

  async function render() {
    fixture = TestBed.createComponent(Chat);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }

  // What the browser does when the user picks an option: change the value, then fire "change".
  async function chooseUniverse(value: string) {
    universeSelect().value = value;
    universeSelect().dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  // Starting a conversation navigates to it; the test router has no routes, so the navigation is only recorded.
  function stubNavigation(): unknown[][] {
    const targets: unknown[][] = [];
    TestBed.inject(Router).navigate = ((commands: unknown[]) => {
      targets.push(commands);
      return Promise.resolve(true);
    }) as Router['navigate'];
    return targets;
  }

  beforeEach(() => {
    askedFilters = [];
    universes = of(['Sabotage Studio Universe', 'The Legend of Zelda']);
    params = new BehaviorSubject(convertToParamMap({}));
    TestBed.configureTestingModule({
      imports: [Chat],
      providers: [
        provideRouter([]),
        {
          provide: ChatService,
          useValue: {
            ask: (_question: string, filters: ChatFilters) => {
              askedFilters.push(filters);
              answers = new Subject<ChatStreamEvent>();
              return answers;
            },
            universes: () => universes,
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

    answers.next(token('Los **Sheikah**'));
    answers.next(token(' custodiaban:\n\n- Sabiduría'));
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

  it('lists the consulted documents numbered like the citations, tagging the non-canon ones', async () => {
    const service = TestBed.inject(ConversationService);
    const id = service.start('¿Quién era Ganon?');
    params.next(convertToParamMap({ id }));
    await render();

    answers.next({
      type: 'sources',
      sources: [
        { number: 1, documentId: 'doc-a', title: 'Crónicas de Hyrule', universe: 'The Legend of Zelda', work: 'Breath of the Wild', canonicity: 'CANON' },
        { number: 2, documentId: 'doc-b', title: 'Teorías de fans', universe: 'The Legend of Zelda', work: null, canonicity: 'SPECULATION' },
      ],
    });
    answers.next(token('Ganon era el Rey de la Calamidad [1][2].'));
    fixture.detectChanges();
    await fixture.whenStable();

    const chips = Array.from(root().querySelectorAll('.source-chip'));
    expect(root().textContent).toContain('Documentos consultados');
    expect(chips.map(chip => chip.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      '1Crónicas de Hyrule',
      '2Teorías de fans Especulación',
    ]);
    expect(chips[0].getAttribute('title')).toBe('The Legend of Zelda · Breath of the Wild');
  });

  it('offers every approved universe and searches the whole archive by default', async () => {
    await render();

    expect(Array.from(universeSelect().options).map(option => option.text)).toEqual([
      'Todos los universos',
      'Sabotage Studio Universe',
      'The Legend of Zelda',
    ]);
    expect(universeSelect().value).toBe('');
    expect(canonSwitch().checked).toBe(false);
    expect(root().querySelector('.filter-chip.is-active')).toBeNull();
  });

  it('starts the conversation with the filters chosen before the first question', async () => {
    stubNavigation();
    await render();

    await chooseUniverse('The Legend of Zelda');
    canonSwitch().click();
    component.send('¿Quién es Link?');

    const [conversation] = TestBed.inject(ConversationService).conversations();
    expect(conversation.filters).toEqual({ universe: 'The Legend of Zelda', canonOnly: true });
    expect(askedFilters).toEqual([{ universe: 'The Legend of Zelda', canonOnly: true }]);
  });

  it('changes the filters of the open conversation and highlights them', async () => {
    const service = TestBed.inject(ConversationService);
    const id = service.start('¿Quién es Link?');
    params.next(convertToParamMap({ id }));
    await render();

    await chooseUniverse('Sabotage Studio Universe');

    expect(service.find(id)!.filters).toEqual({ universe: 'Sabotage Studio Universe', canonOnly: false });
    expect(root().querySelector('.universe-filter')!.classList).toContain('is-active');
    expect(root().querySelector('.canon-filter')!.classList).not.toContain('is-active');
  });

  it('shows the filters of the conversation that is open', async () => {
    const service = TestBed.inject(ConversationService);
    // A universe that is no longer in the list still shows up, so the conversation does not look unfiltered.
    const id = service.start('¿Quién es Link?', { universe: 'Retired Universe', canonOnly: true });
    params.next(convertToParamMap({ id }));
    await render();

    expect(universeSelect().value).toBe('Retired Universe');
    expect(canonSwitch().checked).toBe(true);
  });

  it('still lets the user ask when the universes cannot be loaded', async () => {
    universes = throwError(() => new Error('HTTP 500'));
    stubNavigation();
    await render();

    expect(Array.from(universeSelect().options).map(option => option.text)).toEqual(['Todos los universos']);

    component.send('¿Quién es Link?');
    expect(askedFilters).toEqual([NO_FILTERS]);
  });

  it('explains when an opened conversation is no longer in memory', async () => {
    params.next(convertToParamMap({ id: 'from-a-previous-session' }));
    await render();

    expect(component.missing()).toBe(true);
    expect(root().textContent).toContain('solo se guardan mientras dura la sesión');
  });
});
