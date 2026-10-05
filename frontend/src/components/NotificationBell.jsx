import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Bell, CheckCheck, X } from "lucide-react";

import notificationService from "../services/notificationService";
import { useAuth } from "../context/useAuth";

const POLL_INTERVAL_MS = 60_000;

function formatWhen(iso) {
  if (!iso) {
    return "";
  }

  /*
   * Compared against the raw string rather than a parsed Date so
   * the relative time never drifts with the browser's timezone.
   */
  const then = new Date(iso).getTime();
  const seconds = Math.max(0, (Date.now() - then) / 1000);

  if (seconds < 60) {
    return "just now";
  }

  if (seconds < 3600) {
    return `${Math.floor(seconds / 60)}m ago`;
  }

  if (seconds < 86_400) {
    return `${Math.floor(seconds / 3600)}h ago`;
  }

  return `${Math.floor(seconds / 86_400)}d ago`;
}

export default function NotificationBell() {
  const { user } = useAuth();

  const navigate = useNavigate();

  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  const panelRef = useRef(null);

  const refreshCount = useCallback(async () => {
    if (!user) {
      return;
    }

    try {
      setUnread(await notificationService.unreadCount());
    } catch {
      /*
       * A failed badge poll must never surface an error. The bell
       * is ambient chrome, and a transient network blip should not
       * interrupt whatever the user is doing.
       */
    }
  }, [user]);

  /*
   * ============================================================
   * BADGE POLLING
   *
   * Polls the count only. The list endpoint returns every message
   * body, so polling it would re-download the whole inbox on every
   * tick for a single integer.
   * ============================================================
   */
  useEffect(() => {
    if (!user) {
      setUnread(0);
      setItems([]);
      return undefined;
    }

    refreshCount();

    const timer = setInterval(refreshCount, POLL_INTERVAL_MS);

    return () => clearInterval(timer);
  }, [user, refreshCount]);

  /*
   * ============================================================
   * FETCH THE INBOX ONCE OPENED
   * ============================================================
   */
  useEffect(() => {
    if (!open || !user) {
      return;
    }

    let cancelled = false;

    setLoading(true);

    notificationService
      .list({ size: 20 })
      .then((page) => {
        if (!cancelled) {
          setItems(page.content);
          setUnread(page.unreadCount);
        }
      })
      .catch(() => {
        /* Leave the previous contents rather than blanking them. */
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [open, user]);

  /*
   * ============================================================
   * DISMISS ON OUTSIDE CLICK OR ESCAPE
   * ============================================================
   */
  useEffect(() => {
    if (!open) {
      return undefined;
    }

    function handleOutsideClick(event) {
      if (
        panelRef.current &&
        !panelRef.current.contains(event.target)
      ) {
        setOpen(false);
      }
    }

    function handleEscape(event) {
      if (event.key === "Escape") {
        setOpen(false);
      }
    }

    document.addEventListener("mousedown", handleOutsideClick);
    document.addEventListener("keydown", handleEscape);

    return () => {
      document.removeEventListener(
        "mousedown",
        handleOutsideClick
      );
      document.removeEventListener("keydown", handleEscape);
    };
  }, [open]);

  /*
   * ============================================================
   * OPEN A NOTIFICATION
   *
   * Marked read before navigating so a failed route still clears
   * the badge. The backend scopes this to the caller, so a
   * notification belonging to someone else simply will not open.
   * ============================================================
   */
  async function handleOpen(item) {
    if (!item.read) {
      notificationService
        .markRead(item.notificationId)
        .then(() => {
          setItems((current) =>
            current.map((existing) =>
              existing.notificationId === item.notificationId
                ? { ...existing, read: true }
                : existing
            )
          );
          setUnread((count) => Math.max(0, count - 1));
        })
        .catch(() => {
          /* Navigation is more important than the badge. */
        });
    }

    setOpen(false);

    if (item.actionUrl) {
      navigate(item.actionUrl);
    }
  }

  async function handleMarkAllRead() {
    try {
      await notificationService.markAllRead();

      setUnread(0);

      setItems((current) =>
        current.map((item) => ({ ...item, read: true }))
      );
    } catch {
      /* Nothing useful to do; the next poll will correct it. */
    }
  }

  if (!user) {
    return null;
  }

  return (
    <div className="relative" ref={panelRef}>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label={
          unread > 0
            ? `Notifications, ${unread} unread`
            : "Notifications"
        }
        aria-expanded={open}
        className="relative rounded-lg p-2 text-slate-600 transition hover:bg-slate-100 hover:text-slate-900"
      >
        <Bell size={20} />

        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold leading-none text-white">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-12 z-50 w-[360px] max-w-[calc(100vw-2rem)] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-900">
              Notifications
            </h2>

            {unread > 0 && (
              <button
                type="button"
                onClick={handleMarkAllRead}
                className="inline-flex items-center gap-1 text-xs font-medium text-slate-500 transition hover:text-slate-900"
              >
                <CheckCheck size={13} />
                Mark all read
              </button>
            )}
          </div>

          <div className="max-h-[380px] overflow-y-auto">
            {loading && items.length === 0 && (
              <p className="px-4 py-8 text-center text-sm text-slate-400">
                Loading...
              </p>
            )}

            {!loading && items.length === 0 && (
              <p className="px-4 py-8 text-center text-sm text-slate-400">
                You have no notifications yet.
              </p>
            )}

            {items.map((item) => (
              <button
                key={item.notificationId}
                type="button"
                onClick={() => handleOpen(item)}
                className={`block w-full border-b border-slate-50 px-4 py-3 text-left transition hover:bg-slate-50 ${
                  item.read ? "opacity-60" : ""
                }`}
              >
                <div className="flex items-start gap-2">
                  {!item.read && (
                    <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-sky-500" />
                  )}

                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-slate-900">
                      {item.title}
                    </p>

                    {item.body && (
                      <p className="mt-0.5 line-clamp-2 text-xs leading-relaxed text-slate-500">
                        {item.body}
                      </p>
                    )}

                    <p className="mt-1 text-[11px] text-slate-400">
                      {formatWhen(item.createdAt)}
                    </p>
                  </div>
                </div>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export { X };