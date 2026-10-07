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

import ReviewCenter from "../components/ReviewCenter";
import TreasureMap from "../components/TreasureMap";
import RazorpayPayment from "../components/payment/RazorpayPayment";
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

const mockPaymentsEnabled =
  import.meta.env.VITE_MOCK_PAYMENTS === "true";

function money(amount, currency) {
  if (amount === null || amount === undefined) {
    return null;
  }
  return `${currency ?? "INR"} ${Number(amount).toFixed(2)}`;
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
  const [checkoutHistory, setCheckoutHistory] = useState([]);
  const [checkoutHistoryLoading, setCheckoutHistoryLoading] = useState(true);
  const [checkoutHistoryError, setCheckoutHistoryError] = useState("");
  const [dayAllocation, setDayAllocation] = useState(null);
  const [allocationBusy, setAllocationBusy] = useState(false);
  const [allocationError, setAllocationError] = useState("");
  const [tripHealth, setTripHealth] = useState(null);
  const [healthBusy, setHealthBusy] = useState(false);
  const [healthError, setHealthError] = useState("");

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

  const loadCheckoutHistory = useCallback(async () => {
    setCheckoutHistoryLoading(true);
    setCheckoutHistoryError("");
    try {
      const history = await tripService.getCheckoutHistory(tripId);
      setCheckoutHistory(Array.isArray(history) ? history : []);
    } catch (err) {
      setCheckoutHistoryError(
        err?.response?.data?.message ||
          "Unable to verify this trip's checkout status."
      );
    } finally {
      setCheckoutHistoryLoading(false);
    }
  }, [tripId]);

  useEffect(() => {
    loadCheckoutHistory();
  }, [loadCheckoutHistory]);

  const startCheckout = useCallback(async () => {
    setCheckoutBusy(true);
    setCheckoutError("");

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

  const loadDayAllocation = useCallback(async () => {
    setAllocationBusy(true);
    setAllocationError("");
    try {
      setDayAllocation(await tripService.getDayAllocation(tripId));
    } catch (err) {
      setAllocationError(
        err?.response?.data?.message ||
          "Unable to calculate a suggested stay allocation."
      );
    } finally {
      setAllocationBusy(false);
    }
  }, [tripId]);

  const loadTripHealth = useCallback(async () => {
    setHealthBusy(true);
    setHealthError("");
    try {
      setTripHealth(await tripService.getTripHealth(tripId));
    } catch (err) {
      setHealthError(
        err?.response?.data?.message ||
          "Unable to check itinerary feasibility."
      );
    } finally {
      setHealthBusy(false);
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
  const acceptCheckout = useCallback(
    async () => {
      if (!preview) {
        return;
      }

      setCheckoutBusy(true);
      setCheckoutError("");

      try {
        const checkout = await tripService.confirmCheckout(
          tripId,
          preview.checkoutId,
          preview.revalidatedTotal,
          preview.bill?.currency
        );

        setCheckoutHistory((history) =>
          [checkout, ...history.filter(
            (item) => item.checkoutId !== checkout.checkoutId
          )].sort((left, right) => right.checkoutId - left.checkoutId)
        );
      } catch (err) {
        await loadCheckoutHistory();
        setCheckoutError(
          err?.response?.data?.message ||
            "Payment could not be completed."
        );
      } finally {
        setCheckoutBusy(false);
      }
    },
    [loadCheckoutHistory, preview, tripId]
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
  const latestCheckout = checkoutHistory[0] ?? null;
  const pendingCheckout =
    latestCheckout?.status === "PAYMENT_PENDING"
      ? latestCheckout
      : null;
  const persistedPaymentOutcome =
    latestCheckout &&
    ["PAID", "CONFIRMED", "RECOVERY_REQUIRED"].includes(
      latestCheckout.status
    )
      ? latestCheckout
      : null;
  const persistedPaymentFailure =
    latestCheckout?.status === "FAILED" ? latestCheckout : null;

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
    !checkoutHistoryLoading &&
    !checkoutHistoryError &&
    !pendingCheckout &&
    !persistedPaymentOutcome;

  async function runMockPayment(paymentSuccessful) {
    if (!pendingCheckout) return;
    setCheckoutBusy(true);
    setCheckoutError("");
    try {
      const result = await tripService.payMockCheckout(
        tripId,
        pendingCheckout.checkoutId,
        paymentSuccessful
      );
      setCheckoutHistory((history) =>
        [result, ...history.filter(
          (item) => item.checkoutId !== result.checkoutId
        )].sort((left, right) => right.checkoutId - left.checkoutId)
      );
      if (result.status === "FAILED") {
        setPreview(null);
      } else {
        await load();
      }
    } catch (err) {
      setCheckoutError(
        err?.response?.data?.message ||
          "The mock payment could not be processed."
      );
    } finally {
      setCheckoutBusy(false);
    }
  }

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

          <section className="rounded-2xl border border-white/10 bg-white/5 p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-bold uppercase tracking-wider text-white">
                  Itinerary health
                </h2>
                <p className="mt-1 text-xs text-white/50">
                  Checks dates, route gaps, unavailable picks and budget. Advice only; your plan will not be changed.
                </p>
              </div>
              <button
                type="button"
                onClick={loadTripHealth}
                disabled={healthBusy}
                className="inline-flex items-center gap-2 rounded-xl border border-white/15 px-4 py-2 text-sm text-white hover:bg-white/10 disabled:opacity-50"
              >
                {healthBusy ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Sparkles className="h-4 w-4" />
                )}
                {tripHealth ? "Refresh checks" : "Check itinerary"}
              </button>
            </div>
            {healthError && (
              <p role="alert" className="mt-3 rounded-xl bg-red-500/10 p-3 text-sm text-red-300">
                {healthError}
              </p>
            )}
            {tripHealth && (
              <div className="mt-4 space-y-3">
                <p
                  className={[
                    "inline-flex rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-wider",
                    tripHealth.status === "NEEDS_ATTENTION"
                      ? "bg-amber-400/10 text-amber-200"
                      : "bg-emerald-500/10 text-emerald-200",
                  ].join(" ")}
                >
                  {tripHealth.status === "NEEDS_ATTENTION"
                    ? "Needs attention"
                    : "On track"}
                </p>
                {tripHealth.issues?.length === 0 ? (
                  <p className="text-sm text-emerald-200">
                    No date, budget, or selected-service conflicts found.
                  </p>
                ) : (
                  <ul className="space-y-2">
                    {tripHealth.issues?.map((issue, index) => (
                      <li
                        key={`${issue.code}-${issue.tripCityId ?? ""}-${issue.selectionId ?? index}`}
                        className={[
                          "rounded-xl p-3 text-sm",
                          issue.severity === "BLOCKER"
                            ? "bg-red-500/10 text-red-200"
                            : issue.severity === "WARNING"
                              ? "bg-amber-400/10 text-amber-100"
                              : "bg-white/5 text-white/70",
                        ].join(" ")}
                      >
                        <span className="mr-2 text-[10px] font-bold uppercase tracking-wider opacity-70">
                          {issue.severity}
                        </span>
                        {issue.message}
                      </li>
                    ))}
                  </ul>
                )}
                {tripHealth.budget != null && (
                  <p className="text-xs text-white/50">
                    Current estimate: {money(tripHealth.estimatedTotal, tripHealth.currency)}
                    {" · "}Budget: {money(
                      tripHealth.budget,
                      tripHealth.budgetCurrency || tripHealth.currency
                    )}
                  </p>
                )}
              </div>
            )}
          </section>

          <section className="rounded-2xl border border-white/10 bg-white/5 p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-bold uppercase tracking-wider text-white">
                  Suggested city stays
                </h2>
                <p className="mt-1 text-xs text-white/50">
                  Advisory only; your saved itinerary will not be changed.
                </p>
              </div>
              {!dayAllocation && (
                <button
                  type="button"
                  onClick={loadDayAllocation}
                  disabled={allocationBusy || cities.length === 0}
                  className="inline-flex items-center gap-2 rounded-xl border border-white/15 px-4 py-2 text-sm text-white hover:bg-white/10 disabled:opacity-50"
                >
                  {allocationBusy ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Sparkles className="h-4 w-4" />
                  )}
                  Suggest allocation
                </button>
              )}
            </div>
            {allocationError && (
              <p className="mt-3 rounded-xl bg-red-500/10 p-3 text-sm text-red-300">
                {allocationError}
              </p>
            )}
            {dayAllocation && (
              <div className="mt-4 space-y-3">
                {dayAllocation.notes?.map((note) => (
                  <p
                    key={note}
                    className="rounded-xl bg-amber-400/10 p-3 text-sm text-amber-200"
                  >
                    {note}
                  </p>
                ))}
                {dayAllocation.cityAllocations?.map((allocation) => (
                  <div
                    key={allocation.tripCityId}
                    className="flex flex-wrap items-start justify-between gap-3 rounded-xl bg-white/5 p-3"
                  >
                    <div>
                      <p className="font-medium text-white">
                        {allocation.cityName}
                      </p>
                      <p className="mt-1 text-xs text-white/50">
                        {allocation.suggestedArrivalDate} to{" "}
                        {allocation.suggestedDepartureDate}
                      </p>
                      <p className="mt-1 text-xs text-white/40">
                        {allocation.rationale}
                      </p>
                    </div>
                    <p className="text-sm font-semibold text-amber-200">
                      {allocation.suggestedNights}{" "}
                      {allocation.suggestedNights === 1 ? "night" : "nights"}
                    </p>
                  </div>
                ))}
                <button
                  type="button"
                  onClick={loadDayAllocation}
                  disabled={allocationBusy}
                  className="text-xs text-white/50 underline hover:text-white disabled:opacity-50"
                >
                  Recalculate suggestion
                </button>
              </div>
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

          {checkoutHistoryError && (
            <section className="rounded-2xl border border-amber-300/20 bg-amber-400/10 p-5 text-sm text-amber-100">
              <p>{checkoutHistoryError} New payment is disabled until the status can be checked.</p>
              <button
                type="button"
                onClick={loadCheckoutHistory}
                disabled={checkoutHistoryLoading}
                className="mt-3 underline disabled:opacity-50"
              >
                Retry status check
              </button>
            </section>
          )}

          {/* ==========================================================
              CHECKOUT
             ========================================================== */}
          {/*
           * A declined payment is the normal case, not an error
           * case, so it is offered as an explicit choice. A single
           * "Pay" button would make testing a decline impossible and
           * would hide the fact that the payment is simulated.
           */}
          {(canCheckout || pendingCheckout) && (
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

              {persistedPaymentFailure && !pendingCheckout && (
                <p className="mb-4 rounded-xl bg-amber-400/10 p-3 text-sm text-amber-200">
                  The previous payment was not completed. No payment was captured; review the trip total to start a new checkout.
                </p>
              )}

              {pendingCheckout ? (
                <>
                  <p className="mb-4 text-sm text-white/70">
                    A payment session is already open for this trip. Continue with that session; do not start another checkout.
                  </p>
                  {mockPaymentsEnabled ? (
                    <div className="space-y-3">
                      <p className="rounded-xl border border-amber-300/20 bg-amber-400/10 p-3 text-xs text-amber-100">
                        Development mock payment is enabled. It does not process real money.
                      </p>
                      <div className="flex flex-wrap gap-3">
                        <button
                          type="button"
                          onClick={() => runMockPayment(false)}
                          disabled={checkoutBusy}
                          className="rounded-xl border border-white/15 px-4 py-2.5 text-sm text-white disabled:opacity-50"
                        >
                          Simulate decline
                        </button>
                        <button
                          type="button"
                          onClick={() => runMockPayment(true)}
                          disabled={checkoutBusy}
                          className="rounded-xl bg-amber-400 px-4 py-2.5 text-sm font-semibold text-slate-900 disabled:opacity-50"
                        >
                          {checkoutBusy ? "Processing..." : "Simulate successful payment"}
                        </button>
                      </div>
                    </div>
                  ) : (
                    <RazorpayPayment
                      tripId={Number(tripId)}
                      checkoutId={pendingCheckout.checkoutId}
                      paymentAmountMinor={Math.round(
                        Number(pendingCheckout.totalAmount) * 100
                      )}
                      booking={{ currency: pendingCheckout.currency }}
                      onSuccess={(result) => {
                        setCheckoutHistory((history) =>
                          [result, ...history.filter(
                            (item) => item.checkoutId !== result.checkoutId
                          )].sort((left, right) => right.checkoutId - left.checkoutId)
                        );
                        load();
                      }}
                      onFailure={async (message) => {
                        setCheckoutError(message);
                        await loadCheckoutHistory();
                      }}
                      onCancel={async (message) => {
                        setCheckoutError(message);
                        await loadCheckoutHistory();
                      }}
                    />
                  )}
                </>
              ) : !preview ? (
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

                  {(preview.changedLines ?? []).length === 0 &&
                    (
                      <div className="flex flex-wrap gap-3">
                        <button
                          type="button"
                          onClick={acceptCheckout}
                          disabled={checkoutBusy}
                          className="inline-flex items-center gap-2 rounded-xl bg-amber-400 px-5 py-2.5 text-sm font-semibold text-slate-900 hover:bg-amber-300 disabled:opacity-50"
                        >
                          {checkoutBusy ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <Coins className="h-4 w-4" />
                          )}
                          {preview.requiresConfirmation
                            ? "Accept updated total"
                            : "Continue to secure payment"}
                        </button>
                      </div>
                    )}
                </div>
              )}
            </section>
          )}

          {persistedPaymentOutcome && (
            <section
              className={[
                "rounded-2xl border p-5 text-sm",
                persistedPaymentOutcome.status === "RECOVERY_REQUIRED"
                  ? "border-red-300/20 bg-red-500/10 text-red-200"
                  : "border-emerald-300/20 bg-emerald-500/10 text-emerald-200",
              ].join(" ")}
            >
              {persistedPaymentOutcome.status === "RECOVERY_REQUIRED" ? (
                <>
                  <p className="font-semibold">
                    Payment received; reservation recovery is in progress.
                  </p>
                  {persistedPaymentOutcome.recoveryReason && (
                    <p className="mt-2">{persistedPaymentOutcome.recoveryReason}</p>
                  )}
                  <p className="mt-2">
                    Do not pay again. This checkout will remain visible here while our team resolves it.
                  </p>
                </>
              ) : persistedPaymentOutcome.status === "PAID" ? (
                <>
                  <p className="font-semibold">Payment received; trip confirmation is being finalized.</p>
                  <p className="mt-2">No new payment is needed while this checkout is being reconciled.</p>
                </>
              ) : (
                <p className="flex items-center gap-2 font-semibold">
                  <CheckCircle2 className="h-4 w-4 shrink-0" />
                  Payment confirmed. Your trip is booked.
                </p>
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

      {/*
       * The Review Center. (SRS 2.3 TP-12)

       * Self-gating: it asks the backend what is reviewable and
       * renders nothing until the server says the window is open, so
       * this line needs no conditional of its own.
       */}
      <ReviewCenter tripId={tripId} />
    </div>
  );
}

export default TripDetail;