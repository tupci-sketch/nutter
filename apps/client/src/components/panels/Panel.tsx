import type { ReactNode } from 'react';
import { useUiStore } from '@/stores/uiStore';

interface PanelProps {
  title: string;
  width?: number;
  children: ReactNode;
}

export function Panel({ title, width = 300, children }: PanelProps) {
  const { closePanel } = useUiStore();

  return (
    <div style={{ ...styles.panel, width }}>
      <div style={styles.header}>
        <span style={styles.title}>{title}</span>
        <button style={styles.close} onClick={closePanel}>✕</button>
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
    fontSize: 14,
  },
  close: {
    background: 'none',
    border: 'none',
    color: '#888',
    cursor: 'pointer',
    fontSize: 16,
    lineHeight: 1,
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
  },
};
