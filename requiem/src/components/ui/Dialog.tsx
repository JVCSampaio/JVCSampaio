import * as DialogPrimitive from "@radix-ui/react-dialog";
import type { ReactNode } from "react";

export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        {/* EXCEPTION to noise-scan/blur-on-card: the blur sits on a dialog overlay, over moving content. */}
        <DialogPrimitive.Overlay className="dialog-overlay fixed inset-0 z-40 bg-background/70 backdrop-blur-sm" />
        <DialogPrimitive.Content className="dialog-content fixed left-1/2 top-1/2 z-50 w-[min(440px,calc(100vw-32px))] -translate-x-1/2 -translate-y-1/2 rounded-surface border border-border bg-card p-6 shadow-2xl shadow-black/60 outline-none">
          <DialogPrimitive.Title className="text-lg font-bold text-foreground">{title}</DialogPrimitive.Title>
          {description ? (
            <DialogPrimitive.Description className="mt-1 text-sm text-muted-foreground">{description}</DialogPrimitive.Description>
          ) : (
            <DialogPrimitive.Description className="sr-only">{title}</DialogPrimitive.Description>
          )}
          <div className="mt-4">{children}</div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
