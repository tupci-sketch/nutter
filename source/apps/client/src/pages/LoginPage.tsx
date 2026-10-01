import { useState } from 'react';
import { useAuthStore } from '@/stores/authStore';

/**
 * The way in, when the website has not already opened the door.
 *
 * Almost nobody should see this: a player signs in on the site and arrives
 * with a ticket in the address, which is spent before anything is drawn. What
 * is left is the two cases where there is nothing to spend — somebody running
 * the client on its own, and somebody whose ticket had already expired by the
 * time they got here.
 */
export function LoginPage() {
  const [ticket, setTicket] = useState('');
  const login = useAuthStore(s => s.login);
  const phase = useAuthStore(s => s.phase);
  const error = useAuthStore(s => s.error);
  const handedOver = useAuthStore(s => s.handedOver);

  const connecting = phase === 'connecting';

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (ticket.trim()) login(ticket.trim());
  }

  // Arriving from the site with a ticket that is still being checked: there is
  // nothing to ask for yet, so asking would only be confusing.
  if (handedOver && connecting) {
    return (
      <div style={styles.page}>
        <div style={styles.card}>
          <div style={styles.logo}>🌰 Habnut</div>
          <div style={styles.tagline} role="status" aria-live="polite">
            Opening the door…
          </div>
        </div>
      </div>
    );
  }

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <div style={styles.logo}>🌰 Habnut</div>
        <div style={styles.tagline}>
          {handedOver
            ? 'That did not work. Sign in on the website again to get back in.'
            : 'Sign in on the website to come in, or paste a session ticket.'}
        </div>

        {error && (
          <div style={styles.error} role="alert">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={styles.form}>
          <label htmlFor="ticket" style={styles.srOnly}>Session ticket</label>
          <input
            id="ticket"
            style={styles.input}
            value={ticket}
            onChange={e => setTicket(e.target.value)}
            placeholder="Session ticket"
            autoComplete="off"
            autoFocus
          />
          <button style={styles.btn} type="submit" disabled={connecting}>
            {connecting ? 'Connecting…' : 'Connect'}
          </button>
        </form>

        <a href="/" style={styles.back}>Back to the website</a>
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  page: {
    width: '100%', height: '100%', display: 'flex',
    alignItems: 'center', justifyContent: 'center', padding: 16,
    background: 'linear-gradient(135deg, #1a1a2e 0%, #0f3460 100%)',
  },
  card: {
    background: 'rgba(10,10,20,0.95)', border: '1px solid #444',
    borderRadius: 12, padding: 40, width: '100%', maxWidth: 360,
    textAlign: 'center', boxShadow: '0 20px 60px rgba(0,0,0,0.5)',
  },
  logo: {
    fontSize: 36, marginBottom: 8, color: '#f0a040',
    fontFamily: 'monospace', fontWeight: 'bold',
  },
  tagline: { color: '#888', fontSize: 13, marginBottom: 24, fontFamily: 'monospace' },
  error: {
    background: 'rgba(180,40,40,0.2)', border: '1px solid #a33',
    borderRadius: 6, padding: '8px 10px', marginBottom: 16,
    color: '#f0a0a0', fontSize: 12, textAlign: 'left',
  },
  form: { display: 'flex', flexDirection: 'column', gap: 12 },
  input: {
    background: '#2a2a3e', border: '1px solid #555', borderRadius: 6,
    padding: '10px 12px', color: '#e8e0d0', fontSize: 14,
    fontFamily: 'monospace', outline: 'none',
  },
  btn: {
    background: '#f0a040', border: 'none', borderRadius: 6, padding: '12px',
    cursor: 'pointer', color: '#1a1a2e', fontWeight: 'bold', fontSize: 15,
    fontFamily: 'monospace', minHeight: 44,
  },
  back: {
    display: 'inline-block', marginTop: 20, color: '#888',
    fontSize: 12, textDecoration: 'none',
  },
  srOnly: {
    position: 'absolute', width: 1, height: 1, padding: 0, margin: -1,
    overflow: 'hidden', clip: 'rect(0, 0, 0, 0)', whiteSpace: 'nowrap', border: 0,
  },
};
