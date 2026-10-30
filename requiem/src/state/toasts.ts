import { create } from "zustand";

export interface Toast {
  id: number;
  message: string;
  tone: "info" | "error";
  action?: { label: string; run: () => void };
}

interface ToastState {
  toasts: Toast[];
  dismiss(id: number): void;
}

export const useToasts = create<ToastState>()((set) => ({
  toasts: [],
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

let next = 1;

export function toast(message: string, tone: Toast["tone"] = "info", action?: Toast["action"]) {
  const id = next++;
  useToasts.setState((s) => ({ toasts: [...s.toasts.slice(-2), { id, message, tone, action }] }));
  setTimeout(() => useToasts.getState().dismiss(id), tone === "error" ? 7000 : 4000);
}
