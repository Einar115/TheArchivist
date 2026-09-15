/**
 * Reads a browser cookie by name.
 *
 * Angular's HttpClient attaches the CSRF header on its own, but only for requests it issues.
 * Anything built on raw fetch (the SSE chat stream) has to read the token and send it manually.
 */
export function readCookie(name: string): string | null {
  const match = document.cookie
    .split('; ')
    .find(entry => entry.startsWith(`${name}=`));

  return match ? decodeURIComponent(match.substring(name.length + 1)) : null;
}
