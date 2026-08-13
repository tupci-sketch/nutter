import { useEffect, useState } from 'react';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';
import { Panel } from './Panel';

interface Achievement {
  id: number;
  name: string;
  description: string;
  category: string;
  progress: number;
  target: number;
  completed: boolean;
  completedAt: string | null;
}

export function AchievementsPanel() {
  const [achievements, setAchievements] = useState<Achievement[]>([]);

  useEffect(() => {
    const ws = getWsClient();
    const unsub = ws.on(Packet.ACH_LIST_RESULT, (raw) => {
      const p = raw as { achievements: Achievement[] };
      setAchievements(p.achievements);
    });
    ws.send(Packet.ACH_LIST, {});
    return unsub;
  }, []);

  const completed = achievements.filter(a => a.completed);
  const pending   = achievements.filter(a => !a.completed);

  return (
    <Panel title="Achievements" width={320}>
      <div style={styles.summary}>
        {completed.length}/{achievements.length} completed
      </div>
      {pending.map(a => (
        <AchRow key={a.id} ach={a} />
      ))}
      {completed.length > 0 && (
        <div style={styles.sectionLabel}>Completed</div>
      )}
      {completed.map(a => (
        <AchRow key={a.id} ach={a} />
      ))}
    </Panel>
  );
}

function AchRow({ ach }: { ach: Achievement }) {
  const pct = Math.min(100, (ach.progress / ach.target) * 100);
  return (
    <div style={{ ...styles.row, opacity: ach.completed ? 0.6 : 1 }}>
      <div style={styles.rowTop}>
        <span style={styles.achName}>{ach.completed ? '✅ ' : ''}{ach.name}</span>
        <span style={styles.achProg}>{ach.progress}/{ach.target}</span>
      </div>
      <div style={styles.achDesc}>{ach.description}</div>
      {!ach.completed && (
        <div style={styles.barBg}>
          <div style={{ ...styles.barFill, width: `${pct}%` }} />
        </div>
      )}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  summary: { color: '#f0a040', fontSize: 12, marginBottom: 10, textAlign: 'right' },
  sectionLabel: { color: '#888', fontSize: 11, marginTop: 12, marginBottom: 4 },
  row: { background: '#2a2a3e', border: '1px solid #444', borderRadius: 4,
    padding: 8, marginBottom: 6 },
  rowTop: { display: 'flex', justifyContent: 'space-between', marginBottom: 2 },
  achName: { color: '#e8e0d0', fontSize: 13, fontWeight: 'bold' },
  achProg: { color: '#888', fontSize: 11 },
  achDesc: { color: '#aaa', fontSize: 11, marginBottom: 4 },
  barBg: { height: 4, background: '#444', borderRadius: 2, overflow: 'hidden' },
  barFill: { height: '100%', background: '#f0a040', transition: 'width 0.3s', borderRadius: 2 },
};
