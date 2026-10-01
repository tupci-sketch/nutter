import { useEffect, useState } from 'react';
import { useAuthStore } from '@/stores/authStore';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';
import { Panel } from './Panel';

interface Badge {
  code: string;
  name: string;
}

interface ProfileData {
  userId: number;
  username: string;
  figure: string;
  motto: string;
  credits: number;
  diamonds: number;
  nutPoints: number;
  level: number;
  xp: number;
  xpToNextLevel: number;
  roomsOwned: number;
  friendCount: number;
  achievementPoints: number;
  memberSince: string;
  lastSeen: string;
  online: boolean;
  equippedBadges: Badge[];
}

export function ProfilePanel() {
  const { userId } = useAuthStore();
  const [profile, setProfile] = useState<ProfileData | null>(null);

  useEffect(() => {
    const ws = getWsClient();
    const unsub = ws.on(Packet.PROFILE_VIEW_RESULT, (raw) => {
      const p = raw as { profile: ProfileData };
      setProfile(p.profile ?? null);
    });
    ws.send(Packet.PROFILE_VIEW, { userId });
    return unsub;
  }, [userId]);

  return (
    <Panel title="Profile" width={300}>
      {!profile ? (
        <div style={styles.empty}>Loading…</div>
      ) : (
        <div style={styles.body}>
          <div style={styles.avatar} aria-hidden="true">
            {profile.username.slice(0, 1).toUpperCase()}
          </div>
          <div style={styles.name}>{profile.username}</div>
          <div style={styles.meta}>Level {profile.level}</div>
          {profile.motto && <div style={styles.motto}>"{profile.motto}"</div>}
          <div style={styles.stats}>
            <Stat label="Achievement Points" value={profile.achievementPoints} />
            <Stat label="Friends" value={profile.friendCount} />
            <Stat label="Rooms" value={profile.roomsOwned} />
            <Stat label="Member Since" value={profile.memberSince?.slice(0, 10) ?? '—'} />
          </div>
        </div>
      )}
    </Panel>
  );
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div style={styles.stat}>
      <span style={styles.statLabel}>{label}</span>
      <span style={styles.statValue}>{value}</span>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  body: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 },
  avatar: { fontSize: 64, lineHeight: 1, marginBottom: 4 },
  name: { fontSize: 18, fontWeight: 'bold', color: '#f0a040' },
  meta: { fontSize: 12, color: '#888' },
  motto: { fontSize: 12, color: '#aaa', fontStyle: 'italic', textAlign: 'center' },
  stats: { width: '100%', marginTop: 12 },
  stat: { display: 'flex', justifyContent: 'space-between', padding: '4px 0',
    borderBottom: '1px solid #333' },
  statLabel: { color: '#888', fontSize: 12 },
  statValue: { color: '#e8e0d0', fontSize: 12 },
  empty: { color: '#666', textAlign: 'center', padding: 24 },
};
