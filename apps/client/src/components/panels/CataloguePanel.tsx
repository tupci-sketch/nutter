import { useEffect, useState } from 'react';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';
import { Panel } from './Panel';

interface CatItem {
  id: number;
  name: string;
  description: string;
  credits: number;
  diamonds: number;
  furniId: number;
}

interface CatPage {
  id: number;
  name: string;
  items: CatItem[];
}

export function CataloguePanel() {
  const [page, setPage] = useState<CatPage | null>(null);
  const [pages, setPages] = useState<CatPage[]>([]);

  useEffect(() => {
    const ws = getWsClient();
    const unsub = ws.on(Packet.CAT_PAGE_RESULT, (raw) => {
      const p = raw as { pages?: CatPage[]; page?: CatPage };
      if (p.pages) {
        setPages(p.pages);
        if (p.pages[0]) loadPage(p.pages[0].id);
      }
      if (p.page) setPage(p.page);
    });
    ws.send(Packet.CAT_PAGE, { pageId: 0 });
    return unsub;
  }, []);

  function loadPage(pageId: number) {
    getWsClient().send(Packet.CAT_PAGE, { pageId });
  }

  function purchase(itemId: number) {
    getWsClient().send(Packet.CAT_PURCHASE, { itemId, quantity: 1 });
  }

  return (
    <Panel title="Catalogue" width={340}>
      <div style={styles.layout}>
        <div style={styles.sidebar}>
          {pages.map(p => (
            <div
              key={p.id}
              style={styles.sideItem}
              onClick={() => loadPage(p.id)}
            >
              {p.name}
            </div>
          ))}
        </div>
        <div style={styles.content}>
          {page?.items.map(item => (
            <div key={item.id} style={styles.item}>
              <div style={styles.itemName}>{item.name}</div>
              <div style={styles.itemDesc}>{item.description}</div>
              <div style={styles.itemFooter}>
                {item.credits > 0 && <span style={styles.price}>💰 {item.credits}</span>}
                {item.diamonds > 0 && <span style={styles.price}>💎 {item.diamonds}</span>}
                <button style={styles.buyBtn} onClick={() => purchase(item.id)}>Buy</button>
              </div>
            </div>
          ))}
          {!page && <div style={styles.empty}>Select a category.</div>}
        </div>
      </div>
    </Panel>
  );
}

const styles: Record<string, React.CSSProperties> = {
  layout: { display: 'flex', gap: 8, flex: 1, overflow: 'hidden' },
  sidebar: { width: 100, overflowY: 'auto', flexShrink: 0 },
  sideItem: {
    padding: '6px 4px', fontSize: 12, color: '#aaa', cursor: 'pointer',
    borderBottom: '1px solid #333',
  },
  content: { flex: 1, overflowY: 'auto' },
  item: {
    background: '#2a2a3e', border: '1px solid #444', borderRadius: 4,
    padding: 8, marginBottom: 6,
  },
  itemName: { color: '#f0a040', fontSize: 13, fontWeight: 'bold', marginBottom: 2 },
  itemDesc: { color: '#aaa', fontSize: 11, marginBottom: 6 },
  itemFooter: { display: 'flex', alignItems: 'center', gap: 6 },
  price: { color: '#e8e0d0', fontSize: 12 },
  buyBtn: {
    marginLeft: 'auto', background: '#f0a040', border: 'none', borderRadius: 4,
    padding: '2px 10px', cursor: 'pointer', color: '#1a1a2e', fontWeight: 'bold', fontSize: 12,
  },
  empty: { color: '#666', textAlign: 'center', padding: 24 },
};
