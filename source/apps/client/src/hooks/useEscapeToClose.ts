import { useEffect } from 'react';

/**
 * Closes something when Escape is pressed.
 *
 * A panel that can only be closed by clicking a small ✕ is a panel somebody
 * using a keyboard is stuck inside.
 */
export function useEscapeToClose(active: boolean, close: () => void): void {
  useEffect(() => {
    if (!active) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.stopPropagation();
      close();
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [active, close]);
}
