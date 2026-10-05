import { useEffect, useState } from "react";
import { Bookmark, Loader2 } from "lucide-react";
import { useNavigate } from "react-router-dom";

import bucketListService from "../../services/bucketListService";

export default function BucketListButton({
  placeId,
  className = "",
  onSavedChange,
}) {
  const navigate = useNavigate();

  const [saved, setSaved] = useState(false);
  const [bucketId, setBucketId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let mounted = true;

    async function checkSaved() {
      if (!placeId) {
        setLoading(false);
        return;
      }

      try {
        setLoading(true);

        const result =
          await bucketListService.checkBucketList(placeId);

        if (!mounted) {
          return;
        }

        setSaved(Boolean(result?.saved));
        setBucketId(result?.bucketId ?? null);

        if (onSavedChange) {
          onSavedChange(
            Boolean(result?.saved),
            result?.bucketId ?? null
          );
        }
      } catch (error) {
        /*
         * Guests are allowed to browse destinations.
         *
         * Bucket-list status is only meaningful for
         * authenticated users, so a 401/403 here simply
         * means "not currently saved".
         */
        if (mounted) {
          setSaved(false);
          setBucketId(null);
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    checkSaved();

    return () => {
      mounted = false;
    };
  }, [placeId, onSavedChange]);

  async function handleClick(event) {
    event.preventDefault();
    event.stopPropagation();

    if (!placeId || saving) {
      return;
    }

    /*
     * Guest → Login
     */
    if (!localStorage.getItem("accessToken")) {
      navigate(
        `/login?redirect=/destinations/${placeId}`
      );
      return;
    }

    try {
      setSaving(true);

      /*
       * Remove from bucket list
       */
      if (saved) {
        if (!bucketId) {
          const check =
            await bucketListService.checkBucketList(placeId);

          if (!check?.saved || !check?.bucketId) {
            setSaved(false);
            setBucketId(null);

            if (onSavedChange) {
              onSavedChange(false, null);
            }

            return;
          }

          await bucketListService.removeFromBucketList(
            check.bucketId
          );
        } else {
          await bucketListService.removeFromBucketList(
            bucketId
          );
        }

        setSaved(false);
        setBucketId(null);

        if (onSavedChange) {
          onSavedChange(false, null);
        }

        return;
      }

      /*
       * Add to bucket list
       */
      const result =
        await bucketListService.addToBucketList(placeId);

      setSaved(true);
      setBucketId(result?.bucketId ?? null);

      if (onSavedChange) {
        onSavedChange(
          true,
          result?.bucketId ?? null
        );
      }
    } catch (error) {
      console.error(
        "Bucket list operation failed:",
        error
      );

      /*
       * If authentication expired, send the user
       * to login instead of silently failing.
       */
      if (
        error?.response?.status === 401 ||
        error?.response?.status === 403
      ) {
        navigate(
          `/login?redirect=/destinations/${placeId}`
        );
      }
    } finally {
      setSaving(false);
    }
  }

  const isBusy = loading || saving;

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={isBusy}
      aria-label={
        saved
          ? "Remove from bucket list"
          : "Add to bucket list"
      }
      title={
        saved
          ? "Remove from bucket list"
          : "Add to bucket list"
      }
      className={[
        "inline-flex items-center justify-center transition-all duration-200",
        "disabled:cursor-not-allowed disabled:opacity-70",
        className,
      ].join(" ")}
    >
      {isBusy ? (
        <Loader2
          size={18}
          className="animate-spin"
        />
      ) : (
        <Bookmark
          size={18}
          fill={saved ? "currentColor" : "none"}
        />
      )}
    </button>
  );
}