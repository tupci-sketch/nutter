import { Envelope } from '@/protocol/packets';

type Handler = (payload: unknown) => void;

const INITIAL_DELAY_MS = 1_000;
const MAX_DELAY_MS = 30_000;
const PING_INTERVAL_MS = 30_000;

export class WsClient {
  private ws: WebSocket | null = null;
  private readonly handlers = new Map<string, Set<Handler>>();
  private readonly queue: string[] = [];
  private reconnectDelay = INITIAL_DELAY_MS;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private closed = false;

  constructor(private readonly url: string) {}

  connect(): void {
    if (this.closed) return;
    try {
      this.ws = new WebSocket(this.url);
      this.ws.onopen = this.onOpen;
      this.ws.onmessage = this.onMessage;
      this.ws.onclose = this.onClose;
      this.ws.onerror = this.onError;
    } catch {
      this.scheduleReconnect();
    }
  }

  private onOpen = (): void => {
    this.reconnectDelay = INITIAL_DELAY_MS;
    this.queue.splice(0).forEach(msg => this.ws!.send(msg));
    this.pingTimer = setInterval(() => {
      this.send('system.ping', { ts: Date.now() });
    }, PING_INTERVAL_MS);
  };

  private onMessage = (ev: MessageEvent): void => {
    try {
      const env = JSON.parse(ev.data as string) as Envelope;
      const set = this.handlers.get(env.type);
      set?.forEach(h => h(env.payload));
    } catch {
      // malformed packet — ignore
    }
  };

  private onClose = (): void => {
    this.clearPing();
    if (!this.closed) this.scheduleReconnect();
  };

  private onError = (): void => {
    this.ws?.close();
  };

  private scheduleReconnect(): void {
    this.reconnectTimer = setTimeout(() => {
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, MAX_DELAY_MS);
      this.connect();
    }, this.reconnectDelay);
  }

  send(type: string, payload: unknown = {}): void {
    const msg = JSON.stringify({ type, payload });
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(msg);
    } else {
      this.queue.push(msg);
    }
  }

  on(type: string, handler: Handler): () => void {
    if (!this.handlers.has(type)) this.handlers.set(type, new Set());
    this.handlers.get(type)!.add(handler);
    return () => this.handlers.get(type)?.delete(handler);
  }

  off(type: string, handler: Handler): void {
    this.handlers.get(type)?.delete(handler);
  }

  close(): void {
    this.closed = true;
    this.clearPing();
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.ws?.close();
  }

  get connected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  private clearPing(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }
}

let _client: WsClient | null = null;

export function getWsClient(): WsClient {
  if (!_client) {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${proto}//${location.host}/ws`;
    _client = new WsClient(url);
  }
  return _client;
}
