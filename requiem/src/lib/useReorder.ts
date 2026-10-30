import { useRef, useState, type KeyboardEvent, type PointerEvent as ReactPointerEvent } from "react";

export interface Reordering {
  /** The row being dragged and where it would land. */
  from: number;
  to: number;
}

const EDGE = 56;

/**
 * Drag to reorder a list with pointer events (HTML5 drag and drop draws a ghost image and
 * behaves differently in each web view). The row's handle starts the drag, the list scrolls by
 * itself near its edges, Escape cancels, and Alt with the up or down arrow moves a focused row
 * one place. `indexAt` turns a pointer height into the row under it, so a virtualized list that
 * only draws part of its rows still finds any of them.
 */
export function useReorder(options: {
  count: number;
  indexAt: (clientY: number) => number;
  scroller: () => HTMLElement | null;
  onMove: (from: number, to: number) => void;
}) {
  const [drag, setDrag] = useState<Reordering | null>(null);
  const active = useRef(false);
  // A download landing mid-drag re-renders the list: the drag reads the newest list.
  const latest = useRef(options);
  latest.current = options;
  const clamp = (i: number) => Math.max(0, Math.min(latest.current.count - 1, i));

  const handle = (index: number) => ({
    onPointerDown(e: ReactPointerEvent<HTMLElement>) {
      if (e.button !== 0 || active.current) return;
      e.preventDefault();
      e.stopPropagation();
      const target = e.currentTarget;
      target.setPointerCapture(e.pointerId);
      active.current = true;
      let y = e.clientY;
      let to = index;
      let frame = 0;
      setDrag({ from: index, to });

      const update = () => {
        const next = clamp(latest.current.indexAt(y));
        if (next !== to) {
          to = next;
          setDrag({ from: index, to });
        }
      };
      // Near the top or bottom edge, the list scrolls faster the closer the pointer gets.
      const tick = () => {
        const el = latest.current.scroller();
        if (el) {
          const r = el.getBoundingClientRect();
          const push = y < r.top + EDGE ? y - (r.top + EDGE) : y > r.bottom - EDGE ? y - (r.bottom - EDGE) : 0;
          if (push) {
            el.scrollTop += Math.max(-24, Math.min(24, push / 2));
            update();
          }
        }
        frame = requestAnimationFrame(tick);
      };
      const finish = (commit: boolean) => {
        cancelAnimationFrame(frame);
        target.removeEventListener("pointermove", move);
        target.removeEventListener("pointerup", up);
        target.removeEventListener("pointercancel", cancel);
        window.removeEventListener("keydown", escape, true);
        active.current = false;
        setDrag(null);
        if (commit && to !== index) latest.current.onMove(index, to);
      };
      const move = (ev: PointerEvent) => {
        y = ev.clientY;
        update();
      };
      const up = () => finish(true);
      const cancel = () => finish(false);
      const escape = (ev: globalThis.KeyboardEvent) => {
        if (ev.key !== "Escape") return;
        ev.preventDefault();
        ev.stopPropagation();
        finish(false);
      };
      target.addEventListener("pointermove", move);
      target.addEventListener("pointerup", up);
      target.addEventListener("pointercancel", cancel);
      window.addEventListener("keydown", escape, true);
      frame = requestAnimationFrame(tick);
    },
  });

  /** Alt with an arrow moves the focused row; true when the key was used. */
  const keyMove = (index: number, e: KeyboardEvent) => {
    if (!e.altKey || (e.key !== "ArrowUp" && e.key !== "ArrowDown")) return false;
    e.preventDefault();
    const to = clamp(index + (e.key === "ArrowUp" ? -1 : 1));
    if (to !== index) latest.current.onMove(index, to);
    return true;
  };

  return { drag, handle, keyMove };
}

/** Where the drop line goes for a row: above it when the dragged row moves up to it, below it
 *  when it moves down. */
export function dropSide(drag: Reordering | null, index: number): "above" | "below" | null {
  if (!drag || drag.to !== index || drag.to === drag.from) return null;
  return drag.to < drag.from ? "above" : "below";
}

/** The list with `id` moved next to `target`: before it when moving up, after it when moving
 *  down. Ids not shown (songs still downloading in a playlist) keep their places. */
export function moveId(ids: string[], id: string, target: string, after: boolean): string[] {
  if (id === target) return ids;
  const rest = ids.filter((x) => x !== id);
  const at = rest.indexOf(target);
  if (at < 0) return ids;
  rest.splice(after ? at + 1 : at, 0, id);
  return rest;
}
