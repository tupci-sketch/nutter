import { useEffect, useRef, type ReactNode } from 'react';
import { useUiStore } from '@/stores/uiStore';
import { useA11yStore } from '@/stores/a11yStore';
import { useEscapeToClose } from '@/hooks/useEscapeToClose';
import { useViewport } from '@/hooks/useViewport';

interface PanelProps {
  title: string;
  width?: number;
  children: ReactNode;
}

/**
 * A panel docked beside the room — or, on a phone, covering it.
 *
 * There is no room left beside the room on a narrow screen, so the same panel
 * takes the whole screen instead of being squeezed into a strip. It is a dialog
 * either way: it takes focus when it opens, Escape closes it, and focus does
 * not wander back out into the room behind it while it is open.
 */
export function Panel({ title, width = 300, children }: PanelProps) {
  const closePanel = useUiStore((s) => s.closePanel);
  const largeText = useA11yStore((s) => s.largeText);
  const { compact } = useViewport();
  const panelRef = useRef<HTMLDivElement>(null);

  useEscapeToClose(true, closePanel);

  useEffect(() => {
    // Opening a panel and leaving focus behind in the room means a keyboard
    // user has to tab through everything to reach what they just opened.
    panelRef.current?.focus();
  }, []);

  /** Keeps Tab inside the panel while it is open. */
  function trapFocus(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Tab') return;

    const focusable = panelRef.current?.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    );
    if (!focusable || focusable.length === 0) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  const frame: React.CSSProperties = compact
    ? { ...styles.panel, ...styles.compact }
    : { ...styles.panel, width };

  return (
    <div
      ref={panelRef}
      role="dialog"
      aria-modal="true"
      aria-label={title}
      tabIndex={-1}
      onKeyDown={trapFocus}
      style={{ ...frame, fontSize: largeText ? 15 : undefined }}
    >
      <div style={styles.header}>
        <h2 style={{ ...styles.title, fontSize: largeText ? 16 : 14 }}>{title}</h2>
        <button
          style={{ ...styles.close, ...(compact ? styles.closeLarge : {}) }}
          onClick={closePanel}
          aria-label={`Close ${title}`}
        >
          ✕
        </button>
      </div>
      <div style={styles.body}>{children}</div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  panel: {
    position: 'absolute',
    top: 8,
    right: 8,
    bottom: 60,
    background: 'rgba(10,10,20,0.95)',
    border: '1px solid #444',
    borderRadius: 6,
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
    zIndex: 200,
  },
  // On a phone the panel is the screen. Anything less is a strip too narrow to
  // read a catalogue in.
  compact: {
    top: 0,
    right: 0,
    left: 0,
    bottom: 0,
    width: 'auto',
    borderRadius: 0,
    border: 'none',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    padding: '8px 12px',
    borderBottom: '1px solid #333',
    background: '#1a1a2e',
  },
  title: {
    flex: 1,
    color: '#f0a040',
    fontFamily: 'monospace',
    fontWeight: 'bold',
    margin: 0,
  },
  close: {
    background: 'none',
    border: 'none',
    color: '#888',
    cursor: 'pointer',
    fontSize: 16,
    lineHeight: 1,
  },
  // A finger needs a target it can actually hit.
  closeLarge: {
    minWidth: 44,
    minHeight: 44,
    fontSize: 20,
  },
  body: {
    flex: 1,
    overflowY: 'auto',
    padding: 12,
    display: 'flex',
    flexDirection: 'column',
    color: '#e8e0d0',
    fontFamily: 'monospace',
    fontSize: 13,
    // A panel of items on a phone should scroll under a thumb, not fight it.
    WebkitOverflowScrolling: 'touch',
  },
};
