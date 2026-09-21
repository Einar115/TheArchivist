/**
 * Minimal Markdown to HTML for chat answers: paragraphs, headings, lists, fenced code, bold, italic and inline code.
 *
 * Everything is HTML-escaped before any formatting is applied, so model output can never inject markup; Angular's
 * sanitizer still runs on top when the result is bound through [innerHTML]. It copes with partial input, which is
 * what it gets while an answer is still streaming.
 */
export function renderMarkdown(source: string): string {
  const html: string[] = [];
  let paragraph: string[] = [];
  let list: { ordered: boolean; items: string[] } | null = null;
  let code: string[] | null = null;

  const flushParagraph = () => {
    if (paragraph.length) html.push(`<p>${inline(paragraph.join('\n')).replace(/\n/g, '<br>')}</p>`);
    paragraph = [];
  };
  const flushList = () => {
    if (list) {
      const tag = list.ordered ? 'ol' : 'ul';
      html.push(`<${tag}>${list.items.map(item => `<li>${inline(item)}</li>`).join('')}</${tag}>`);
    }
    list = null;
  };

  for (const line of source.replace(/\r\n?/g, '\n').split('\n')) {
    if (code) {
      if (/^\s*```/.test(line)) {
        html.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`);
        code = null;
      } else {
        code.push(line);
      }
      continue;
    }

    const heading = /^(#{1,3})\s+(.*)$/.exec(line);
    const item = /^\s*(?:[-*•]|(\d+)[.)])\s+(.*)$/.exec(line);

    if (/^\s*```/.test(line)) {
      flushParagraph();
      flushList();
      code = [];
    } else if (heading) {
      flushParagraph();
      flushList();
      // Answers sit inside the page, so their headings start below the page's own.
      const level = heading[1].length + 3;
      html.push(`<h${level}>${inline(heading[2])}</h${level}>`);
    } else if (item) {
      flushParagraph();
      const ordered = item[1] !== undefined;
      if (list && list.ordered !== ordered) flushList();
      list ??= { ordered, items: [] };
      list.items.push(item[2]);
    } else if (!line.trim()) {
      flushParagraph();
      flushList();
    } else {
      flushList();
      paragraph.push(line);
    }
  }

  // A fence still open means the answer is mid-stream: show what arrived so far.
  if (code) html.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`);
  flushParagraph();
  flushList();
  return html.join('');
}

function inline(text: string): string {
  return escapeHtml(text)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>');
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
