import { useState, useRef, useEffect } from 'react';
import { useRoomStore } from '@/stores/roomStore';
import { useUiStore } from '@/stores/uiStore';
import { describeMute, describeRemaining, useMuteStore } from '@/stores/muteStore';

export function ChatBox() {
  const [input, setInput] = useState('');
  const [help, setHelp] = useState('');
  const chatLog = useRoomStore((s) => s.chat);
  const { sendChat } = useRoomStore();
  const { setChatInputFocused } = useUiStore();
  const mute = useMuteStore();
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatLog]);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const msg = input.trim();
    if (!msg) return;
    sendChat(msg);
    setInput('');
  }

  function handleHelp(e: React.FormEvent) {
    e.preventDefault();
    mute.askForHelp(help.trim());
    setHelp('');
  }

  return (
    <div style={styles.container}>
      <div style={styles.log}>
        {chatLog.map((entry, i) => (
          <div key={i} style={styles.line}>
            <span style={styles.name}>{entry.username}: </span>
            <span>{entry.message}</span>
          </div>
        ))}
        <div ref={endRef} />
      </div>
      {mute.muted && (
        <div style={styles.muteBanner} role="status" aria-live="polite">
          <div>{describeMute(mute)}</div>
          {mute.expiresAt && (
            <div style={styles.muteDetail}>
              It lifts on its own in {describeRemaining(mute.expiresAt)} if nobody has
              looked by then.
            </div>
          )}

          {mute.automatic && mute.canAskForHelp && (
            <form onSubmit={handleHelp} style={styles.helpForm}>
              <input
                style={styles.input}
                value={help}
                onChange={e => setHelp(e.target.value)}
                onFocus={() => setChatInputFocused(true)}
                onBlur={() => setChatInputFocused(false)}
                placeholder="Tell a staff member what happened"
                maxLength={512}
              />
              <button style={styles.btn} type="submit">Ask</button>
            </form>
          )}

          {mute.notice && (
            <div style={styles.muteDetail}>
              {mute.notice}{' '}
              <button style={styles.linkBtn} type="button" onClick={mute.dismissNotice}>
                Dismiss
              </button>
            </div>
          )}
        </div>
      )}

      <form onSubmit={handleSubmit} style={styles.form}>
        <input
          style={styles.input}
          value={input}
          onChange={e => setInput(e.target.value)}
          onFocus={() => setChatInputFocused(true)}
          onBlur={() => setChatInputFocused(false)}
          placeholder={mute.muted ? 'You are muted' : 'Say something...'}
          maxLength={256}
          disabled={mute.muted}
        />
        <button style={styles.btn} type="submit" disabled={mute.muted}>→</button>
      </form>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    position: 'absolute',
    bottom: 60,
    left: 8,
    width: 360,
    background: 'rgba(10,10,20,0.85)',
    borderRadius: 6,
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
    maxHeight: 200,
  },
  log: {
    flex: 1,
    overflowY: 'auto',
    padding: '6px 8px',
    fontSize: 13,
    color: '#e8e0d0',
    fontFamily: 'monospace',
    minHeight: 80,
    maxHeight: 150,
  },
  line: { marginBottom: 2 },
  name: { color: '#f0a040', fontWeight: 'bold' },
  form: { display: 'flex', borderTop: '1px solid #333' },
  muteBanner: {
    padding: '8px',
    borderTop: '1px solid #e94560',
    background: 'rgba(233,69,96,0.14)',
    color: '#ffc9d2',
    fontSize: 12,
    lineHeight: 1.45,
  },
  muteDetail: { marginTop: 4, color: '#d8b9c0' },
  helpForm: { display: 'flex', marginTop: 6, background: 'rgba(0,0,0,0.3)', borderRadius: 4 },
  linkBtn: {
    background: 'none',
    border: 'none',
    color: '#f0a040',
    cursor: 'pointer',
    padding: 0,
    font: 'inherit',
    textDecoration: 'underline',
  },
  input: {
    flex: 1,
    background: 'transparent',
    border: 'none',
    padding: '6px 8px',
    color: '#fff',
    fontFamily: 'monospace',
    fontSize: 13,
    outline: 'none',
  },
  btn: {
    background: '#f0a040',
    border: 'none',
    padding: '6px 10px',
    cursor: 'pointer',
    color: '#1a1a2e',
    fontWeight: 'bold',
    fontSize: 16,
  },
};
