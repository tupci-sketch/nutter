import { useEffect, useRef } from 'react';
import { RoomRenderer } from '@/renderer/RoomRenderer';
import { useRoomStore } from '@/stores/roomStore';
import { useA11yStore } from '@/stores/a11yStore';
import { useAuthStore } from '@/stores/authStore';
import { describeMove, describePosition, describeRoom } from '@/a11y/roomDescription';

/**
 * The room itself.
 *
 * Three ways in, because a canvas that only answers a mouse leaves out anybody
 * on a phone and anybody who cannot use one: pointer and touch for tapping,
 * dragging and pinching; the arrow keys for walking; and a live region that
 * says in words what the picture is showing.
 */

/** How far a pointer may travel and still count as a tap rather than a drag. */
const TAP_SLOP = 8;

/** How much one wheel notch or one key press changes the zoom. */
const ZOOM_STEP = 1.15;

export function RoomCanvas() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const rendererRef = useRef<RoomRenderer | null>(null);
  const users = useRoomStore((s) => s.users);
  const furni = useRoomStore((s) => s.furni);
  const room = useRoomStore((s) => s.currentRoom);
  const { move } = useRoomStore();
  const selfId = useAuthStore((s) => s.userId);
  const announce = useA11yStore((s) => s.announce);
  const announcement = useA11yStore((s) => s.announcement);

  useEffect(() => {
    if (!canvasRef.current) return;
    const canvas = canvasRef.current;
    const renderer = new RoomRenderer(canvas, canvas.clientWidth, canvas.clientHeight);
    rendererRef.current = renderer;

    const stopClicks = renderer.onClick((tx, ty) => {
      move(tx, ty);
      announce(describeMove(tx, ty, false));
    });
    const stopGestures = attachGestures(canvas, renderer);

    const handleResize = () => renderer.resize(canvas.clientWidth, canvas.clientHeight);
    window.addEventListener('resize', handleResize);
    window.addEventListener('orientationchange', handleResize);

    return () => {
      stopClicks();
      stopGestures();
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('orientationchange', handleResize);
      renderer.destroy();
      rendererRef.current = null;
    };
    // move and announce are stable store actions; listing them keeps the
    // handlers bound to the current ones rather than the first render's.
  }, [move, announce]);

  useEffect(() => {
    rendererRef.current?.updateUsers(users);
  }, [users]);

  useEffect(() => {
    rendererRef.current?.updateFurni(furni);
  }, [furni]);

  /**
   * Walking, zooming and recentring from the keyboard.
   *
   * The arrows move one tile at a time from where the player is standing, which
   * is the only way to walk without being able to see where to click.
   */
  function handleKeyDown(event: React.KeyboardEvent<HTMLCanvasElement>) {
    const renderer = rendererRef.current;
    if (!renderer) return;

    const me = selfId === null ? undefined : users.get(selfId);

    const step: Record<string, [number, number]> = {
      ArrowUp: [0, -1],
      ArrowDown: [0, 1],
      ArrowLeft: [-1, 0],
      ArrowRight: [1, 0],
    };

    if (event.key in step && me) {
      const [dx, dy] = step[event.key];
      event.preventDefault();
      move(me.x + dx, me.y + dy);
      announce(describeMove(me.x + dx, me.y + dy, false));
      return;
    }

    switch (event.key) {
      case '+':
      case '=':
        event.preventDefault();
        renderer.zoomBy(ZOOM_STEP);
        break;
      case '-':
        event.preventDefault();
        renderer.zoomBy(1 / ZOOM_STEP);
        break;
      case '0':
        event.preventDefault();
        renderer.resetCamera();
        announce('View reset.');
        break;
      // A player who cannot see the room needs a way to ask what is in it
      // rather than waiting to be told.
      case 'r':
      case 'R':
        event.preventDefault();
        announce(describeRoom({
          room,
          users: [...users.values()],
          furni: [...furni.values()],
          selfId,
        }));
        break;
      case 'p':
      case 'P':
        event.preventDefault();
        announce(describePosition(me));
        break;
      default:
        break;
    }
  }

  return (
    <>
      <canvas
        ref={canvasRef}
        tabIndex={0}
        role="application"
        aria-label={
          room
            ? `${room.name}. Arrow keys to walk, R to describe the room, ` +
              'P for your position, plus and minus to zoom.'
            : 'Room view'
        }
        style={{
          width: '100%',
          height: '100%',
          display: 'block',
          // Panning and pinching are handled here, so the browser must not also
          // scroll the page out from underneath the gesture.
          touchAction: 'none',
        }}
        onKeyDown={handleKeyDown}
      />
      <div
        aria-live="polite"
        aria-atomic="true"
        style={{
          position: 'absolute',
          width: 1,
          height: 1,
          margin: -1,
          padding: 0,
          overflow: 'hidden',
          clip: 'rect(0 0 0 0)',
          whiteSpace: 'nowrap',
          border: 0,
        }}
      >
        {announcement}
      </div>
    </>
  );
}

/**
 * Dragging to pan, pinching to zoom, and the wheel to zoom.
 *
 * Written against pointer events rather than touch events so that a finger, a
 * stylus and a mouse all take the same path through here.
 */
function attachGestures(canvas: HTMLCanvasElement, renderer: RoomRenderer): () => void {
  const active = new Map<number, { x: number; y: number }>();
  let lastPinchDistance = 0;
  let travelled = 0;

  function onPointerDown(event: PointerEvent) {
    active.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (active.size === 1) {
      travelled = 0;
      renderer.setGestureMoved(false);
    }
    canvas.setPointerCapture(event.pointerId);
  }

  function onPointerMove(event: PointerEvent) {
    const previous = active.get(event.pointerId);
    if (!previous) return;

    const dx = event.clientX - previous.x;
    const dy = event.clientY - previous.y;
    active.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (active.size === 1) {
      travelled += Math.abs(dx) + Math.abs(dy);
      // Past a few pixels this stopped being a tap and became a drag, and the
      // avatar must not walk when the gesture ends.
      if (travelled > TAP_SLOP) {
        renderer.setGestureMoved(true);
        renderer.panBy(dx, dy);
      }
      return;
    }

    if (active.size === 2) {
      renderer.setGestureMoved(true);
      const [a, b] = [...active.values()];
      const distance = Math.hypot(a.x - b.x, a.y - b.y);

      if (lastPinchDistance > 0 && distance > 0) {
        renderer.zoomBy(distance / lastPinchDistance);
      }
      lastPinchDistance = distance;
    }
  }

  function onPointerUp(event: PointerEvent) {
    active.delete(event.pointerId);
    if (active.size < 2) lastPinchDistance = 0;
    if (canvas.hasPointerCapture(event.pointerId)) {
      canvas.releasePointerCapture(event.pointerId);
    }
  }

  function onWheel(event: WheelEvent) {
    event.preventDefault();
    renderer.zoomBy(event.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP);
  }

  canvas.addEventListener('pointerdown', onPointerDown);
  canvas.addEventListener('pointermove', onPointerMove);
  canvas.addEventListener('pointerup', onPointerUp);
  canvas.addEventListener('pointercancel', onPointerUp);
  canvas.addEventListener('wheel', onWheel, { passive: false });

  return () => {
    canvas.removeEventListener('pointerdown', onPointerDown);
    canvas.removeEventListener('pointermove', onPointerMove);
    canvas.removeEventListener('pointerup', onPointerUp);
    canvas.removeEventListener('pointercancel', onPointerUp);
    canvas.removeEventListener('wheel', onWheel);
  };
}
