import * as SliderPrimitive from "@radix-ui/react-slider";
import * as SwitchPrimitive from "@radix-ui/react-switch";
import * as ToggleGroup from "@radix-ui/react-toggle-group";
import { forwardRef, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/format";

export interface SegmentOption<T extends string> {
  value: T;
  label: string;
  count?: number;
}

/** A handful of choices that fit in one row. Under six options, never a dropdown. */
export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
  className,
}: {
  value: T;
  options: SegmentOption<T>[];
  onChange: (value: T) => void;
  label: string;
  className?: string;
}) {
  return (
    <ToggleGroup.Root
      type="single"
      value={value}
      aria-label={label}
      onValueChange={(v) => v && onChange(v as T)}
      className={cn("inline-flex items-center gap-1 rounded-control bg-surface p-1", className)}
    >
      {options.map((o) => (
        <ToggleGroup.Item
          key={o.value}
          value={o.value}
          className={cn(
            "inline-flex h-7 items-center gap-1.5 rounded-[4px] px-3 text-sm font-medium",
            "transition-[background-color,color] [transition-duration:var(--dur-fast)]",
            "text-muted-foreground hover:text-foreground data-[state=on]:bg-raised data-[state=on]:text-foreground",
          )}
        >
          {o.label}
          {o.count != null && o.count > 0 && <span className="text-[12px] tabular-nums text-faint-foreground">{o.count}</span>}
        </ToggleGroup.Item>
      ))}
    </ToggleGroup.Root>
  );
}

export function Slider({
  value,
  min = 0,
  max,
  onChange,
  onCommit,
  label,
  valueText,
  className,
  tone = "bone",
  step,
  vertical,
}: {
  value: number;
  min?: number;
  max: number;
  onChange: (value: number) => void;
  onCommit?: (value: number) => void;
  label: string;
  /** What a screen reader says for the value ("3 dB"), when the number alone is not enough. */
  valueText?: string;
  className?: string;
  tone?: "bone" | "ember";
  step?: number;
  vertical?: boolean;
}) {
  return (
    <SliderPrimitive.Root
      value={[Math.max(min, Math.min(value, max || 0))]}
      min={min}
      max={max || 1}
      step={step ?? (max - min > 60 ? 1 : 0.01)}
      orientation={vertical ? "vertical" : "horizontal"}
      aria-label={label}
      onValueChange={([v]) => onChange(v)}
      onValueCommit={([v]) => onCommit?.(v)}
      className={cn("group relative flex touch-none select-none items-center", vertical ? "w-4 flex-col" : "h-4", className)}
    >
      <SliderPrimitive.Track className={cn("relative grow overflow-hidden rounded-full bg-border-strong", vertical ? "w-1" : "h-1")}>
        <SliderPrimitive.Range className={cn("absolute", vertical ? "w-full" : "h-full", tone === "ember" ? "bg-primary" : "bg-foreground")} />
      </SliderPrimitive.Track>
      <SliderPrimitive.Thumb aria-label={label} aria-valuetext={valueText} className="block h-3 w-3 rounded-full bg-foreground shadow-[0_0_0_3px_hsl(var(--background))]" />
    </SliderPrimitive.Root>
  );
}

export function Switch({
  checked,
  onChange,
  label,
  id,
  disabled,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  /** Leave it out when a visible <label htmlFor={id}> names the switch. */
  label?: string;
  id?: string;
  disabled?: boolean;
}) {
  return (
    <SwitchPrimitive.Root
      id={id}
      checked={checked}
      disabled={disabled}
      aria-label={label}
      onCheckedChange={onChange}
      className={cn(
        "relative inline-flex h-5 w-9 shrink-0 items-center rounded-full bg-border-strong",
        "transition-[background-color] [transition-duration:var(--dur-fast)] data-[state=checked]:bg-primary disabled:opacity-40",
      )}
    >
      <SwitchPrimitive.Thumb className="block h-4 w-4 translate-x-0.5 rounded-full bg-foreground transition-transform [transition-duration:var(--dur-fast)] [transition-timing-function:var(--ease-out)] data-[state=checked]:translate-x-[18px]" />
    </SwitchPrimitive.Root>
  );
}

export const TextField = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { icon?: ReactNode; end?: ReactNode }>(
  function TextField({ icon, end, className, ...rest }, ref) {
    return (
      <div
        className={cn(
          "flex h-10 items-center gap-2 rounded-control border border-border bg-surface px-3 text-muted-foreground",
          "transition-[border-color] [transition-duration:var(--dur-fast)] focus-within:border-border-strong",
          className,
        )}
      >
        {icon}
        <input
          ref={ref}
          className="h-full min-w-0 flex-1 bg-transparent text-[15px] text-foreground outline-none placeholder:text-faint-foreground"
          {...rest}
        />
        {end}
      </div>
    );
  },
);

/** A thin bar for a download. Only drawn when the fraction is real. */
export function ProgressBar({ fraction, className }: { fraction: number; className?: string }) {
  return (
    <div className={cn("h-1 overflow-hidden rounded-full bg-border", className)} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(fraction * 100)}>
      <div
        className="h-full origin-left rounded-full bg-primary transition-transform [transition-duration:var(--dur-slow)] [transition-timing-function:linear]"
        style={{ transform: `scaleX(${Math.max(0, Math.min(1, fraction))})` }}
      />
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("skeleton rounded-control", className)} aria-hidden />;
}
