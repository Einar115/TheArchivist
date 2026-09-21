/**
 * Incremental parser for the text/event-stream the chat endpoint writes.
 *
 * Spring writes each token as `data:<token>` with nothing in between, and LLM tokens usually carry their own leading
 * space (" Hyrule"). So unlike a strict EventSource, the text after `data:` is kept verbatim: trimming it, or dropping
 * the one optional space the SSE spec allows, glues the words together. A token that contains line breaks arrives as
 * several `data:` lines of the same event, which are joined back with '\n'.
 */
export class SseParser {
  private buffer = '';
  private data: string[] = [];

  /** Feeds a decoded chunk and returns the payload of every event it completed. */
  push(chunk: string): string[] {
    this.buffer += chunk;
    const lines = this.buffer.split('\n');
    this.buffer = lines.pop() ?? '';

    const events: string[] = [];
    for (const raw of lines) {
      const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw;
      if (line === '') {
        if (this.data.length) events.push(this.data.join('\n'));
        this.data = [];
      } else if (line.startsWith('data:')) {
        this.data.push(line.slice('data:'.length));
      }
      // event:, id:, retry: and comments are not used by this endpoint.
    }
    return events;
  }

  /** Flushes an event left open when the stream ends without its trailing blank line. */
  end(): string[] {
    const events = this.push('\n');
    if (this.data.length) events.push(this.data.join('\n'));
    this.data = [];
    return events;
  }
}
