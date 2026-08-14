import { useState, useRef, useEffect } from 'react';
import { useRoomStore } from '@/stores/roomStore';
import { useUiStore } from '@/stores/uiStore';

export function ChatBox() {
  const [input, setInput] = useState('');
  const chatLog = useRoomStore((s) => s.chat);
  const { sendChat } = useRoomStore();
  const { setChatInputFocused } = useUiStore();
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
      <form onSubmit={handleSubmit} style={styles.form}>
        <input
          style={styles.input}
          value={input}
          onChange={e => setInput(e.target.value)}
          onFocus={() => setChatInputFocused(true)}
          onBlur={() => setChatInputFocused(false)}
          placeholder="Say something..."
          maxLength={256}
        />
        <button style={styles.btn} type="submit">→</button>
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
