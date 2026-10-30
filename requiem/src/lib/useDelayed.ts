import { useEffect, useState } from "react";

/** True once `on` has held for `ms`. Loaders use it so a quick answer never flashes a skeleton. */
export function useDelayed(on: boolean, ms: number): boolean {
  const [shown, setShown] = useState(false);
  useEffect(() => {
    if (!on) {
      setShown(false);
      return;
    }
    const timer = window.setTimeout(() => setShown(true), ms);
    return () => window.clearTimeout(timer);
  }, [on, ms]);
  return shown;
}
