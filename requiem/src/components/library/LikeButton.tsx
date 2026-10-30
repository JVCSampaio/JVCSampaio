import { Heart } from "@phosphor-icons/react";
import { IconButton } from "@/components/ui/Button";
import { backend } from "@/lib/backend";
import { cn } from "@/lib/format";
import type { Item } from "@/lib/types";
import { useLibrary } from "@/state/library";
import { toast } from "@/state/toasts";

/** Likes or unlikes at once on screen; the engine's item event confirms it. */
export async function toggleLike(item: Item) {
  const liked = !item.likedAt;
  const put = (likedAt: number | null) =>
    useLibrary.setState((s) => (s.items[item.id] ? { items: { ...s.items, [item.id]: { ...s.items[item.id], likedAt } } } : {}));
  put(liked ? Date.now() : null);
  try {
    await (await backend()).itemLike(item.id, liked);
  } catch (e) {
    put(item.likedAt);
    toast(String(e), "error");
  }
}

/** The heart. Shown always when liked; in lists, an unliked heart appears on hover or focus. */
export function LikeButton({ item, reveal = false, className }: { item: Item; reveal?: boolean; className?: string }) {
  const liked = !!item.likedAt;
  const hidden = reveal && !liked ? "[@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 focus-visible:opacity-100" : "";
  return (
    <IconButton
      label={liked ? "Descurtir" : "Curtir"}
      size="sm"
      active={liked}
      className={cn(hidden, className)}
      onClick={(e) => {
        e.stopPropagation();
        void toggleLike(item);
      }}
    >
      <Heart weight={liked ? "fill" : "regular"} className="h-4 w-4" />
    </IconButton>
  );
}
