// Browser preview only: a WAV of the right length holding a faint, low hum (about -42 dBFS),
// so the player can be exercised without the engine's media server and the sound chain's check
// hears something. 4000 Hz, 8 bits: small, and above the lowest rate browsers decode.

const cache = new Map<number, string>();

export function previewWav(seconds: number): string {
  const s = Math.max(1, Math.round(seconds));
  const hit = cache.get(s);
  if (hit) return hit;
  const rate = 4000;
  const samples = rate * s;
  const buffer = new ArrayBuffer(44 + samples);
  const view = new DataView(buffer);
  const text = (offset: number, value: string) => [...value].forEach((c, i) => view.setUint8(offset + i, c.charCodeAt(0)));
  text(0, "RIFF");
  view.setUint32(4, 36 + samples, true);
  text(8, "WAVE");
  text(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, rate, true);
  view.setUint32(28, rate, true);
  view.setUint16(32, 1, true);
  view.setUint16(34, 8, true);
  text(36, "data");
  view.setUint32(40, samples, true);
  const pcm = new Uint8Array(buffer, 44, samples);
  for (let i = 0; i < samples; i++) pcm[i] = 128 + (Math.floor(i / 20) % 2 ? 1 : -1);
  const url = URL.createObjectURL(new Blob([buffer], { type: "audio/wav" }));
  cache.set(s, url);
  return url;
}
