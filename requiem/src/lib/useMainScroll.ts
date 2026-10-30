import { useEffect, useState, type RefObject } from "react";

/** The app's scrolling area (#main-scroll) and how far below its top `ref` starts, for lists
 *  virtualized against the page scroll. Anything above the list growing or shrinking (a banner,
 *  a hero) moves it, so the distance is measured again whenever the content resizes. */
export function useMainScroll(ref: RefObject<HTMLElement | null>) {
  const [scroller, setScroller] = useState<HTMLElement | null>(null);
  const [margin, setMargin] = useState(0);

  useEffect(() => {
    const main = document.getElementById("main-scroll");
    setScroller(main);
    const measure = () => {
      if (!main || !ref.current) return;
      setMargin(ref.current.getBoundingClientRect().top - main.getBoundingClientRect().top + main.scrollTop);
    };
    measure();
    const ro = new ResizeObserver(measure);
    const content = document.getElementById("main-content");
    if (content) ro.observe(content);
    return () => ro.disconnect();
  }, [ref]);

  return { scroller, margin };
}
