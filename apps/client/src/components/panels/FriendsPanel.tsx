import { useEffect, useState } from 'react';
import { useSocialStore } from '@/stores/socialStore';
import { Panel } from './Panel';

export function FriendsPanel() {
  const { friends, loadFriends, removeFriend, sendFriendRequest } = useSocialStore();
  const [newFriend, setNewFriend] = useState('');

  useEffect(() => { loadFriends(); }, []);

  function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    if (newFriend.trim()) {
      sendFriendRequest(newFriend.trim());
      setNewFriend('');
    }
  }

  const online  = friends.filter(f => f.online);
  const offline = friends.filter(f => !f.online);

  return (
    <Panel title={`Friends (${online.length}/${friends.length})`} width={280}>
      <form onSubmit={handleAdd} style={styles.addRow}>
        <input
          style={styles.input}
          value={newFriend}
          onChange={e => setNewFriend(e.target.value)}
          placeholder="Add friend by username"
        />
        <button style={styles.addBtn} type="submit">+</button>
      </form>
      <div style={styles.section}>Online</div>
      {online.map(f => (
        <FriendRow key={f.userId} friend={f} onRemove={removeFriend} />
      ))}
      {online.length === 0 && <div style={styles.empty}>No friends online.</div>}
      <div style={styles.section}>Offline</div>
      {offline.map(f => (
        <FriendRow key={f.userId} friend={f} onRemove={removeFriend} />
      ))}
    </Panel>
  );
}

function FriendRow({ friend, onRemove }: {
  friend: { userId: number; username: string; online: boolean };
  onRemove: (id: number) => void;
}) {
  return (
    <div style={styles.row}>
      <span style={{ ...styles.dot, background: friend.online ? '#40c070' : '#666' }} />
      <span style={styles.name}>{friend.username}</span>
      <button style={styles.removeBtn} onClick={() => onRemove(friend.userId)}>✕</button>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  addRow: { display: 'flex', gap: 4, marginBottom: 10 },
  input: {
    flex: 1, background: '#2a2a3e', border: '1px solid #444', borderRadius: 4,
    padding: '4px 8px', color: '#e8e0d0', fontSize: 13, fontFamily: 'monospace',
  },
  addBtn: {
    background: '#f0a040', border: 'none', borderRadius: 4, padding: '4px 10px',
    cursor: 'pointer', color: '#1a1a2e', fontWeight: 'bold', fontSize: 16,
  },
  section: { color: '#888', fontSize: 11, marginTop: 8, marginBottom: 4 },
  row: { display: 'flex', alignItems: 'center', gap: 8, padding: '4px 0' },
  dot: { width: 8, height: 8, borderRadius: '50%', flexShrink: 0 },
  name: { flex: 1, color: '#e8e0d0', fontSize: 13 },
  removeBtn: {
    background: 'none', border: 'none', color: '#666', cursor: 'pointer', fontSize: 14,
  },
  empty: { color: '#666', fontSize: 12, padding: '4px 0' },
};
