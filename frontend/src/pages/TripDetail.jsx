import { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  BedDouble,
  CalendarDays,
  CheckCircle2,
  Coins,
  Loader2,
  MapPin,
  Sparkles,
  Ticket,
} from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";

import TreasureMap from "../components/TreasureMap";
import tripService from "../services/tripService";

/*
 * One planned trip. (SRS 2.2)
 *
 * The route, the cart and the treasure map arrive in one payload
 * from the backend, and this page renders them side by side
 * without refetching. Three separate requests would mean three
 * chances to show a cart that disagrees with the map next to it.
 */

const STATUS_TONE = {
  DRAFT: "bg-white/10 text-white/70",
  PLANNING: "bg-sky-500/20 text-sky-200",
  READY_FOR_CHECKOUT: "bg-amber-500/20 text-amber-200",
  CONFIRMED: "bg-emerald-500/20 text-emerald-200",
  IN_PROGRESS: "bg-indigo-500/20 text-indigo-200",
  COMPLETED: "bg-emerald-500/20 text-emerald-200",
  REVIEW_OPEN: "bg-purple-500/20 text-purple-200",
  CLOSED: "bg-white/10 text-white/50",
};

const SELECTION_ICON = {
  HOTEL: BedDouble,
  GUIDE: Sparkles,
  CAB: Ticket,
  PLACE: MapPin,
};

function money(amount, currency) {
  if (amount === null || amount === undefined) {
    return null;
  }
  return `${currency ?? "INR"} ${Number(amount).toFixed(0)}`;
}

