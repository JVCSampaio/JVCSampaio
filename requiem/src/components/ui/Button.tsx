import { ArrowLeft, ArrowRight } from "@phosphor-icons/react";
import * as TooltipPrimitive from "@radix-ui/react-tooltip";
import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/format";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

const variants: Record<Variant, string> = {
  primary: "bg-primary text-primary-foreground hover:bg-primary-hover",
  secondary: "bg-raised text-foreground hover:bg-border",
  ghost: "text-muted-foreground hover:bg-raised hover:text-foreground",
  danger: "text-destructive hover:bg-destructive/10",
};

const sizes: Record<Size, string> = {
  sm: "h-8 gap-1.5 px-3 text-[13px]",
  md: "h-9 gap-2 px-4 text-sm",
  lg: "h-11 gap-2 px-5 text-[15px]",
};

// Press feedback lives here once: every button sinks a little when pressed.
const base =
  "inline-flex shrink-0 select-none items-center justify-center whitespace-nowrap rounded-control font-medium " +
  "transition-[background-color,color,transform] [transition-duration:var(--dur-press)] [transition-timing-function:var(--ease-out)] " +
  "active:scale-[0.97] disabled:pointer-events-none disabled:opacity-40";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  icon?: ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "secondary", size = "md", icon, className, children, type = "button", ...rest },
  ref,
) {
  return (
    <button ref={ref} type={type} className={cn(base, variants[variant], sizes[size], className)} {...rest}>
      {icon}
      {children}
    </button>
  );
});

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  label: string;
  active?: boolean;
  size?: "sm" | "md" | "lg";
  tone?: "ghost" | "primary" | "bone";
  /** Keyboard shortcut shown in the tooltip. */
  shortcut?: ReactNode;
}

const iconSizes = { sm: "h-8 w-8", md: "h-9 w-9", lg: "h-10 w-10" };

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { label, active, size = "md", tone = "ghost", shortcut, className, children, type = "button", ...rest },
  ref,
) {
  const toneClass =
    tone === "primary"
      ? "rounded-full bg-primary text-primary-foreground hover:bg-primary-hover"
      : tone === "bone"
        ? "rounded-full bg-foreground text-background hover:bg-foreground/90"
        : cn("rounded-control hover:bg-raised", active ? "text-primary" : "text-muted-foreground hover:text-foreground");
  return (
    <Tooltip label={label} shortcut={shortcut}>
      <button
        ref={ref}
        type={type}
        aria-label={label}
        aria-pressed={active}
        className={cn(base, iconSizes[size], toneClass, className)}
        {...rest}
      >
        {children}
      </button>
    </Tooltip>
  );
});

export function Tooltip({ label, shortcut, children }: { label: string; shortcut?: ReactNode; children: ReactNode }) {
  return (
    <TooltipPrimitive.Root delayDuration={500}>
      <TooltipPrimitive.Trigger asChild>{children}</TooltipPrimitive.Trigger>
      <TooltipPrimitive.Portal>
        <TooltipPrimitive.Content
          side="top"
          sideOffset={6}
          className="menu-content z-50 flex max-w-64 items-center gap-2 rounded-control border border-border bg-raised px-2 py-1 text-[13px] text-foreground origin-[var(--radix-tooltip-content-transform-origin)]"
        >
          {label}
          {shortcut && <Kbd>{shortcut}</Kbd>}
        </TooltipPrimitive.Content>
      </TooltipPrimitive.Portal>
    </TooltipPrimitive.Root>
  );
}

export function Kbd({ children }: { children: ReactNode }) {
  return (
    <kbd className="rounded-[4px] border border-border-strong px-1 font-sans text-[11px] text-muted-foreground">{children}</kbd>
  );
}

/** A modifier plus an arrow key, drawn with the icon set instead of an arrow character. */
export function KeyCombo({ mod, arrow }: { mod: string; arrow: "left" | "right" }) {
  const Arrow = arrow === "left" ? ArrowLeft : ArrowRight;
  return (
    <span className="inline-flex items-center gap-0.5">
      {mod} <Arrow className="h-3 w-3" aria-label={arrow === "left" ? "seta para a esquerda" : "seta para a direita"} />
    </span>
  );
}
