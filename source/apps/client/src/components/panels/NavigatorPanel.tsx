import { useEffect, useState } from 'react';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';
import { useRoomStore } from '@/stores/roomStore';
import { Panel } from './Panel';

interface RoomEntry {
  id: number;
  name: string;
  ownerName: string;
  /** 0 open, 1 doorbell, 2 password, 3 invisible. */
  accessType: number;
  maxVisitors: number;
  playerCount: number;
  score: number;
}

export function NavigatorPanel() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<RoomEntry[]>([]);
  const [tab, setTab] = useState<'popular' | 'search' | 'mine'>('popular');
  const enterRoom = useRoomStore(s => s.enterRoom);

  function search() {
    const ws = getWsClient();
    if (tab === 'popular') {
      ws.send(Packet.ROOM_NAV_POPULAR, {});
    } else if (tab === 'mine') {
      ws.send(Packet.ROOM_NAV_MY_ROOMS, {});
    } else {
      ws.send(Packet.ROOM_NAV_SEARCH, { query });
    }
  }

  function loadTab(t: typeof tab) {
    setTab(t);
    const ws = getWsClient();
    if (t === 'popular') ws.send(Packet.ROOM_NAV_POPULAR, {});
    else if (t === 'mine') ws.send(Packet.ROOM_NAV_MY_ROOMS, {});
  }

  useEffect(() => {
    const ws = getWsClient();
    const unsub = [
      ws.on(Packet.ROOM_NAV_POPULAR_RESULT, (raw) => {
        const p = raw as { rooms: RoomEntry[] };
        setResults(p.rooms ?? []);
      }),
      ws.on(Packet.ROOM_NAV_SEARCH_RESULT, (raw) => {
        const p = raw as { rooms: RoomEntry[] };
        setResults(p.rooms ?? []);
      }),
      ws.on(Packet.ROOM_NAV_MY_ROOMS_RESULT, (raw) => {
        const p = raw as { rooms: RoomEntry[] };
        setResults(p.rooms ?? []);
      }),
    ];
    ws.send(Packet.ROOM_NAV_POPULAR, {});
    return () => unsub.forEach(u => u());
  }, []);

  return (
    <Panel title="Navigator" width={320}>
      <div style={styles.tabs}>
        {(['popular', 'search', 'mine'] as const).map(t => (
          <button
            key={t}
            style={{ ...styles.tab, ...(tab === t ? styles.tabActive : {}) }}
            onClick={() => loadTab(t)}
          >
            {t}
          </button>
        ))}
      </div>
      {tab === 'search' && (
        <div style={styles.searchRow}>
          <input
            style={styles.input}
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && search()}
            placeholder="Search rooms..."
          />
          <button style={styles.searchBtn} onClick={search}>Go</button>
        </div>
      )}
      <div style={styles.list}>
        {results.map(room => (
          <div key={room.id} style={styles.room} onClick={() => enterRoom(room.id)}>
            <div style={styles.roomName}>{room.name}</div>
            <div style={styles.roomMeta}>by {room.ownerName} · {room.playerCount}/{room.maxVisitors}</div>
          </div>
        ))}
        {results.length === 0 && <div style={styles.empty}>No rooms found.</div>}
      </div>
    </Panel>
  );
}

const styles: Record<string, React.CSSProperties> = {
  tabs: { display: 'flex', gap: 4, marginBottom: 8 },
  tab: {
    flex: 1, padding: '4px 0', background: '#2a2a3e', border: '1px solid #444',
    borderRadius: 4, cursor: 'pointer', color: '#aaa', fontSize: 12,
  },
  tabActive: { background: '#f0a040', color: '#1a1a2e', border: '1px solid #c07030' },
  searchRow: { display: 'flex', gap: 4, marginBottom: 8 },
  input: {
    flex: 1, background: '#2a2a3e', border: '1px solid #444', borderRadius: 4,
    padding: '4px 8px', color: '#e8e0d0', fontSize: 13, fontFamily: 'monospace',
  },
  searchBtn: {
    background: '#f0a040', border: 'none', borderRadius: 4, padding: '4px 10px',
    cursor: 'pointer', color: '#1a1a2e', fontWeight: 'bold',
  },
  list: { overflowY: 'auto', flex: 1 },
  room: {
    padding: '6px 8px', borderBottom: '1px solid #333', cursor: 'pointer',
    transition: 'background 0.15s',
  },
  roomName: { color: '#e8e0d0', fontSize: 13, fontFamily: 'monospace' },
  roomMeta: { color: '#888', fontSize: 11, marginTop: 2 },
  empty: { color: '#666', textAlign: 'center', padding: 16, fontSize: 13 },
};
