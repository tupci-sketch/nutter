import { useEffect } from 'react';
import { useInventoryStore } from '@/stores/inventoryStore';
import { Panel } from './Panel';

export function InventoryPanel() {
  const { items, load, loaded } = useInventoryStore();

  useEffect(() => { if (!loaded) load(); }, [loaded, load]);

  return (
    <Panel title={`Inventory (${items.length})`} width={300}>
      {items.length === 0 ? (
        <div style={styles.empty}>Your inventory is empty.</div>
      ) : (
        <div style={styles.grid}>
          {items.map(item => (
            <div key={item.id} style={styles.cell} title={item.name}>
              <div style={styles.icon}>🪑</div>
              <div style={styles.label}>{item.name}</div>
            </div>
          ))}
        </div>
      )}
    </Panel>
  );
}

const styles: Record<string, React.CSSProperties> = {
  grid: { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6 },
  cell: {
    background: '#2a2a3e', border: '1px solid #444', borderRadius: 4,
    padding: 4, textAlign: 'center', cursor: 'pointer',
  },
  icon: { fontSize: 24, lineHeight: 1.2 },
  label: { fontSize: 10, color: '#aaa', marginTop: 2, overflow: 'hidden',
    textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  empty: { color: '#666', textAlign: 'center', padding: 24 },
};
