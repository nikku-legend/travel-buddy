import {
  Check,
  Flag,
  LogIn,
  LogOut,
  MapPin,
} from "lucide-react";

/*
 * The treasure map. (SRS 2.2 TP-11)
 *
 * Renders the checkpoints the planner returns. Every pin is
 * either reached or not, and nothing here decides that: the
 * backend only marks a checkpoint when a real event happened,
 * such as a payment clearing or a hotel checking a paid guest
 * in. This component's whole job is not to contradict that.
 *
 * In particular it must never show a completed pin on the
 * strength of its own optimism, and it must never show progress
 * for a trip that has not happened yet.
 */

/* Icon per checkpoint, so a glance reads as a journey. */
const ICONS = {
  TRIP_STARTED: Flag,
  CITY_ARRIVED: MapPin,
  CHECKED_IN: LogIn,
  CHECKED_OUT: LogOut,
  CITY_DEPARTED: MapPin,
  TRIP_COMPLETED: Flag,
  REVIEW_OPENED: Check,
};

const LABELS = {
  TRIP_STARTED: "Start",
  CITY_ARRIVED: "Arrive",
  CHECKED_IN: "Check in",
  CHECKED_OUT: "Check out",
  CITY_DEPARTED: "Depart",
  TRIP_COMPLETED: "Finish",
  REVIEW_OPENED: "Review",
};

function Pin({ milestone }) {
  const Icon =
    ICONS[milestone.milestoneType] ?? MapPin;
  const reached = milestone.completed;

  return (
    <li className="relative flex gap-4">
      {/*
       * The connector is drawn behind the pin so the line does
       * not shift when a checkpoint is ticked.
       */}
      <div className="flex flex-col items-center">
        <div
          className={[
            "flex h-9 w-9 shrink-0 items-center",
            "justify-center rounded-full border-2",
            reached
              ? "border-amber-400 bg-amber-400/20 text-amber-300"
              : "border-white/20 bg-white/5 text-white/40",
          ].join(" ")}
        >
          <Icon className="h-4 w-4" />
        </div>
      </div>

      <div className="min-w-0 flex-1 pb-6">
        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
          <span
            className={[
              "text-xs font-semibold uppercase",
              "tracking-wider",
              reached
                ? "text-amber-300"
                : "text-white/40",
            ].join(" ")}
          >
            {LABELS[milestone.milestoneType] ??
              milestone.milestoneType}
          </span>

          {/*
           * The raw position is an ordering key, not a score,
           * so it is shown to partners and authors rather than
           * to travellers. A pin reading "34%" implies a
           * measure of progress that it is not.
           */}
          <span className="text-[10px] text-white/25">
            pos {milestone.progressPercent}
          </span>
        </div>

        <p
          className={[
            "mt-0.5 text-sm font-medium",
            reached ? "text-white" : "text-white/60",
          ].join(" ")}
        >
          {milestone.label}
        </p>

        {reached && milestone.completedAt && (
          <p className="mt-1 text-xs text-amber-200/70">
            Reached{" "}
            {new Date(
              milestone.completedAt
            ).toLocaleString()}
          </p>
        )}
      </div>
    </li>
  );
}

export default function TreasureMap({
  milestones,
  className = "",
}) {
  const pins = Array.isArray(milestones)
    ? milestones
    : [];

  if (pins.length === 0) {
    return null;
  }

  const reached = pins.filter(
    (m) => m.completed
  ).length;

  return (
    <section
      className={[
        "rounded-2xl border border-white/10",
        "bg-white/5 p-5",
        className,
      ].join(" ")}
    >
      <header className="mb-4 flex items-baseline justify-between gap-3">
        <h2 className="text-sm font-bold uppercase tracking-wider text-white">
          Your journey
        </h2>

        {/*
         * A count, not a percentage. Two of six is a fact; 33%
         * of a journey is not a meaningful measure, because the
         * checkpoints are not equal in size or importance.
         */}
        <span className="text-xs text-white/50">
          {reached} of {pins.length} reached
        </span>
      </header>

      <ol className="space-y-0">
        {pins.map((milestone) => (
          <Pin
            key={milestone.milestoneId}
            milestone={milestone}
          />
        ))}
      </ol>
    </section>
  );
}