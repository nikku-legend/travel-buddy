import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  BedDouble,
  CheckCircle2,
  Clock,
  MapPin,
  Sparkles,
  Star,
  Ticket,
} from "lucide-react";

import reviewService, { LIMITS } from "../services/reviewService";

/*
 * The post-trip Review Center. (SRS 2.3 TP-12, section 10/11)
 *
 * One card per service that has completed and can be reviewed. The
 * backend decides what is eligible; this only renders what it is
 * told, which is why there is no client-side eligibility logic here
 * beyond formatting the booleans that arrive on each card.
 */

const TARGET_ICON = {
  HOTEL: BedDouble,
  GUIDE: Sparkles,
  CAB: Ticket,
  DESTINATION: MapPin,
};

/*
 * Card tone follows the review's fate, not the card's. A rejected
 * review is a problem to solve, so it gets the alert treatment
 * rather than the neutral one.
 */
const STATUS_TONE = {
  "Awaiting moderation": "bg-amber-500/20 text-amber-200",
  Published: "bg-emerald-500/20 text-emerald-200",
  Flagged: "bg-red-500/20 text-red-200",
  Rewritten: "bg-purple-500/20 text-purple-200",
  "To review": "bg-purple-500/20 text-purple-200",
  Locked: "bg-white/10 text-white/60",
};

function StarPicker({ value, onChange, disabled }) {
  return (
    <div
      className="flex items-center gap-1"
      role="radiogroup"
      aria-label="Your rating"
    >
      {[1, 2, 3, 4, 5].map((star) => (
        <button
          key={star}
          type="button"
          role="radio"
          aria-checked={value === star}
          aria-label={`${star} star${star > 1 ? "s" : ""}`}
          disabled={disabled}
          onClick={() => onChange(star)}
          className="transition disabled:cursor-not-allowed"
        >
          <Star
            size={26}
            className={
              star <= value
                ? "fill-amber-400 text-amber-400"
                : "text-white/25"
            }
          />
        </button>
      ))}
    </div>
  );
}

/*
 * One service. Self-contained so an open form cannot be reset by a
 * sibling card re-rendering, and so submitting one card does not
 * touch the text half-written in another.
 */
