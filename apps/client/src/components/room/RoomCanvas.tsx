import { useEffect, useRef } from 'react';
import { RoomRenderer } from '@/renderer/RoomRenderer';
import { useRoomStore } from '@/stores/roomStore';

export function RoomCanvas() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const rendererRef = useRef<RoomRenderer | null>(null);
  const users = useRoomStore((s) => s.users);
  const furni  = useRoomStore((s) => s.furni);
  const { move } = useRoomStore();

  useEffect(() => {
    if (!canvasRef.current) return;
    const canvas = canvasRef.current;
    const r = new RoomRenderer(canvas, canvas.clientWidth, canvas.clientHeight);
    rendererRef.current = r;

    const cleanup = r.onClick((tx, ty) => move(tx, ty));

    const handleResize = () => {
      r.resize(canvas.clientWidth, canvas.clientHeight);
    };
    window.addEventListener('resize', handleResize);

    return () => {
      cleanup();
      window.removeEventListener('resize', handleResize);
      r.destroy();
      rendererRef.current = null;
    };
    // move is a stable store action; listing it keeps the click handler bound
    // to the current one rather than closing over the first render's copy.
  }, [move]);

  useEffect(() => {
    rendererRef.current?.updateUsers(users);
  }, [users]);

  useEffect(() => {
    rendererRef.current?.updateFurni(furni);
  }, [furni]);

  return (
    <canvas
      ref={canvasRef}
      style={{ width: '100%', height: '100%', display: 'block' }}
    />
  );
}
