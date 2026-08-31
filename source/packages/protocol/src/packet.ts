export type Direction = 'c2s' | 's2c' | 'both';

export interface Packet<T = unknown> {
  type: string;
  id: string;
  payload: T;
}

export interface ErrorPayload {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export interface OkPayload {
  ok: true;
}

export type PacketHandler<TIn, TOut> = (packet: Packet<TIn>) => Promise<Packet<TOut>>;
