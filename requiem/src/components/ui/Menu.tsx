import * as ContextMenu from "@radix-ui/react-context-menu";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import type { ReactNode } from "react";
import { cn } from "@/lib/format";

/** One description of a menu, drawn as a dropdown (the ⋯ button) or a right-click menu. */
export type MenuEntry =
  | { type: "item"; label: string; icon?: ReactNode; onSelect: () => void; danger?: boolean; disabled?: boolean }
  | { type: "submenu"; label: string; icon?: ReactNode; entries: MenuEntry[] }
  | { type: "separator" }
  | { type: "label"; label: string };

const content =
  "menu-content z-50 min-w-52 max-w-80 rounded-surface border border-border bg-raised p-1 text-sm text-foreground shadow-2xl shadow-black/50";
const item =
  "flex h-8 select-none items-center gap-2 rounded-[4px] px-2 outline-none data-[disabled]:opacity-40 data-[highlighted]:bg-border [&_svg]:text-muted-foreground";

type Kit = typeof DropdownMenu | typeof ContextMenu;

function Entries({ kit, entries }: { kit: Kit; entries: MenuEntry[] }) {
  const K = kit as typeof DropdownMenu;
  return (
    <>
      {entries.map((e, i) => {
        if (e.type === "separator") return <K.Separator key={i} className="my-1 h-px bg-border" />;
        if (e.type === "label")
          return (
            <K.Label key={i} className="px-2 pb-1 pt-1.5 text-[12px] text-faint-foreground">
              {e.label}
            </K.Label>
          );
        if (e.type === "submenu")
          return (
            <K.Sub key={i}>
              <K.SubTrigger className={cn(item, "data-[state=open]:bg-border")}>
                {e.icon}
                <span className="flex-1 truncate">{e.label}</span>
              </K.SubTrigger>
              <K.Portal>
                <K.SubContent className={cn(content, "max-h-80 overflow-y-auto")} sideOffset={4}>
                  <Entries kit={kit} entries={e.entries} />
                </K.SubContent>
              </K.Portal>
            </K.Sub>
          );
        return (
          <K.Item key={i} disabled={e.disabled} onSelect={e.onSelect} className={cn(item, e.danger && "text-destructive [&_svg]:text-destructive")}>
            {e.icon}
            <span className="truncate">{e.label}</span>
          </K.Item>
        );
      })}
    </>
  );
}

export function Dropdown({ trigger, entries, align = "end" }: { trigger: ReactNode; entries: MenuEntry[]; align?: "start" | "end" }) {
  return (
    <DropdownMenu.Root modal={false}>
      <DropdownMenu.Trigger asChild>{trigger}</DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content align={align} sideOffset={4} className={cn(content, "origin-[var(--radix-dropdown-menu-content-transform-origin)]")}>
          <Entries kit={DropdownMenu} entries={entries} />
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

export function RightClick({ children, entries }: { children: ReactNode; entries: MenuEntry[] }) {
  return (
    <ContextMenu.Root modal={false}>
      <ContextMenu.Trigger asChild>{children}</ContextMenu.Trigger>
      <ContextMenu.Portal>
        <ContextMenu.Content className={cn(content, "origin-[var(--radix-context-menu-content-transform-origin)]")}>
          <Entries kit={ContextMenu} entries={entries} />
        </ContextMenu.Content>
      </ContextMenu.Portal>
    </ContextMenu.Root>
  );
}
