import { useVirtualizer } from "@tanstack/react-virtual";
import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { useMainScroll } from "@/lib/useMainScroll";

/**
 * A grid of tiles that only renders the rows on screen, so thousands of albums, artists or
 * videos scroll like a few dozen. Columns fill the width like
 * `repeat(auto-fill, minmax(minWidth, 1fr))`; rows are measured once drawn.
 */
export function VirtualGrid<T>({
  items,
  minWidth,
  rowGap,
  getKey,
  estimateHeight,
  children,
}: {
  items: T[];
  minWidth: number;
  rowGap: number;
  getKey: (item: T) => string;
  /** A row's height for a given column width, before it is measured. */
  estimateHeight: (columnWidth: number) => number;
  children: (item: T, index: number) => ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const { scroller, margin } = useMainScroll(ref);
  const [width, setWidth] = useState(0);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    setWidth(el.clientWidth);
    const ro = new ResizeObserver(() => setWidth(el.clientWidth));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const columns = Math.max(1, Math.floor(width / minWidth));
  const rows = Math.ceil(items.length / columns);
  const virtualizer = useVirtualizer({
    count: rows,
    getScrollElement: () => scroller,
    estimateSize: () => estimateHeight(width / columns) + rowGap,
    overscan: 3,
    scrollMargin: margin,
  });

  // A new column count changes every row's height.
  useLayoutEffect(() => virtualizer.measure(), [columns, virtualizer]);

  return (
    <div ref={ref} className="relative" style={{ height: virtualizer.getTotalSize() }}>
      {virtualizer.getVirtualItems().map((row) => {
        const first = row.index * columns;
        return (
          <div
            key={row.key}
            data-index={row.index}
            ref={virtualizer.measureElement}
            className="absolute left-0 right-0 top-0 grid"
            style={{
              gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
              paddingBottom: rowGap,
              transform: `translateY(${row.start - virtualizer.options.scrollMargin}px)`,
            }}
          >
            {items.slice(first, first + columns).map((item, i) => (
              <div key={getKey(item)} className="min-w-0 [&>*]:w-full">
                {children(item, first + i)}
              </div>
            ))}
          </div>
        );
      })}
    </div>
  );
}
