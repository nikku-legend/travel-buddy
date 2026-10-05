import { ChevronRight } from "lucide-react";

/*
 * Step 4 of the planner: a guide and a ride. (SRS 2.2 TP-05)
 *
 * Split into its own file because the planner was already three
 * screens in one component, and this one is optional -- a traveller
 * with a friend in Puri needs neither.
 *
 * Nothing here decides anything. The prices shown are the ones the
 * backend returned, and the dates come from the saved route, so the
 * figure on this screen is the figure that will be revalidated at
 * checkout rather than a second, local estimate.
 */
function GuideStep({
  trip,
  guides,
  cabs,
  busy,
  onAdd,
  onReview,
}) {
  if (!trip?.cities?.length) {
    return (
      <section className="rounded-2xl border border-white/10 bg-white/5 p-6">
        <h2 className="text-sm font-bold uppercase tracking-wider text-white">
          A guide and a ride
        </h2>
        <p className="mt-4 text-sm text-amber-300">
          Choose a route first, so we know which city you need a
          guide in.
        </p>
      </section>
    );
  }

  /* The saved route is the authority on dates, never local
     editing state: an unsaved change would be quoted for a night
     the traveller is not actually staying. */
  const stop = trip.cities[0];
  const dates = {
    checkIn: stop.arrivalDate,
    checkOut: stop.departureDate,
  };

  const alreadyPicked = (type, id) =>
    (trip.selections ?? []).some(
      (s) =>
        s.selectionType === type &&
        s.targetId === id &&
        s.status !== "REMOVED"
    );

  return (
    <section className="rounded-2xl border border-white/10 bg-white/5 p-6">
      <h2 className="text-sm font-bold uppercase tracking-wider text-white">
        A guide and a ride
      </h2>

      <p className="mt-2 text-sm text-white/50">
        Optional. Both are reserved the moment you pay, so a
        guide who is already taken is refused before any money
        moves.
      </p>

      <div className="mt-5">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-white/60">
          Guides in {stop.cityName}
        </h3>

        {guides.length === 0 ? (
          <p className="mt-3 text-sm text-white/40">
            No verified guides are listed in this state yet.
          </p>
        ) : (
          <ul className="mt-3 space-y-2">
            {guides.map((guide) => {
              const added = alreadyPicked("GUIDE", guide.guideId);

              return (
                <li
                  key={guide.guideId}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-white/10 p-3"
                >
                  <div>
                    <p className="text-sm font-semibold text-white">
                      {guide.fullName ||
                        guide.name ||
                        "Local guide"}
                    </p>
                    <p className="text-xs text-white/50">
                      {guide.yearsOfExperience} yrs experience ·{" "}
                      {guide.dailyRate} {guide.currencyCode}/day
                    </p>
                  </div>

                  <button
                    type="button"
                    disabled={busy || added}
                    onClick={() =>
                      onAdd("GUIDE", guide.guideId, dates)
                    }
                    className="rounded-lg border border-amber-300/40 px-3 py-1.5 text-xs font-semibold text-amber-300 hover:bg-amber-300/10 disabled:opacity-50"
                  >
                    {added ? "Added" : "Add"}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <div className="mt-6">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-white/60">
          Transport
        </h3>

        {cabs.length === 0 ? (
          <p className="mt-3 text-sm text-white/40">
            No vehicles are available in this state yet.
          </p>
        ) : (
          <ul className="mt-3 space-y-2">
            {cabs.map((cab) => {
              const added = alreadyPicked("CAB", cab.cabId);

              return (
                <li
                  key={cab.cabId}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-white/10 p-3"
                >
                  <div>
                    <p className="text-sm font-semibold text-white">
                      {cab.vehicleName}
                    </p>
                    <p className="text-xs text-white/50">
                      {cab.vehicleType} · seats{" "}
                      {cab.seatingCapacity} · from {cab.baseFare}
                    </p>
                  </div>

                  <button
                    type="button"
                    disabled={busy || added}
                    onClick={() => onAdd("CAB", cab.cabId, dates)}
                    className="rounded-lg border border-amber-300/40 px-3 py-1.5 text-xs font-semibold text-amber-300 hover:bg-amber-300/10 disabled:opacity-50"
                  >
                    {added ? "Added" : "Add"}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <button
        type="button"
        onClick={onReview}
        className="mt-6 inline-flex items-center gap-2 rounded-xl bg-amber-400 px-5 py-2.5 text-sm font-semibold text-slate-900 hover:bg-amber-300"
      >
        Review the trip
        <ChevronRight className="h-4 w-4" />
      </button>
    </section>
  );
}

export default GuideStep;