/**
 * Picking up the ticket the website left for us.
 *
 * A player signs in on the site and clicks through to the hotel. The site puts
 * a single-use ticket in the address, we read it, use it, and take it back out
 * of the address bar so a shared link or a bookmark does not carry somebody's
 * session around with it.
 */

export interface Handoff {
  ticket: string;
  world: string | null;
}

/** Read the ticket out of the current address, if there is one. */
export function readHandoff(search: string = window.location.search): Handoff | null {
  let params: URLSearchParams;
  try {
    params = new URLSearchParams(search);
  } catch {
    return null;
  }

  const ticket = params.get('ticket');
  if (!ticket || !ticket.trim()) return null;

  return { ticket: ticket.trim(), world: params.get('world') };
}

/**
 * Take the ticket back out of the address bar.
 *
 * The ticket is spent the moment the hotel reads it, so what is left in the
 * address is useless — but it is still somebody's, and it has no business
 * sitting in a browser history or being pasted into a chat along with the link.
 */
export function clearHandoff(): void {
  if (typeof window === 'undefined' || !window.history?.replaceState) return;

  const url = new URL(window.location.href);
  if (!url.searchParams.has('ticket')) return;

  url.searchParams.delete('ticket');
  const query = url.searchParams.toString();
  window.history.replaceState({}, '', url.pathname + (query ? `?${query}` : '') + url.hash);
}
