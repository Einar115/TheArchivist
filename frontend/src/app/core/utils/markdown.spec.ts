import { renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
  it('escapes HTML before formatting', () => {
    expect(renderMarkdown('<img src=x onerror=alert(1)> **ok**')).toBe(
      '<p>&lt;img src=x onerror=alert(1)&gt; <strong>ok</strong></p>'
    );
  });

  it('renders bullet and numbered lists', () => {
    expect(renderMarkdown('- Poder\n- Valor')).toBe('<ul><li>Poder</li><li>Valor</li></ul>');
    expect(renderMarkdown('1. Uno\n2. Dos')).toBe('<ol><li>Uno</li><li>Dos</li></ol>');
  });

  it('separates paragraphs and keeps single line breaks', () => {
    expect(renderMarkdown('Uno\nsigue\n\nDos')).toBe('<p>Uno<br>sigue</p><p>Dos</p>');
  });

  it('renders fenced code verbatim, even while the fence is still open', () => {
    expect(renderMarkdown('```\n**no** <b>\n')).toBe('<pre><code>**no** &lt;b&gt;\n</code></pre>');
  });
});
