// Abstract night scenes standing in for YouTube thumbnails in the browser preview, so the
// screenshots show the interface with pictures. The real app shows the real thumbnails.

const palettes: Array<[string, string, string]> = [
  ["#2a1240", "#0b0612", "#e9dcc5"],
  ["#4a0f17", "#0d0507", "#f0c9a0"],
  ["#0f2a33", "#04090c", "#cfe3ea"],
  ["#5a2a08", "#120703", "#ffd59a"],
  ["#1d1d2e", "#07070d", "#d9d4ff"],
  ["#2f2a1c", "#0b0a06", "#efe6cc"],
];

function random(seed: string) {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i++) h = Math.imul(h ^ seed.charCodeAt(i), 16777619);
  return () => {
    h = Math.imul(h ^ (h >>> 15), 2246822507);
    h = Math.imul(h ^ (h >>> 13), 3266489909);
    h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  };
}

export function mockArt(seed: string, video = false): string {
  if (typeof document === "undefined") return "";
  const rand = random(seed);
  const w = video ? 480 : 320;
  const h = video ? 270 : 320;
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext("2d");
  if (!ctx) return "";
  const [top, bottom, light] = palettes[Math.floor(rand() * palettes.length)];

  const sky = ctx.createLinearGradient(0, 0, w * rand(), h);
  sky.addColorStop(0, top);
  sky.addColorStop(1, bottom);
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, w, h);

  for (let i = 0; i < 40; i++) {
    ctx.fillStyle = `rgba(255,255,255,${0.15 + rand() * 0.5})`;
    ctx.fillRect(rand() * w, rand() * h * 0.6, 1.2, 1.2);
  }

  const mx = w * (0.2 + rand() * 0.6);
  const my = h * (0.18 + rand() * 0.25);
  const r = Math.min(w, h) * (0.1 + rand() * 0.12);
  const glow = ctx.createRadialGradient(mx, my, r * 0.4, mx, my, r * 3.2);
  glow.addColorStop(0, `${light}55`);
  glow.addColorStop(1, `${light}00`);
  ctx.fillStyle = glow;
  ctx.fillRect(0, 0, w, h);
  ctx.fillStyle = light;
  ctx.beginPath();
  ctx.arc(mx, my, r, 0, Math.PI * 2);
  ctx.fill();
  if (rand() > 0.45) {
    ctx.fillStyle = top;
    ctx.beginPath();
    ctx.arc(mx + r * 0.45, my - r * 0.2, r * 0.92, 0, Math.PI * 2);
    ctx.fill();
  }

  for (let layer = 0; layer < 2; layer++) {
    const base = h * (0.62 + layer * 0.14);
    ctx.fillStyle = layer === 0 ? `${bottom}cc` : "#030204";
    ctx.beginPath();
    ctx.moveTo(0, h);
    let x = 0;
    ctx.lineTo(0, base);
    while (x < w) {
      const step = 10 + rand() * 40;
      const spike = rand() > 0.7 ? base - 20 - rand() * h * 0.22 : base - rand() * 18;
      ctx.lineTo(x + step / 2, spike);
      x += step;
      ctx.lineTo(x, base - rand() * 8);
    }
    ctx.lineTo(w, h);
    ctx.closePath();
    ctx.fill();
  }

  const image = ctx.getImageData(0, 0, w, h);
  for (let i = 0; i < image.data.length; i += 4) {
    const n = (rand() - 0.5) * 18;
    image.data[i] += n;
    image.data[i + 1] += n;
    image.data[i + 2] += n;
  }
  ctx.putImageData(image, 0, 0);
  return canvas.toDataURL("image/jpeg", 0.85);
}
