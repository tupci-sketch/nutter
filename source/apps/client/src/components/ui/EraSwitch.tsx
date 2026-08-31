import { useEraStore } from '@/stores/eraStore';
import type { AssetEra } from '@/renderer/AssetLoader';

const LABELS: Record<AssetEra, string> = {
  classic: 'Classic',
  modern: 'Modern',
};

/**
 * Switches the hotel's visual era.
 *
 * This changes artwork only. The player stays in the room they are standing in,
 * on the same world, with the same people around them — so it is presented as a
 * view control rather than anything that sounds like leaving.
 */
export function EraSwitch() {
  const { era, switching, setEra } = useEraStore();

  return (
    <div style={styles.wrap} role="group" aria-label="Hotel appearance">
      {(Object.keys(LABELS) as AssetEra[]).map((option) => {
        const active = option === era;
        return (
          <button
            key={option}
            type="button"
            onClick={() => void setEra(option)}
            disabled={switching}
            aria-pressed={active}
            style={{
              ...styles.option,
              ...(active ? styles.active : null),
              ...(switching ? styles.busy : null),
            }}
          >
            {LABELS[option]}
          </button>
        );
      })}
      <span style={styles.hint} aria-live="polite">
        {switching ? 'Switching…' : ''}
      </span>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrap: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 2,
    background: '#12142a',
    border: '1px solid #2f3767',
    borderRadius: 999,
    padding: 2,
  },
  option: {
    border: 'none',
    background: 'transparent',
    color: '#8b91b4',
    font: '600 11px/1 ui-monospace, monospace',
    letterSpacing: '0.08em',
    textTransform: 'uppercase',
    padding: '5px 12px',
    borderRadius: 999,
    cursor: 'pointer',
  },
  active: {
    background: 'linear-gradient(180deg, #f0a040, #b8792c)',
    color: '#241503',
  },
  busy: {
    cursor: 'progress',
    opacity: 0.7,
  },
  hint: {
    color: '#8b91b4',
    font: '11px ui-monospace, monospace',
    paddingRight: 6,
    minWidth: 0,
  },
};
