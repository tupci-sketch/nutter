import { useA11yStore, type A11ySettings } from '@/stores/a11yStore';

/**
 * The accessibility settings, as a list of plain switches.
 *
 * Written in terms of what changes for the player rather than what the setting
 * is called internally, because somebody turning these on is trying to make the
 * hotel usable, not to configure a renderer.
 */

const OPTIONS: Array<{ key: keyof A11ySettings; label: string; detail: string }> = [
  {
    key: 'reducedMotion',
    label: 'Less movement',
    detail: 'Stops walk cycles and other animation that plays on its own.',
  },
  {
    key: 'neverColourAlone',
    label: 'Never use colour alone',
    detail: 'Adds a word or a shape anywhere a colour is the only thing saying something.',
  },
  {
    key: 'announceRoom',
    label: 'Describe rooms aloud',
    detail: 'Announces who is in the room and what happens in it. Press R in a room to hear it again.',
  },
  {
    key: 'largeText',
    label: 'Larger text',
    detail: 'Makes the panels and their controls bigger.',
  },
];

export function AccessibilityPanel() {
  const store = useA11yStore();

  return (
    <div style={styles.list}>
      {OPTIONS.map(({ key, label, detail }) => (
        <label key={key} style={styles.row}>
          <input
            type="checkbox"
            checked={store[key]}
            onChange={(e) => store.set(key, e.target.checked)}
            style={styles.checkbox}
          />
          <span>
            <span style={styles.label}>{label}</span>
            <span style={styles.detail}>{detail}</span>
          </span>
        </label>
      ))}

      <p style={styles.note}>
        In a room: arrow keys walk, R describes the room, P says where you are standing,
        plus and minus zoom, and 0 puts the view back.
      </p>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  list: { display: 'flex', flexDirection: 'column', gap: 12 },
  row: { display: 'flex', gap: 10, alignItems: 'flex-start', cursor: 'pointer' },
  // Big enough to hit with a thumb.
  checkbox: { width: 20, height: 20, flex: 'none', marginTop: 2 },
  label: { display: 'block', color: '#e8e0d0', fontWeight: 'bold' },
  detail: { display: 'block', color: '#8b91b4', fontSize: 12, marginTop: 2 },
  note: {
    marginTop: 8,
    paddingTop: 10,
    borderTop: '1px solid #333',
    color: '#8b91b4',
    fontSize: 12,
    lineHeight: 1.5,
  },
};
