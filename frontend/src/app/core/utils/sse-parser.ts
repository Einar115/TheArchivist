/** One event of the stream: its name ("message" when the server sends none, as for answer tokens) and its data. */
export interface SseEvent {
  event: string;
  data: string;
}

/**
 * Incremental parser for the text/event-stream the chat endpoint writes.
 *
 * Spring (since Framework 7.1) writes each token as `data: <token>`, with the single space the SSE spec puts between a
 * field and its value. LLM tokens usually carry their own leading space too (" Hyrule" arrives as `data:  Hyrule`), so
 * exactly that one separator space is dropped, as EventSource does, and everything after it is kept: trimming would
 * glue the words together. A token that contains line breaks arrives as several `data:` lines of the same event, which
 * are joined back with '\n'. Named events (`event: sources`) carry their name; the value after `event:` is trimmed,
 * since it is an identifier rather than text.
 */
export class SseParser {
  private buffer = '';
  private data: string[] = [];
  private event = '';

  /** Feeds a decoded chunk and returns every event it completed. */
  push(chunk: string): SseEvent[] {
    this.buffer += chunk;
    const lines = this.buffer.split('\n');
    this.buffer = lines.pop() ?? '';

    const events: SseEvent[] = [];
    for (const raw of lines) {
      const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw;
      if (line === '') {
        this.dispatch(events);
      } else if (line.startsWith('data:')) {
        this.data.push(fieldValue(line, 'data:'));
      } else if (line.startsWith('event:')) {
        this.event = line.slice('event:'.length).trim();
      }
      // id:, retry: and comments are not used by this endpoint.
    }
    return events;
  }

  /** Flushes an event left open when the stream ends without its trailing blank line. */
  end(): SseEvent[] {
    const events = this.push('\n');
    this.dispatch(events);
    return events;
  }

  private dispatch(events: SseEvent[]): void {
    if (this.data.length) events.push({ event: this.event || 'message', data: this.data.join('\n') });
    this.data = [];
    this.event = '';
  }
}

/** The value of a field line: what follows the colon, minus the one space the SSE spec allows as a separator. */
function fieldValue(line: string, field: string): string {
  const value = line.slice(field.length);
  return value.startsWith(' ') ? value.slice(1) : value;
}