function TripDetail() {
  const { tripId } = useParams();
  const navigate = useNavigate();

  const [trip, setTrip] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  /*
   * Checkout state.
   *
   * <p>Kept separate from `trip` on purpose: the preview is a server
   * decision about one moment in time, and folding it into the trip
   * object would let a stale total survive a refetch.
   */
  const [preview, setPreview] = useState(null);
  const [checkoutBusy, setCheckoutBusy] = useState(false);
  const [checkoutError, setCheckoutError] = useState("");
  const [paid, setPaid] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const data = await tripService.getTrip(tripId);
      setTrip(data);
    } catch (err) {
      const status = err?.response?.status;

      if (status === 401 || status === 403) {
        setError(
          "Please log in to view this trip."
        );
      } else {
        setError(
          err?.response?.data?.message ||
            "Unable to load this trip."
        );
      }
    } finally {
      setLoading(false);
    }
  }, [tripId]);

  useEffect(() => {
    load();
  }, [load]);

  const startCheckout = useCallback(async () => {
    setCheckoutBusy(true);
    setCheckoutError("");
    setPaid(null);

    try {
      setPreview(await tripService.previewCheckout(tripId));
    } catch (err) {
      setCheckoutError(
        err?.response?.data?.message ||
          "Unable to start checkout."
      );
    } finally {
      setCheckoutBusy(false);
    }
  }, [tripId]);

  /*
   * Agree to the revalidated total, then pay.
   *
   * <p>Two calls rather than one, because section 6 requires the
   * traveller to see the revalidated figure before any money moves.
   * The accepted total is the number currently on screen, so a bill
   * that moved again is rejected by the backend rather than charged.
   */
  const acceptAndPay = useCallback(
    async (successful) => {
      if (!preview) {
        return;
      }

      setCheckoutBusy(true);
      setCheckoutError("");

      try {
        await tripService.confirmCheckout(
          tripId,
          preview.checkoutId,
          preview.revalidatedTotal,
          preview.bill?.currency
        );

        setPaid(
          await tripService.payCheckout(
            tripId,
            preview.checkoutId,
            successful
          )
        );

        // Paying is what confirms the trip and ticks the start
        // checkpoint, so the page must refetch or it would show a
        // stale status next to a paid bill.
        await load();
      } catch (err) {
        setCheckoutError(
          err?.response?.data?.message ||
            "Payment could not be completed."
        );
      } finally {
        setCheckoutBusy(false);
      }
    },
    [preview, tripId, load]
  );

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-amber-300" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16 text-center">
        <AlertTriangle className="mx-auto h-8 w-8 text-amber-300" />
        <p className="mt-4 text-white/80">{error}</p>
        <Link
          to="/trips"
          className="mt-6 inline-block text-sm text-amber-300 underline"
        >
          Back to my trips
        </Link>
      </div>
    );
  }

  if (!trip) {
    return null;
  }

  const cities = trip.cities ?? [];
  const selections = trip.selections ?? [];
  const estimated = money(
    trip.estimatedTotal,
    trip.currency
  );

  /*
   * Checkout is only offered for a trip that still has something to
   * pay for. A confirmed or already-closed trip is not offered a
   * second payment, because the button could only fail.
   */
  const canCheckout =
    selections.length > 0 &&
    trip.status !== "CONFIRMED" &&
    trip.status !== "COMPLETED" &&
    trip.status !== "CLOSED" &&
    !paid;

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <button
        type="button"
        onClick={() => navigate("/trips")}
        className="mb-6 inline-flex items-center gap-2 text-sm text-white/60 hover:text-white"
      >
        <ArrowLeft className="h-4 w-4" />
        My trips
      </button>

      <header className="mb-8">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-bold text-white sm:text-3xl">
            {trip.title}
          </h1>

          <span
            className={[
              "rounded-full px-3 py-1 text-xs font-semibold",
              "uppercase tracking-wider",
              STATUS_TONE[trip.status] ??
                "bg-white/10 text-white/60",
            ].join(" ")}
          >
            {trip.status}
          </span>
        </div>

        <div className="mt-3 flex flex-wrap gap-x-6 gap-y-2 text-sm text-white/60">
          <span className="inline-flex items-center gap-2">
            <CalendarDays className="h-4 w-4" />
            {trip.startDate} to {trip.endDate}
            {trip.nights > 0 && (
              <span className="text-white/40">
                ({trip.nights} nights)
              </span>
            )}
          </span>

          {estimated && (
            <span className="inline-flex items-center gap-2">
              <Coins className="h-4 w-4" />
              {estimated} estimated
            </span>
          )}

          {trip.overBudget && (
            <span className="inline-flex items-center gap-2 text-amber-300">
              <AlertTriangle className="h-4 w-4" />
              Over budget
            </span>
          )}
        </div>
      </header>

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="space-y-6">
          {/* ==========================================================
              ROUTE
             ========================================================== */}
          <section className="rounded-2xl border border-white/10 bg-white/5 p-5">
            <h2 className="mb-4 text-sm font-bold uppercase tracking-wider text-white">
              Route
            </h2>

            {cities.length === 0 ? (
              <p className="text-sm text-white/50">
                No cities chosen yet. Add stops to
                build the route.
              </p>
            ) : (
              <ol className="space-y-3">
                {cities.map((stop, index) => (
                  <li
                    key={stop.tripCityId}
                    className="flex gap-3"
                  >
                    <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white/10 text-xs font-semibold text-white/70">
                      {index + 1}
                    </span>

                    <div className="min-w-0">
                      <p className="font-medium text-white">
                        {stop.cityName}
                      </p>
                      <p className="text-xs text-white/50">
                        {stop.arrivalDate &&
                          stop.departureDate
                            ? `${stop.arrivalDate} to ${stop.departureDate}`
                            : "Dates not decided yet"}
                      </p>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </section>

          {/* ==========================================================
              CART
             ========================================================== */}
          <section className="rounded-2xl border border-white/10 bg-white/5 p-5">
            <h2 className="mb-4 text-sm font-bold uppercase tracking-wider text-white">
              Your picks
            </h2>

            {selections.length === 0 ? (
              <p className="text-sm text-white/50">
                Nothing added yet.
              </p>
            ) : (
              <ul className="space-y-3">
                {selections.map((selection) => {
                  const Icon =
                    SELECTION_ICON[
                      selection.selectionType
                    ] ?? MapPin;

                  return (
                    <li
                      key={selection.selectionId}
                      className="flex items-center gap-3"
                    >
                      <Icon className="h-4 w-4 shrink-0 text-amber-300" />

                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium text-white">
                          {selection.targetName ??
                            selection.selectionType}
                        </p>
                        <p className="text-xs text-white/50">
                          {selection.cityName
                            ? `${selection.cityName} · `
                            : ""}
                          {selection.status}
                        </p>
                      </div>

                      {selection.quotedAmount != null && (
                        <span className="shrink-0 text-sm text-white/70">
                          {money(
                            selection.quotedAmount,
                            selection.currency
                          )}
                        </span>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          {/* ==========================================================
              CHECKOUT
             ========================================================== */}
          {/*
           * A declined payment is the normal case, not an error
           * case, so it is offered as an explicit choice. A single
           * "Pay" button would make testing a decline impossible and
           * would hide the fact that the payment is simulated.
           */}
          {canCheckout && (
            <section className="rounded-2xl border border-white/10 bg-white/5 p-5">
              <h2 className="mb-4 text-sm font-bold uppercase tracking-wider text-white">
                Checkout
              </h2>

              {checkoutError && (
                <p className="mb-4 flex items-start gap-2 rounded-xl bg-red-500/10 p-3 text-sm text-red-300">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  {checkoutError}
                </p>
              )}

              {!preview ? (
                <button
                  type="button"
                  onClick={startCheckout}
                  disabled={checkoutBusy}
                  className="inline-flex items-center gap-2 rounded-xl bg-amber-400 px-5 py-2.5 text-sm font-semibold text-slate-900 hover:bg-amber-300 disabled:opacity-50"
                >
                  {checkoutBusy ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Coins className="h-4 w-4" />
                  )}
                  Review and pay
                </button>
              ) : (
                <div className="space-y-4">
                  {/*
                   * A changed price is the whole point of
                   * revalidation, so the old figure stays on screen
                   * beside the new one. Replacing it silently would
                   * hide the change section 6 exists to reveal.
                   */}
                  {preview.priceChanged && (
                    <p className="rounded-xl bg-amber-400/10 p-3 text-sm text-amber-200">
                      Prices changed while you were planning.
                      You were quoted{" "}
                      <span className="line-through opacity-70">
                        {money(
                          preview.quotedTotal,
                          preview.bill?.currency
                        )}
                      </span>
                    </p>
                  )}

                  {/*
                   * A line that failed revalidation is never
                   * silently substituted, and the checkout cannot
                   * proceed while one is outstanding.
                   */}
                  {(preview.changedLines ?? []).length > 0 && (
                    <div className="rounded-xl bg-red-500/10 p-3 text-sm text-red-300">
                      <p className="font-semibold">
                        Some items are no longer available
                      </p>
                      <ul className="mt-2 space-y-1">
                        {preview.changedLines.map((line) => (
                          <li key={line.selectionId}>
                            {line.label} — {line.reason}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  <div className="flex items-baseline justify-between">
                    <span className="text-sm text-white/60">
                      Total
                    </span>
                    <span className="text-xl font-bold text-white">
                      {money(
                        preview.revalidatedTotal,
                        preview.bill?.currency
                      )}
                    </span>
                  </div>

                  {!preview.requiresConfirmation &&
                    (preview.changedLines ?? []).length === 0 && (
                      <div className="flex flex-wrap gap-3">
                        <button
                          type="button"
                          onClick={() => acceptAndPay(true)}
                          disabled={checkoutBusy}
                          className="inline-flex items-center gap-2 rounded-xl bg-amber-400 px-5 py-2.5 text-sm font-semibold text-slate-900 hover:bg-amber-300 disabled:opacity-50"
                        >
                          {checkoutBusy ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <Coins className="h-4 w-4" />
                          )}
                          Pay now
                        </button>

                        <button
                          type="button"
                          onClick={() => acceptAndPay(false)}
                          disabled={checkoutBusy}
                          className="rounded-xl border border-white/20 px-4 py-2.5 text-sm text-white/70 hover:bg-white/5 disabled:opacity-50"
                        >
                          Simulate a decline
                        </button>
                      </div>
                    )}

                  {/*
                   * Recovery is not a decline. The money cleared and
                   * something still has to be put right, so it is
                   * worded differently and carries the reason the
                   * server recorded -- a traveller owed a fix should
                   * not be shown the same message as one whose card
                   * was rejected.
                   */}
                  {paid?.status === "RECOVERY_REQUIRED" && (
                    <div className="rounded-xl bg-red-500/10 p-3 text-sm text-red-300">
                      <p className="font-semibold">
                        Your payment went through, but part of
                        your trip could not be reserved
                      </p>
                      {paid.recoveryReason && (
                        <p className="mt-1 text-red-200/80">
                          {paid.recoveryReason}
                        </p>
                      )}
                      <p className="mt-2 text-red-200/60">
                        Our team is resolving this. You have not
                        been charged twice.
                      </p>
                    </div>
                  )}

                  {paid && paid.status !== "RECOVERY_REQUIRED" && (
                    <p
                      className={[
                        "flex items-center gap-2 rounded-xl p-3 text-sm",
                        paid.status === "PAID" ||
                        paid.status === "CONFIRMED"
                          ? "bg-emerald-500/10 text-emerald-300"
                          : "bg-amber-500/10 text-amber-200",
                      ].join(" ")}
                    >
                      {paid.status === "PAID" ||
                      paid.status === "CONFIRMED" ? (
                        <CheckCircle2 className="h-4 w-4 shrink-0" />
                      ) : (
                        <AlertTriangle className="h-4 w-4 shrink-0" />
                      )}
                      Payment {paid.status}.{" "}
                      {paid.status === "PAID" ||
                      paid.status === "CONFIRMED"
                        ? "Your trip is confirmed."
                        : "Nothing was charged."}
                    </p>
                  )}
                </div>
              )}
            </section>
          )}
        </div>

        {/* ============================================================
            TREASURE MAP
           ============================================================ */}
        <TreasureMap
          milestones={trip.milestones}
          className="lg:sticky lg:top-24 lg:self-start"
        />
      </div>
    </div>
  );
}

export default TripDetail;