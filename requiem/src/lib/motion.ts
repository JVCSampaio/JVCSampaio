// Motion SSoT, mirrored in styles.css (--ease-*, --dur-*). Components never hardcode a curve or
// a duration: they use these names or the CSS variables.

export const EASE_OUT = "var(--ease-out)";
export const EASE_IN_OUT = "var(--ease-in-out)";

export const DUR = {
  press: 120,
  fast: 160,
  base: 200,
  slow: 300,
} as const;

/** Loaders wait this long before showing, then stay at least this long (anti-flicker pair). */
export const WAIT = {
  showAfter: 300,
  holdFor: 500,
} as const;
