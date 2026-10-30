export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(" ");
}

/** 3:07, 1:02:45 */
export function duration(seconds: number | null | undefined): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) return "";
  const s = Math.floor(seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const ss = String(sec).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${ss}` : `${m}:${ss}`;
}

/** 48 min, 2 h 14 min */
export function totalDuration(seconds: number): string {
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return m ? `${h} h ${m} min` : `${h} h`;
}

export function count(n: number, one: string, many: string): string {
  return `${n.toLocaleString("pt-BR")} ${n === 1 ? one : many}`;
}

/** agora, há 4 min, há 3 h, ontem, há 5 dias, 12 de mar. */
export function ago(ms: number | null | undefined, now = Date.now()): string {
  if (!ms) return "";
  const diff = Math.max(0, now - ms);
  const min = Math.floor(diff / 60000);
  if (min < 1) return "agora";
  if (min < 60) return `há ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `há ${h} h`;
  const days = Math.floor(h / 24);
  if (days === 1) return "ontem";
  if (days < 30) return `há ${days} dias`;
  return new Date(ms).toLocaleDateString("pt-BR", { day: "numeric", month: "short" });
}

/** em 12 min, em 2 h */
export function inTime(ms: number | null | undefined, now = Date.now()): string {
  if (!ms) return "";
  const min = Math.max(0, Math.round((ms - now) / 60000));
  if (min < 1) return "em instantes";
  if (min < 60) return `em ${min} min`;
  return `em ${Math.round(min / 60)} h`;
}

export function bytes(n: number | null | undefined): string {
  if (!n) return "";
  if (n >= 1 << 30) return `${(n / (1 << 30)).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} GB`;
  if (n >= 1 << 20) return `${Math.round(n / (1 << 20))} MB`;
  return `${Math.max(1, Math.round(n / 1024))} KB`;
}

export function speed(bytesPerSecond: number | null | undefined): string {
  if (!bytesPerSecond) return "";
  return `${bytes(bytesPerSecond)}/s`;
}

export function percent(fraction: number | null | undefined): string {
  if (fraction == null) return "";
  return `${Math.floor(fraction * 100)}%`;
}

export function greeting(date = new Date()): string {
  const h = date.getHours();
  if (h >= 5 && h < 12) return "Bom dia";
  if (h >= 12 && h < 18) return "Boa tarde";
  return "Boa noite";
}

/** Accent- and case-insensitive key for filtering. */
export function key(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{Mn}+/gu, "")
    .toLowerCase();
}

export function matches(haystack: string, query: string): boolean {
  const words = key(query).split(/\s+/).filter(Boolean);
  if (!words.length) return true;
  const h = key(haystack);
  return words.every((w) => h.includes(w));
}
