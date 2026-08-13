import { useEffect, useState } from 'react';
import { getWsClient } from '@/ws/WsClient';
import { Packet } from '@/protocol/packets';
import { Panel } from './Panel';

interface Plot {
  id: number;
  ownerUserId: number;
  plantType: string | null;
  stage: string | null;
  plantedAt: string | null;
  wateredAt: string | null;
  readyAt: string | null;
  harvestYield: number;
}

interface PlantDef {
  id: number;
  type: string;
  name: string;
  growthMs: number;
}

interface Goal {
  id: number;
  description: string;
  targetCount: number;
  currentCount: number;
  completed: boolean;
}

export function GardenPanel() {
  const [plots, setPlots] = useState<Plot[]>([]);
  const [plants, setPlants] = useState<PlantDef[]>([]);
  const [goals, setGoals] = useState<Goal[]>([]);
  const [selectedPlant, setSelectedPlant] = useState('');

  useEffect(() => {
    const ws = getWsClient();
    const unsub = [
      ws.on(Packet.GARDEN_STATE_RESULT, (raw) => {
        const p = raw as { plots: Plot[]; plants: PlantDef[]; goals: Goal[] };
        setPlots(p.plots);
        setPlants(p.plants);
        setGoals(p.goals);
        if (p.plants[0]) setSelectedPlant(p.plants[0].type);
      }),
      ws.on(Packet.GARDEN_PLANTED,  () => ws.send(Packet.GARDEN_STATE, {})),
      ws.on(Packet.GARDEN_WATERED,  () => ws.send(Packet.GARDEN_STATE, {})),
      ws.on(Packet.GARDEN_HARVESTED,() => ws.send(Packet.GARDEN_STATE, {})),
      ws.on(Packet.GARDEN_GOAL_UPDATED, (raw) => {
        const p = raw as { goals: Goal[] };
        setGoals(p.goals);
      }),
    ];
    ws.send(Packet.GARDEN_STATE, {});
    return () => unsub.forEach(u => u());
  }, []);

  function plantOn(plotId: number) {
    getWsClient().send(Packet.GARDEN_PLANT, { plotId, plantType: selectedPlant });
  }
  function waterPlot(plotId: number) {
    getWsClient().send(Packet.GARDEN_WATER, { plotId });
  }
  function harvest(plotId: number) {
    getWsClient().send(Packet.GARDEN_HARVEST, { plotId });
  }

  return (
    <Panel title="Community Garden" width={340}>
      <div style={styles.section}>Community Goals</div>
      {goals.map(g => (
        <div key={g.id} style={styles.goal}>
          <div style={styles.goalDesc}>{g.description}</div>
          <div style={styles.goalBar}>
            <div style={{
              ...styles.goalFill,
              width: `${Math.min(100, (g.currentCount / g.targetCount) * 100)}%`,
              background: g.completed ? '#40c070' : '#f0a040',
            }} />
          </div>
          <div style={styles.goalCount}>{g.currentCount}/{g.targetCount}</div>
        </div>
      ))}

      <div style={styles.section}>Plant</div>
      <select
        style={styles.select}
        value={selectedPlant}
        onChange={e => setSelectedPlant(e.target.value)}
      >
        {plants.map(p => (
          <option key={p.id} value={p.type}>{p.name}</option>
        ))}
      </select>

      <div style={styles.section}>Your Plots</div>
      <div style={styles.plotGrid}>
        {plots.map(plot => (
          <div key={plot.id} style={styles.plot}>
            <div style={styles.plotStage}>{plot.stage ?? '—'}</div>
            <div style={styles.plotType}>{plot.plantType ?? 'Empty'}</div>
            <div style={styles.plotActions}>
              {!plot.plantType && (
                <button style={styles.btn} onClick={() => plantOn(plot.id)}>Plant</button>
              )}
              {plot.stage && plot.stage !== 'ready' && plot.stage !== 'withered' && (
                <button style={styles.btn} onClick={() => waterPlot(plot.id)}>Water</button>
              )}
              {plot.stage === 'ready' && (
                <button style={{ ...styles.btn, background: '#40c070' }}
                  onClick={() => harvest(plot.id)}>Harvest</button>
              )}
            </div>
          </div>
        ))}
      </div>
    </Panel>
  );
}

const styles: Record<string, React.CSSProperties> = {
  section: { color: '#f0a040', fontSize: 11, fontWeight: 'bold', marginTop: 10, marginBottom: 4 },
  goal: { background: '#2a2a3e', border: '1px solid #444', borderRadius: 4, padding: 8, marginBottom: 6 },
  goalDesc: { fontSize: 12, color: '#e8e0d0', marginBottom: 4 },
  goalBar: { height: 6, background: '#444', borderRadius: 3, overflow: 'hidden', marginBottom: 2 },
  goalFill: { height: '100%', transition: 'width 0.3s', borderRadius: 3 },
  goalCount: { fontSize: 11, color: '#888', textAlign: 'right' },
  select: {
    width: '100%', background: '#2a2a3e', border: '1px solid #444', borderRadius: 4,
    padding: '4px 8px', color: '#e8e0d0', fontSize: 13, marginBottom: 6,
  },
  plotGrid: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 },
  plot: {
    background: '#2a2a3e', border: '1px solid #444', borderRadius: 4,
    padding: 8, textAlign: 'center',
  },
  plotStage: { fontSize: 12, color: '#aaa', marginBottom: 2 },
  plotType: { fontSize: 13, color: '#e8e0d0', marginBottom: 6 },
  plotActions: { display: 'flex', gap: 4, justifyContent: 'center' },
  btn: {
    background: '#f0a040', border: 'none', borderRadius: 4, padding: '2px 8px',
    cursor: 'pointer', color: '#1a1a2e', fontSize: 11, fontWeight: 'bold',
  },
};
