/** The equalizer's ten bands, in hertz, and its presets (gains in dB). */
export const BANDS = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];

export const BAND_LABELS = ["31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"];

export interface EqPreset {
  id: string;
  label: string;
  gains: number[];
}

export const PRESETS: EqPreset[] = [
  { id: "flat", label: "Plano", gains: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0] },
  { id: "bass", label: "Mais graves", gains: [6, 5, 4, 2, 0, 0, 0, 0, 0, 0] },
  { id: "treble", label: "Mais agudos", gains: [0, 0, 0, 0, 0, 1, 2, 4, 5, 6] },
  { id: "vocal", label: "Voz em evidência", gains: [-2, -2, -1, 1, 3, 4, 4, 2, 0, -1] },
  { id: "rock", label: "Rock", gains: [5, 4, 2, -1, -2, -1, 1, 3, 4, 5] },
  { id: "electronic", label: "Eletrônica", gains: [5, 4, 1, 0, -2, 1, 0, 1, 4, 5] },
  { id: "acoustic", label: "Acústico", gains: [3, 3, 2, 1, 1, 1, 2, 2, 2, 1] },
  { id: "classical", label: "Clássica", gains: [3, 2, 1, 0, 0, 0, -1, 1, 2, 3] },
  { id: "quiet", label: "Volume baixo", gains: [5, 4, 2, 0, 0, 0, 0, 1, 3, 4] },
];

/** Headroom for a set of gains: the biggest boost, partly taken back so loud songs do not hit
 *  the limiter all the time. */
export function preampFor(gains: number[]): number {
  return -Math.round(Math.max(0, ...gains) * 0.6 * 2) / 2;
}
