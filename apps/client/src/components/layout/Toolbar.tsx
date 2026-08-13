import { useUiStore, type Panel } from '@/stores/uiStore';
import { useAuthStore } from '@/stores/authStore';
import { useSocialStore } from '@/stores/socialStore';

const TOOLS: Array<{ id: Panel; label: string }> = [
  { id: 'navigator',    label: '🗺' },
  { id: 'catalogue',   label: '🛍' },
  { id: 'inventory',   label: '🎒' },
  { id: 'friends',     label: '👥' },
  { id: 'groups',      label: '🏘' },
  { id: 'profile',     label: '👤' },
  { id: 'marketplace', label: '🏪' },
  { id: 'achievements',label: '🏆' },
  { id: 'quests',      label: '📜' },
  { id: 'garden',      label: '🌱' },
  { id: 'games',       label: '🎮' },
  { id: 'rp',          label: '🎭' },
];

export function Toolbar() {
  const { activePanel, togglePanel } = useUiStore();
  const { credits, diamonds, nutPoints } = useAuthStore();
  const unread = useSocialStore((s) => s.unreadCount);

  return (
    <div style={styles.bar}>
      <div style={styles.currency}>
        <span style={styles.chip}>💰 {credits.toLocaleString()}</span>
        <span style={styles.chip}>💎 {diamonds.toLocaleString()}</span>
        <span style={styles.chip}>🌰 {nutPoints.toLocaleString()}</span>
      </div>
      <div style={styles.tools}>
        {TOOLS.map(({ id, label }) => (
          <button
            key={id}
            style={{
              ...styles.btn,
              ...(activePanel === id ? styles.active : {}),
            }}
            onClick={() => togglePanel(id)}
            title={id ?? ''}
          >
            {label}
            {id === 'friends' && unread > 0 && (
              <span style={styles.badge}>{unread}</span>
            )}
          </button>
        ))}
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  bar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 52,
    background: 'rgba(10,10,20,0.95)',
    borderTop: '1px solid #333',
    display: 'flex',
    alignItems: 'center',
    padding: '0 8px',
    gap: 8,
    zIndex: 100,
  },
  currency: { display: 'flex', gap: 6, marginRight: 'auto' },
  chip: {
    background: '#2a2a3e',
    border: '1px solid #444',
    borderRadius: 4,
    padding: '2px 8px',
    fontSize: 12,
    color: '#e8e0d0',
    fontFamily: 'monospace',
  },
  tools: { display: 'flex', gap: 4 },
  btn: {
    width: 36,
    height: 36,
    background: '#2a2a3e',
    border: '1px solid #444',
    borderRadius: 4,
    cursor: 'pointer',
    fontSize: 18,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  active: {
    background: '#f0a040',
    border: '1px solid #c07030',
  },
  badge: {
    position: 'absolute',
    top: -4,
    right: -4,
    background: '#e03030',
    color: '#fff',
    borderRadius: '50%',
    width: 16,
    height: 16,
    fontSize: 10,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
};
