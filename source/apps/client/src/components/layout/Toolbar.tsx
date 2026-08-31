import { useUiStore, type Panel } from '@/stores/uiStore';
import { useAuthStore } from '@/stores/authStore';
import { useSocialStore } from '@/stores/socialStore';

/**
 * The tools, with a name for each.
 *
 * An emoji on its own is read aloud as whatever the screen reader happens to
 * call that picture — "world map", "backpack" — which tells nobody what the
 * button does. The name is what a screen reader announces and what a tooltip
 * shows; the emoji is decoration on top of it.
 */
const TOOLS: Array<{ id: Panel; icon: string; name: string }> = [
  { id: 'navigator',    icon: '🗺', name: 'Rooms' },
  { id: 'catalogue',    icon: '🛍', name: 'Catalogue' },
  { id: 'inventory',    icon: '🎒', name: 'Inventory' },
  { id: 'friends',      icon: '👥', name: 'Friends' },
  { id: 'groups',       icon: '🏘', name: 'Groups' },
  { id: 'profile',      icon: '👤', name: 'Profile' },
  { id: 'marketplace',  icon: '🏪', name: 'Marketplace' },
  { id: 'achievements', icon: '🏆', name: 'Achievements' },
  { id: 'quests',       icon: '📜', name: 'Quests' },
  { id: 'garden',       icon: '🌱', name: 'Garden' },
  { id: 'games',        icon: '🎮', name: 'Games' },
  { id: 'rp',           icon: '🎭', name: 'Roleplay' },
  { id: 'settings',     icon: '⚙️', name: 'Settings' },
];

export function Toolbar() {
  const { activePanel, togglePanel } = useUiStore();
  const { credits, diamonds, nutPoints } = useAuthStore();
  const unread = useSocialStore((s) => s.unreadCount);

  return (
    <div style={styles.bar}>
      <div style={styles.currency}>
        <span style={styles.chip}>
          <span aria-hidden="true">💰</span>
          <span className="sr-only">Credits: </span>
          {credits.toLocaleString()}
        </span>
        <span style={styles.chip}>
          <span aria-hidden="true">💎</span>
          <span className="sr-only">Diamonds: </span>
          {diamonds.toLocaleString()}
        </span>
        <span style={styles.chip}>
          <span aria-hidden="true">🌰</span>
          <span className="sr-only">Nut points: </span>
          {nutPoints.toLocaleString()}
        </span>
      </div>
      <nav style={styles.tools} aria-label="Hotel tools">
        {TOOLS.map(({ id, icon, name }) => (
          <button
            key={id}
            style={{
              ...styles.btn,
              ...(activePanel === id ? styles.active : {}),
            }}
            onClick={() => togglePanel(id)}
            title={name}
            aria-label={
              id === 'friends' && unread > 0
                ? `${name}, ${unread} unread`
                : name
            }
            aria-pressed={activePanel === id}
          >
            <span aria-hidden="true">{icon}</span>
            {id === 'friends' && unread > 0 && (
              <span style={styles.badge} aria-hidden="true">{unread}</span>
            )}
          </button>
        ))}
      </nav>
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
  // Twelve tools do not fit across a phone, so the row scrolls rather than
  // wrapping into a second row that would cover the room.
  tools: {
    display: 'flex',
    gap: 4,
    overflowX: 'auto',
    scrollbarWidth: 'none',
    WebkitOverflowScrolling: 'touch',
  },
  btn: {
    width: 44,
    height: 44,
    flex: 'none',
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
