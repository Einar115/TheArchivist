/** One event of the stream: its name ("message" when the server sends none, as for answer tokens) and its data. */
export interface SseEvent {
  event: string;
  data: string;
}

/**
 * Incremental parser for the text/event-stream the chat endpoint writes.
 *
 * Spring writes each token as `data:<token>` with nothing in between, and LLM tokens usually carry their own leading
 * space (" Hyrule"). So unlike a strict EventSource, the text after `data:` is kept verbatim: trimming it, or dropping
 * the one optional space the SSE spec allows, glues the words together. A token that contains line breaks arrives as
 * several `data:` lines of the same event, which are joined back with '\n'. Named events (`event:sources`) carry their
 * name; the value after `event:` is trimmed, since it is an identifier rather than text.
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
        this.data.push(line.slice('data:'.length));
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
