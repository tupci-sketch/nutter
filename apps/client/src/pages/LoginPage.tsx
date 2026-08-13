import { useState } from 'react';
import { useAuthStore } from '@/stores/authStore';

export function LoginPage() {
  const [ticket, setTicket] = useState('');
  const { login } = useAuthStore();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (ticket.trim()) login(ticket.trim());
  }

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <div style={styles.logo}>🌰 Habnut</div>
        <div style={styles.tagline}>Enter your session ticket to connect.</div>
        <form onSubmit={handleSubmit} style={styles.form}>
          <input
            style={styles.input}
            value={ticket}
            onChange={e => setTicket(e.target.value)}
            placeholder="Session ticket"
            autoFocus
          />
          <button style={styles.btn} type="submit">Connect</button>
        </form>
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  page: {
    width: '100%', height: '100%', display: 'flex',
    alignItems: 'center', justifyContent: 'center',
    background: 'linear-gradient(135deg, #1a1a2e 0%, #0f3460 100%)',
  },
  card: {
    background: 'rgba(10,10,20,0.95)', border: '1px solid #444',
    borderRadius: 12, padding: 40, width: 360, textAlign: 'center',
    boxShadow: '0 20px 60px rgba(0,0,0,0.5)',
  },
  logo: { fontSize: 36, marginBottom: 8, color: '#f0a040', fontFamily: 'monospace',
    fontWeight: 'bold' },
  tagline: { color: '#888', fontSize: 13, marginBottom: 24, fontFamily: 'monospace' },
  form: { display: 'flex', flexDirection: 'column', gap: 12 },
  input: {
    background: '#2a2a3e', border: '1px solid #555', borderRadius: 6,
    padding: '10px 12px', color: '#e8e0d0', fontSize: 14, fontFamily: 'monospace',
    outline: 'none',
  },
  btn: {
    background: '#f0a040', border: 'none', borderRadius: 6, padding: '10px',
    cursor: 'pointer', color: '#1a1a2e', fontWeight: 'bold', fontSize: 15,
    fontFamily: 'monospace',
  },
};
