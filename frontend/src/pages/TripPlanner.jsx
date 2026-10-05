import { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle,
  BedDouble,
  Check,
  ChevronRight,
  Loader2,
  MapPin,
  Plus,
  Sparkles,
  Ticket,
  Trash2,
} from "lucide-react";
import { useNavigate } from "react-router-dom";

import GuideStep from "../components/GuideStep";
import TreasureMap from "../components/TreasureMap";
import tripService from "../services/tripService";
import { useAuth } from "../context/useAuth";

/*
 * Planning a trip. (SRS 2.2 TP-01 to TP-08)
 *
 * One page, three steps, because the steps are strictly
 * dependent: a route needs a trip, picks need a route. Splitting
 * them across screens would mean three routes to learn and three
 * loading states to reconcile.
 *
 * The map is visible from the moment the route is saved, so the
 * traveller can see the checkpoints their choices are earning
 * rather than discovering them at the end.
 */

const STEPS = ["Trip", "Route", "Stay", "Guide & Ride"];

const SELECTION_ICON = {
  HOTEL: BedDouble,
  GUIDE: Sparkles,
  CAB: Ticket,
  ACTIVITY: MapPin,
};

function today() {
  return new Date().toISOString().slice(0, 10);
}

function addDays(iso, days) {
  const date = new Date(`${iso}T00:00:00`);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

function TripPlanner() {
  const navigate = useNavigate();
  const { user, loading: authLoading } = useAuth();

  const [step, setStep] = useState(0);
  const [trip, setTrip] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  /* ---------------------------------------------------------
     STEP 1: the trip itself
     --------------------------------------------------------- */
  const [title, setTitle] = useState("");
  const [startDate, setStartDate] = useState(addDays(today(), 30));
  const [endDate, setEndDate] = useState(addDays(today(), 36));
  const [cityCount, setCityCount] = useState(1);

  /* ---------------------------------------------------------
     STEP 2: the route
     --------------------------------------------------------- */
  const [cityChoices, setCityChoices] = useState([]);
  const [stops, setStops] = useState([]);

  /* ---------------------------------------------------------
     STEP 3: a room
     --------------------------------------------------------- */
  const [recommendations, setRecommendations] = useState([]);
  const [picking, setPicking] = useState(false);

  /* ---------------------------------------------------------
     STEP 4: a guide and a cab

     Loaded from the public listings for the stop's state, not
     from a recommender that does not exist yet. Offering the real
     verified guides is honest; inventing a ranking the backend
     cannot justify would not be.
     --------------------------------------------------------- */
  const [guides, setGuides] = useState([]);
  const [cabs, setCabs] = useState([]);
  const [extrasBusy, setExtrasBusy] = useState(false);

  useEffect(() => {
    if (!authLoading && !user) {
      navigate("/login");
    }
  }, [authLoading, user, navigate]);

  /*
   * Cities come from home discovery, not from the planner's
   * route-suggestion endpoint. That endpoint advises on the
   * order of stops the traveller ALREADY has (TP-03), so with
   * no route set it has nothing to say and returns no options.
   *
   * A failure here is not fatal. The traveller can still finish
   * the trip, and saying so beats blocking the page on a
   * convenience.
   */
  const loadCities = useCallback(async () => {
    try {
      const data = await tripService.getCities();
      setCityChoices(Array.isArray(data) ? data : []);
    } catch {
      setCityChoices([]);
    }
  }, []);

  const createTrip = async () => {
    if (!title.trim()) {
      setError("Give the trip a name.");
      return;
    }

    if (endDate <= startDate) {
      setError(
        "The end date must be after the start date."
      );
      return;
    }

    setBusy(true);
    setError("");

    try {
      const created = await tripService.createTrip({
        title: title.trim(),
        startDate,
        endDate,
        plannedCityCount: cityCount,
        currency: "INR",
      });

      setTrip(created);
      setStep(1);
      await loadCities();
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to create the trip."
      );
    } finally {
      setBusy(false);
    }
  };

  const addStop = (city) => {
    if (stops.length >= cityCount) {
      return;
    }

    if (stops.some((s) => s.cityId === city.cityId)) {
      return;
    }

    const last = stops[stops.length - 1];

    setStops((current) => [
      ...current,
      {
        cityId: city.cityId,
        name: city.name,
        arrivalDate: last
          ? last.departureDate
          : startDate,
        departureDate: last
          ? addDays(last.departureDate, 1)
          : addDays(startDate, 1),
      },
    ]);
  };

  const removeStop = (index) => {
    setStops((current) =>
      current.filter((_, i) => i !== index)
    );
  };

  const saveRoute = async () => {
    setBusy(true);
    setError("");

    try {
      const saved = await tripService.setRoute(
        trip.tripId,
        stops.map((stop) => ({
          cityId: stop.cityId,
          arrivalDate: stop.arrivalDate,
          departureDate: stop.departureDate,
        }))
      );

      setTrip(saved);
      setStep(2);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to save the route."
      );
    } finally {
      setBusy(false);
    }
  };

  /**
   * Recomputed rather than fetched, because availability and
   * prices move under a stored ranking.
   */
  const findHotels = async () => {
    setPicking(true);
    setError("");

    try {
      const updated =
        await tripService.recomputeRecommendations(
          trip.tripId
        );

      /*
       * The endpoint answers with the whole trip, so the
       * recommendations live on it. Reading the response as an
       * array is what made this screen claim nothing was
       * available when it simply had not looked in the right
       * place.
       */
      setTrip(updated);
      setRecommendations(
        Array.isArray(updated?.recommendations)
          ? updated.recommendations
          : []
      );
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to look for rooms right now."
      );
    } finally {
      setPicking(false);
    }
  };

  useEffect(() => {
    if (step === 2 && recommendations.length === 0) {
      findHotels();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step]);

  /* Loads guides and cabs for the first stop's state.

     A trip can span several states. Only the first stop is offered
     here rather than merging every state's listings, because a
     guide in a city three days drive away is not local to the trip
     and saying otherwise would be a claim the map cannot support. */
  const loadExtras = useCallback(async () => {
    const first = (trip?.cities ?? [])[0];

    if (!first?.stateId) {
      setGuides([]);
      setCabs([]);
      return;
    }

    setExtrasBusy(true);

    try {
      const [g, c] = await Promise.all([
        tripService.getGuidesForState(first.stateId),
        tripService.getCabsForState(first.stateId),
      ]);

      setGuides(g);
      setCabs(c);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to look for guides and transport right now."
      );
    } finally {
      setExtrasBusy(false);
    }
  }, [trip]);

  useEffect(() => {
    if (step === 3) {
      loadExtras();
    }
  }, [step, loadExtras]);

  const addExtra = async (type, targetId, dates) => {
    setBusy(true);
    setError("");

    const stop = (trip?.cities ?? [])[0];

    try {
      const saved = await tripService.addSelection(
        trip.tripId,
        {
          selectionType: type,
          tripCityId: stop?.tripCityId,
          targetId,
          checkIn: dates.checkIn,
          checkOut: dates.checkOut,
          guests: 2,
          rooms: 1,
          currency: "INR",
        }
      );

      setTrip(saved);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to add that to your trip."
      );
    } finally {
      setBusy(false);
    }
  };

  const pick = async (recommendation) => {
    setBusy(true);
    setError("");

    /*
     * The stay's dates come from the stop the hotel is actually in,
     * resolved through tripCityId.
     *
     * <p>Two things were wrong before. The lookup matched
     * `s.cityId === stops[0]?.cityId`, which always matched the
     * FIRST stop, so a hotel in the third city was quoted the first
     * city's arrival. And it read the local `stops` state rather
     * than the saved route, so unsaved edits would have been billed.
     *
     * <p>The server's own city list is the authority on the dates
     * that were actually stored.
     */
    const stop = (trip?.cities ?? []).find(
      (c) => c.tripCityId === recommendation.tripCityId
    );

    try {
      const saved = await tripService.addSelection(
        trip.tripId,
        {
          selectionType: "HOTEL",
          tripCityId: recommendation.tripCityId,
          targetId: recommendation.targetId,
          roomTypeId: recommendation.roomTypeId,
          checkIn: stop?.arrivalDate,
          checkOut: stop?.departureDate,
          guests: 2,
          rooms: 1,
          currency: recommendation.currency,
        }
      );

      setTrip(saved);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to add that room."
      );
    } finally {
      setBusy(false);
    }
  };

  const live = recommendations.filter(
    (r) => r.recommended
  );
  const rejected = recommendations.filter(
    (r) => !r.recommended
  );

  const selections = trip?.selections ?? [];

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <h1 className="text-2xl font-bold text-white sm:text-3xl">
        Plan a trip
      </h1>

      {/* ==========================================================
          STEPS
         ========================================================== */}
      <ol className="mt-6 flex flex-wrap gap-2">
        {STEPS.map((label, index) => (
          <li
            key={label}
            className={[
              "flex items-center gap-2 rounded-full px-4 py-1.5",
              "text-xs font-semibold uppercase tracking-wider",
              index === step
                ? "bg-amber-400 text-slate-900"
                : index < step
                  ? "bg-emerald-500/20 text-emerald-200"
                  : "bg-white/10 text-white/40",
            ].join(" ")}
          >
            {index < step ? (
              <Check className="h-3 w-3" />
            ) : (
              index + 1
            )}
            {label}
          </li>
        ))}
      </ol>

      {error && (
        <div className="mt-6 flex items-center gap-2 rounded-xl border border-amber-400/30 bg-amber-400/10 px-4 py-3 text-sm text-amber-200">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_360px]">
        <div>
          {/* ======================================================
              STEP 1
             ====================================================== */}
          {step === 0 && (
            <section className="rounded-2xl border border-white/10 bg-white/5 p-6">
              <label className="block text-sm font-medium text-white">
                Trip name
                <input
                  value={title}
                  onChange={(e) =>
                    setTitle(e.target.value)
                  }
                  placeholder="Konark Pilgrimage"
                  className="mt-2 w-full rounded-xl border border-white/15 bg-slate-950 px-4 py-2.5 text-white outline-none focus:border-amber-300"
                />
              </label>

              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <label className="block text-sm font-medium text-white">
                  Leaving
                  <input
                    type="date"
                    value={startDate}
                    onChange={(e) =>
                      setStartDate(e.target.value)
                    }
                    className="mt-2 w-full rounded-xl border border-white/15 bg-slate-950 px-4 py-2.5 text-white outline-none focus:border-amber-300"
                  />
                </label>

                <label className="block text-sm font-medium text-white">
                  Returning
                  <input
                    type="date"
                    value={endDate}
                    min={addDays(startDate, 1)}
                    onChange={(e) =>
                      setEndDate(e.target.value)
                    }
                    className="mt-2 w-full rounded-xl border border-white/15 bg-slate-950 px-4 py-2.5 text-white outline-none focus:border-amber-300"
                  />
                </label>
              </div>

              <label className="mt-4 block text-sm font-medium text-white">
                How many cities?
                <input
                  type="number"
                  min={1}
                  max={15}
                  value={cityCount}
                  onChange={(e) =>
                    setCityCount(
                      Math.max(
                        1,
                        Math.min(
                          15,
                          Number(e.target.value) || 1
                        )
                      )
                    )
                  }
                  className="mt-2 w-full rounded-xl border border-white/15 bg-slate-950 px-4 py-2.5 text-white outline-none focus:border-amber-300"
                />
              </label>

              <button
                type="button"
                onClick={createTrip}
                disabled={busy}
                className="mt-6 inline-flex items-center gap-2 rounded-xl bg-amber-400 px-5 py-2.5 text-sm font-semibold text-slate-900 hover:bg-amber-300 disabled:opacity-50"
              >
                {busy ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <ChevronRight className="h-4 w-4" />
                )}
                Next: choose cities
              </button>
            </section>
          )}

          {/* ======================================================
              STEP 2
             ====================================================== */}
          {step === 1 && (
            <section className="rounded-2xl border border-white/10 bg-white/5 p-6">
              <h2 className="text-sm font-bold uppercase tracking-wider text-white">
                Your route
              </h2>

              {stops.length > 0 && (
                <ol className="mt-4 space-y-2">
                  {stops.map((stop, index) => (
                    <li
                      key={stop.cityId}
                      className="flex items-center gap-3 rounded-xl bg-white/5 px-4 py-3"
                    >
                      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white/10 text-xs font-semibold text-white/70">
                        {index + 1}
                      </span>

                      <div className="min-w-0 flex-1">
                        <p className="font-medium text-white">
                          {stop.name}
                        </p>
                        <p className="text-xs text-white/50">
                          {stop.arrivalDate} to{" "}
                          {stop.departureDate}
                        </p>
                      </div>

                      <button
                        type="button"
                        onClick={() =>
                          removeStop(index)
                        }
                        className="text-white/40 hover:text-red-300"
                        aria-label={`Remove ${stop.name}`}
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </li>
                  ))}
                </ol>
              )}

              <h3 className="mt-6 text-xs font-semibold uppercase tracking-wider text-white/50">
                Cities
              </h3>

              {cityChoices.length === 0 ? (
                <p className="mt-3 text-sm text-white/50">
                  No cities are available to plan with right
                  now.
                </p>
              ) : (
                <ul className="mt-3 grid gap-2 sm:grid-cols-2">
                  {cityChoices.map((city) => {
                    const chosen = stops.some(
                      (s) => s.cityId === city.cityId
                    );

                    return (
                      <li key={city.cityId}>
                        <button
                          type="button"
                          onClick={() => addStop(city)}
                          disabled={chosen}
                          className="flex w-full items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-left transition hover:border-amber-300/40 disabled:opacity-40"
                        >
                          <MapPin className="h-4 w-4 shrink-0 text-amber-300" />
                          <div className="min-w-0">
                            <p className="truncate font-medium text-white">
                              {city.name}
                            </p>
                            <p className="text-xs text-white/50">
                              {city.stateName}
                              {city.reason
                                ? ` · ${city.reason}`
                                : ""}
                            </p>
                          </div>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}

              <button
                type="button"
                onClick={saveRoute}
                disabled={busy || stops.length === 0}
                className="mt-6 inline-flex items-center gap-2 rounded-xl bg-amber-400 px-5 py-2.5 text-sm font-semibold text-slate-900 hover:bg-amber-300 disabled:opacity-50"
              >
                {busy ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <ChevronRight className="h-4 w-4" />
                )}
                Next: find a place to stay
              </button>
            </section>
          )}

          {/* ======================================================
              STEP 3
             ====================================================== */}
          {step === 2 && (
            <section className="rounded-2xl border border-white/10 bg-white/5 p-6">
              <h2 className="text-sm font-bold uppercase tracking-wider text-white">
                Places to stay
              </h2>

              {selections.length > 0 && (
                <ul className="mt-4 space-y-2">
                  {selections.map((selection) => {
                    const Icon =
                      SELECTION_ICON[
                        selection.selectionType
                      ] ?? MapPin;

                    return (
                      <li
                        key={selection.selectionId}
                        className="flex items-center gap-3 rounded-xl bg-white/5 px-4 py-3"
                      >
                        <Icon className="h-4 w-4 shrink-0 text-amber-300" />

                        {/*
                         * A pick with no name is unknown, so
                         * nothing is rendered in its place. The
                         * backend used to send the literal
                         * "Item 7" here.
                         */}
                        <p className="min-w-0 flex-1 truncate font-medium text-white">
                          {selection.targetName ??
                            "This pick"}
                        </p>

                        {selection.quotedAmount != null && (
                          <span className="shrink-0 text-sm text-white/70">
                            {selection.currency}{" "}
                            {Number(
                              selection.quotedAmount
                            ).toFixed(0)}
                          </span>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}

              {picking ? (
                <div className="mt-6 flex items-center gap-2 text-sm text-white/60">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Checking availability for your dates...
                </div>
              ) : live.length === 0 ? (
                <p className="mt-6 text-sm text-white/50">
                  Nothing is available for every night
                  of your stay right now.
                </p>
              ) : (
                <ul className="mt-6 space-y-3">
                  {live.map((rec) => (
                    <li
                      key={rec.recommendationId}
                      className="rounded-xl border border-white/10 bg-white/5 p-4"
                    >
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="font-semibold text-white">
                            {rec.title}
                          </p>
                          <p className="mt-0.5 text-sm text-white/60">
                            {rec.cityName}
                            {rec.distanceKm != null
                              ? ` · ${Number(
                                  rec.distanceKm
                                ).toFixed(1)} km away`
                              : ""}
                          </p>
                        </div>

                        {rec.quotedAmount != null && (
                          <span className="shrink-0 font-semibold text-amber-300">
                            {rec.currency}{" "}
                            {Number(
                              rec.quotedAmount
                            ).toFixed(0)}
                          </span>
                        )}
                      </div>

                      {/*
                       * The reason is required by SRS 4.1 and is
                       * the backend's own words, so it is shown
                       * verbatim rather than summarised.
                       */}
                      {rec.reason && (
                        <p className="mt-2 text-xs text-white/50">
                          {rec.reason}
                        </p>
                      )}

                      <button
                        type="button"
                        onClick={() => pick(rec)}
                        disabled={busy}
                        className="mt-3 inline-flex items-center gap-2 rounded-lg border border-amber-300/40 px-3 py-1.5 text-xs font-semibold text-amber-300 hover:bg-amber-300/10 disabled:opacity-50"
                      >
                        <Plus className="h-3 w-3" />
                        Add this stay
                      </button>
                    </li>
                  ))}
                </ul>
              )}

              {/*
               * Rejected candidates are shown, not hidden. A
               * property that was considered and turned down
               * for lack of availability is a fact the
               * traveller is entitled to, and hiding it makes
               * the list look arbitrary.
               */}
              {rejected.length > 0 && (
                <details className="mt-6">
                  <summary className="cursor-pointer text-xs text-white/40">
                    {rejected.length} considered but not
                    available
                  </summary>

                  <ul className="mt-2 space-y-1">
                    {rejected.map((rec) => (
                      <li
                        key={rec.recommendationId}
                        className="text-xs text-white/40"
                      >
                        {rec.title}
                        {rec.rejectionReason
                          ? ` — ${rec.rejectionReason}`
                          : ""}
                      </li>
                    ))}
                  </ul>
                </details>
              )}

              <button
                type="button"
                onClick={() => setStep(3)}
                className="mt-6 inline-flex items-center gap-2 rounded-xl bg-amber-400 px-5 py-2.5 text-sm font-semibold text-slate-900 hover:bg-amber-300"
              >
                Add a guide and transport
                <ChevronRight className="h-4 w-4" />
              </button>
            </section>
          )}

          {/* ======================================================
              STEP 4: A GUIDE AND A RIDE
             ====================================================== */}
          {step === 3 && (
            <GuideStep
              trip={trip}
              guides={guides}
              cabs={cabs}
              busy={busy || extrasBusy}
              onAdd={addExtra}
              onReview={() => navigate(`/trips/${trip.tripId}`)}
            />
          )}
        </div>

        {/* ==========================================================
            LIVE MAP
           ========================================================== */}
        <TreasureMap
          milestones={trip?.milestones}
          className="lg:sticky lg:top-24 lg:self-start"
        />
      </div>
    </div>
  );
}

export default TripPlanner;