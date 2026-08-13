import { describe, it, expect, vi, beforeEach } from 'vitest';
import { WsClient } from '../ws/WsClient';

class MockWebSocket {
  static instances: MockWebSocket[] = [];
  readyState = 1;
  onopen: (() => void) | null = null;
  onmessage: ((ev: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  sent: string[] = [];

  constructor() {
    MockWebSocket.instances.push(this);
    setTimeout(() => this.onopen?.(), 0);
  }

  send(data: string) { this.sent.push(data); }
  close() { this.readyState = 3; this.onclose?.(); }
}

beforeEach(() => {
  MockWebSocket.instances = [];
  (global as unknown as Record<string, unknown>)['WebSocket'] = MockWebSocket;
});

describe('WsClient', () => {
  it('registers a handler and calls it on matching message', async () => {
    const client = new WsClient('ws://test');
    client.connect();

    await new Promise(r => setTimeout(r, 10));
    const ws = MockWebSocket.instances[0];
    expect(ws).toBeDefined();

    const handler = vi.fn();
    client.on('test.event', handler);
    ws.onmessage!({ data: JSON.stringify({ type: 'test.event', payload: { x: 1 } }) });

    expect(handler).toHaveBeenCalledWith({ x: 1 });
  });

  it('queues messages sent before connection opens', () => {
    const client = new WsClient('ws://test');
    client.connect();
    client.send('test.msg', { hello: true });

    const ws = MockWebSocket.instances[0];
    ws.readyState = 1;
    ws.onopen!();

    expect(ws.sent.some(s => s.includes('test.msg'))).toBe(true);
  });

  it('off() removes the handler', async () => {
    const client = new WsClient('ws://test');
    client.connect();
    await new Promise(r => setTimeout(r, 10));
    const ws = MockWebSocket.instances[0];

    const handler = vi.fn();
    client.on('evt', handler);
    client.off('evt', handler);
    ws.onmessage!({ data: JSON.stringify({ type: 'evt', payload: {} }) });
    expect(handler).not.toHaveBeenCalled();
  });
});
