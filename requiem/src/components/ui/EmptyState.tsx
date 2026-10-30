import type { ReactNode } from "react";

/** A wait on the person's first data: the illustration, what is missing, the action that ends it. */
export function EmptyState({
  icon,
  title,
  children,
  actions,
}: {
  icon: ReactNode;
  title: string;
  children?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-6 py-16 text-center">
      <div className="text-faint-foreground [&_svg]:h-20 [&_svg]:w-20">{icon}</div>
      <h2 className="mt-4 font-display text-4xl font-semibold text-foreground">{title}</h2>
      {children && <div className="mt-2 text-[15px] text-muted-foreground">{children}</div>}
      {actions && <div className="mt-8 flex flex-wrap items-center justify-center gap-2">{actions}</div>}
    </div>
  );
}
