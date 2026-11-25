import { SseParser } from './sse-parser';

describe('SseParser', () => {
  const data = (events: { data: string }[]) => events.map(event => event.data);

  it('drops the separator space but keeps the leading space each token carries', () => {
    const parser = new SseParser();

    expect(data(parser.push('data: La\n\ndata:  familia\n\ndata:  real\n\n'))).toEqual(['La', ' familia', ' real']);
  });

  it('also reads values written without the separator space', () => {
    expect(data(new SseParser().push('data:Hola\n\n'))).toEqual(['Hola']);
  });

  it('joins the data lines of one event with line breaks', () => {
    expect(data(new SseParser().push('data: Poder\ndata:\ndata: - Valor\n\n'))).toEqual(['Poder\n\n- Valor']);
  });

  it('waits for events split across chunks', () => {
    const parser = new SseParser();

    expect(parser.push('data:  Hy')).toEqual([]);
    expect(data(parser.push('rule\r\n\r\n'))).toEqual([' Hyrule']);
  });

  it('flushes an event left open when the stream ends', () => {
    const parser = new SseParser();
    parser.push('data: fin');

    expect(data(parser.end())).toEqual(['fin']);
  });

  it('names events after their event field and defaults to message', () => {
    const events = new SseParser().push('event: sources\ndata: [{"number":1}]\n\ndata: Hola\n\n');

    expect(events).toEqual([
      { event: 'sources', data: '[{"number":1}]' },
      { event: 'message', data: 'Hola' },
    ]);
  });

  it('does not carry an event name over to the next event', () => {
    const parser = new SseParser();
    parser.push('event: sources\ndata: []\n\n');

    expect(parser.push('data:  token\n\n')).toEqual([{ event: 'message', data: ' token' }]);
  });
});