function ReviewCard({ card, onSubmitted }) {
  const [open, setOpen] = useState(false);
  const [rating, setRating] = useState(0);
  const [title, setTitle] = useState("");
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const actionable = reviewService.canReview(card);
  const Icon = TARGET_ICON[card.targetType] ?? MapPin;
  const tone = STATUS_TONE[reviewService.statusLabel(card)];

  /*
   * Live guard so the submit button is never a trap, though the
   * server re-validates all of it.
   */
  const tooLong =
    title.length > LIMITS.title || comment.length > LIMITS.comment;
  const ready = rating >= LIMITS.rating.min && !tooLong;

  const submit = useCallback(async () => {
    setBusy(true);
    setError("");

    try {
      await reviewService.submitReview({
        targetType: card.targetType,
        targetId: card.targetId,
        rating,
        title: title.trim() || null,
        comment: comment.trim() || null,
      });

      /*
       * Re-derive from the server rather than patching this card
       * here. The card's own status, its actionable flag and the
       * summary counts all move together, and guessing at that
       * locally is how a centre ends up claiming "2 of 3" while
       * showing one card.
       */
      await onSubmitted();
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to submit your review."
      );
    } finally {
      setBusy(false);
    }
  }, [card.targetType, card.targetId, rating, title, comment, onSubmitted]);

  return (
    <li className="rounded-2xl border border-white/10 bg-white/5 p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white/10">
            <Icon className="h-4 w-4 text-amber-300" />
          </span>

          <div className="min-w-0">
            <p className="truncate font-semibold text-white">
              {card.targetName}
            </p>

            <p className="mt-0.5 text-xs text-white/50">
              {reviewService.targetLabel(card.targetType)}
              {card.cityName ? ` in ${card.cityName}` : ""}
              {card.checkIn && card.checkOut
                ? ` · ${card.checkIn} to ${card.checkOut}`
                : ""}
            </p>
          </div>
        </div>

        <span
          className={[
            "shrink-0 rounded-full px-2.5 py-0.5",
            "text-[10px] font-semibold uppercase tracking-wider",
            tone ?? "bg-white/10 text-white/60",
          ].join(" ")}
        >
          {reviewService.statusLabel(card)}
        </span>
      </div>

      {/*
       * Why this card exists, per section 10. Eligibility is
       * booking-based, so saying so is more useful than leaving the
       * traveller to wonder why this one opened and the others did
       * not.
       */}
      {card.reason && (
        <p className="mt-3 text-xs text-white/40">{card.reason}</p>
      )}

      {/* ---- what the traveller already wrote ---- */}
      {card.reviewId && (
        <div className="mt-4 rounded-xl border border-white/10 bg-white/5 p-4">
          {card.rating != null && (
            <div className="flex items-center gap-0.5">
              {Array.from({ length: card.rating }).map((_, i) => (
                <Star
                  key={i}
                  size={14}
                  className="fill-amber-400 text-amber-400"
                />
              ))}
            </div>
          )}

          {card.reviewTitle && (
            <p className="mt-2 font-semibold text-white">
              {card.reviewTitle}
            </p>
          )}

          {card.reviewComment && (
            <p className="mt-1 whitespace-pre-line text-sm text-white/70">
              {card.reviewComment}
            </p>
          )}

          {card.reviewStatus === "PENDING" && (
            <p className="mt-2 flex items-center gap-1.5 text-xs text-amber-200/80">
              <Clock className="h-3.5 w-3.5 shrink-0" />
              Submitted. It goes live once a moderator has read it.
            </p>
          )}

          {card.reviewStatus === "PUBLISHED" && (
            <p className="mt-2 flex items-center gap-1.5 text-xs text-emerald-300/80">
              <CheckCircle2 className="h-3.5 w-3.5 shrink-0" />
              Live on the listing.
            </p>
          )}
        </div>
      )}

      {/*
       * The moderator's reason, when one exists. A rejected review
       * with no explanation is unactionable: the traveller cannot
       * guess what to change.
       */}
      {card.moderationReason && (
        <p className="mt-3 flex items-start gap-2 rounded-xl bg-red-500/10 p-3 text-xs text-red-200">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          {card.moderationReason}
        </p>
      )}

      {/* ---- the form ---- */}
      {actionable && !open && (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="mt-4 w-full rounded-xl bg-amber-400 px-4 py-2.5 text-sm font-semibold text-slate-900 hover:bg-amber-300"
        >
          {card.reviewStatus === "REJECTED"
            ? "Write it again"
            : "Write a review"}
        </button>
      )}

      {actionable && open && (
        <div className="mt-4 space-y-3 rounded-xl border border-white/10 bg-white/5 p-4">
          <div>
            <span className="text-xs font-semibold uppercase tracking-wider text-white/50">
              Rating
            </span>
            <div className="mt-1.5">
              <StarPicker
                value={rating}
                onChange={setRating}
                disabled={busy}
              />
            </div>
          </div>

          <div>
            <label
              htmlFor={`review-title-${card.unlockId}`}
              className="text-xs font-semibold uppercase tracking-wider text-white/50"
            >
              Title
            </label>
            <input
              id={`review-title-${card.unlockId}`}
              type="text"
              maxLength={LIMITS.title}
              value={title}
              disabled={busy}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Sum it up in a few words"
              className="mt-1.5 w-full rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-sm text-white placeholder:text-white/30 focus:border-amber-300/50 focus:outline-none"
            />
          </div>

          <div>
            <label
              htmlFor={`review-comment-${card.unlockId}`}
              className="text-xs font-semibold uppercase tracking-wider text-white/50"
            >
              Your review
            </label>
            <textarea
              id={`review-comment-${card.unlockId}`}
              rows={4}
              maxLength={LIMITS.comment}
              value={comment}
              disabled={busy}
              onChange={(e) => setComment(e.target.value)}
              placeholder="What was it like for you?"
              className="mt-1.5 w-full resize-y rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-sm text-white placeholder:text-white/30 focus:border-amber-300/50 focus:outline-none"
            />
            <p className="mt-1 text-right text-[10px] text-white/30">
              {comment.length}/{LIMITS.comment}
            </p>
          </div>

          {tooLong && (
            <p className="text-xs text-red-300">
              That is longer than the server accepts.
            </p>
          )}

          {error && <p className="text-xs text-red-300">{error}</p>}

          <div className="flex gap-2">
            <button
              type="button"
              onClick={submit}
              disabled={busy || !ready}
              className="flex-1 rounded-xl bg-amber-400 px-4 py-2.5 text-sm font-semibold text-slate-900 hover:bg-amber-300 disabled:opacity-50"
            >
              {busy ? "Submitting…" : "Submit review"}
            </button>

            <button
              type="button"
              onClick={() => setOpen(false)}
              disabled={busy}
              className="rounded-xl border border-white/20 px-4 py-2.5 text-sm text-white/70 hover:bg-white/5 disabled:opacity-50"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </li>
  );
}

export default function ReviewCenter({ tripId }) {
  const [centre, setCentre] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      setCentre(await reviewService.getReviewCentre(tripId));
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to load your reviews."
      );
    } finally {
      setLoading(false);
    }
  }, [tripId]);

  useEffect(() => {
    load();
  }, [load]);

  const summary = centre?.summary;

  /*
   * "2 of 3 reviewed" in words.
   *
   * `total` is awaiting + submitted + published + flagged, and a
   * FLAGGED card still accepts a review -- ReviewUnlockStatus
   * .acceptsReview() covers both ELIGIBLE and FLAGGED. So flagged
   * counts as outstanding, not as done, and only submitted + published
   * are work the traveller has actually finished. Treating flagged as
   * finished is what would make a trip read "all done" while still
   * showing an unreviewed card.
   */
  const outstanding = summary
    ? summary.awaitingReview + summary.flagged
    : 0;
  const finished = summary
    ? summary.submitted + summary.published
    : 0;

  const headline = useMemo(() => {
    if (!summary || summary.total === 0) {
      return null;
    }

    if (outstanding === 0) {
      return "All done. Your reviews are with a moderator.";
    }

    return `${finished} of ${summary.total} reviewed`;
  }, [summary, outstanding, finished]);

  /*
   * Loading, or still shut.
   *
   * Gated on the server's own `reviewsUnlocked`, never on the trip
   * status. The trip can read REVIEW_OPEN while no individual service
   * has completed, and eligibility is booking-based rather than
   * date-based -- so the status is not a reliable proxy. While shut
   * this renders nothing at all, because an empty card here would
   * read as "you have nothing to review" rather than "not yet",
   * which is a different and wrong claim.
   *
   * The error check has to come first. A failed load leaves `centre`
   * null, so testing `centre` before `error` would swallow every
   * failure and the traveller would never learn why the section is
   * missing.
   */
  if (error && !centre) {
    return (
      <section className="mt-8">
        <div className="flex items-center gap-2 rounded-xl border border-amber-400/30 bg-amber-400/10 px-4 py-3 text-sm text-amber-200">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      </section>
    );
  }

  if (loading && !centre) {
    return null;
  }

  if (!centre?.reviewsUnlocked) {
    return null;
  }

  return (
    <section className="mt-8">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-bold text-white">
          <Star className="h-5 w-5 text-amber-300" />
          Rate your trip
        </h2>

        {headline && (
          <span className="rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-white/70">
            {headline}
          </span>
        )}
      </div>

      {!loading &&
        !error &&
        centre?.cards?.length === 0 && (
          <div className="rounded-2xl border border-dashed border-white/15 p-8 text-center">
            <Star className="mx-auto h-6 w-6 text-white/30" />
            <p className="mt-3 text-white/60">
              Nothing to review yet.
            </p>
            <p className="mt-1 text-sm text-white/40">
              Cards appear here as each service on your trip is
              completed.
            </p>
          </div>
        )}

      <ul className="space-y-4">
        {(centre?.cards ?? []).map((card) => (
          <ReviewCard
            key={
              card.unlockId ?? `${card.targetType}-${card.targetId}`
            }
            card={card}
            onSubmitted={load}
          />
        ))}
      </ul>
    </section>
  );
}