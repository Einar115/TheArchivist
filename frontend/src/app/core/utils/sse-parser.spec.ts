import { SseParser } from './sse-parser';

describe('SseParser', () => {
  it('keeps the leading space each token carries', () => {
    const parser = new SseParser();

    expect(parser.push('data:La\n\ndata: familia\n\ndata: real\n\n')).toEqual(['La', ' familia', ' real']);
  });

  it('joins the data lines of one event with line breaks', () => {
    expect(new SseParser().push('data:Poder\ndata:\ndata:- Valor\n\n')).toEqual(['Poder\n\n- Valor']);
  });

  it('waits for events split across chunks', () => {
    const parser = new SseParser();

    expect(parser.push('data: Hy')).toEqual([]);
    expect(parser.push('rule\r\n\r\n')).toEqual([' Hyrule']);
  });

  it('flushes an event left open when the stream ends', () => {
    const parser = new SseParser();
    parser.push('data:fin');

    expect(parser.end()).toEqual(['fin']);
  });
});
