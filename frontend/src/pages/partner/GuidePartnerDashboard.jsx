import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  Calendar,
  CheckCircle,
  Compass,
  DollarSign,
  Loader2,
  MapPin,
  Star,
  Users,
} from "lucide-react";

import guidePartnerService from "../../services/guidePartnerService";
import { useAuth } from "../../context/useAuth";

/*
 * The guide partner portal. (SRS 2.2 FR-16, FR-23)
 *
 * This used to render four fixed cards: zero tours, zero earnings,
 * a 5.0 rating on a profile it simultaneously described as new, and
 * a "Verified guide" badge it never checked. A partner opening this
 * page was shown invented numbers, and the rating in particular
 * looked like evidence of quality.
 *
 * Every figure below now comes from the API. Where the backend has
 * no answer, the card says so rather than filling the gap:
 * an unrated guide shows "Not rated", not a flattering default.
 */

function money(amount, currency) {
  if (amount === null || amount === undefined) {
    return null;
  }
  return `${currency ?? "INR"} ${Number(amount).toFixed(2)}`;
}

function StatCard({ label, hint, value, tone, icon: Icon }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
          {label}
        </span>
        <div
          className={`flex h-9 w-9 items-center justify-center rounded-xl ${tone}`}
        >
          <Icon className="h-5 w-5" />
        </div>
      </div>

      <div className="mt-3 text-2xl font-bold text-slate-900">
        {value}
      </div>
      <div className="mt-1 text-xs text-slate-500">{hint}</div>
    </div>
  );
}

export default function GuidePartnerDashboard() {
  const { user } = useAuth();

  const [profile, setProfile] = useState(null);
  const [reservations, setReservations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const [p, r] = await Promise.all([
        guidePartnerService.getMyGuideProfile(),
        guidePartnerService.getMyReservations(),
      ]);

      setProfile(p);
      setReservations(r);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to load your guide dashboard."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /*
   * Earnings are counted from reservations the backend marked paid,
   * not from every row. A tour that was paid for and then refunded
   * is not income, and summing the raw column would report it as
   * such.
   */
  const summary = useMemo(() => {
    const paid = reservations.filter(
      (r) => r.paymentStatus === "PAID"
    );

    const upcoming = reservations.filter(
      (r) =>
        r.paymentStatus === "PAID" &&
        r.bookingStatus !== "CANCELLED" &&
        r.bookingStatus !== "COMPLETED" &&
        new Date(`${r.tourDate}T00:00:00`) >= new Date(new Date().toDateString())
    );

    return {
      tours: reservations.length,
      upcoming: upcoming.length,
      earned: paid.reduce(
        (sum, r) => sum + Number(r.amount ?? 0),
        0
      ),
    };
  }, [reservations]);

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="border-b border-slate-200 bg-white">
        <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          <div className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700">
            <Compass className="h-3.5 w-3.5" />
            Tour Guide Partner Operations
          </div>

          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Guide Portal — {profile?.fullName || user?.fullName || "Guide"}
          </h1>

          {profile?.stateName && (
            <p className="mt-1 inline-flex items-center gap-1 text-sm text-slate-500">
              <MapPin className="h-3.5 w-3.5" />
              {profile.stateName}
            </p>
          )}
        </div>
      </div>

      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        {error && (
          <p className="mb-6 flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            {error}
          </p>
        )}

        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            label="Upcoming Tours"
            hint="Paid tours not yet finished"
            value={summary.upcoming}
            tone="bg-emerald-50 text-emerald-600"
            icon={Calendar}
          />

          <StatCard
            label="Total Earnings"
            hint="Paid tours, before commission"
            value={
              summary.earned > 0
                ? money(
                    summary.earned,
                    reservations[0]?.currency
                  )
                : money(0, reservations[0]?.currency)
            }
            tone="bg-blue-50 text-blue-600"
            icon={DollarSign}
          />

          <StatCard
            label="Rating"
            /*
             * "Not rated" rather than a number. A guide with no
             * reviews has no rating, and printing 5.0 here was the
             * same bug this dashboard had everywhere else: a
             * flattering default presented as evidence.
             */
            value={
              profile?.rating != null &&
              profile?.reviewCount > 0
                ? `${Number(profile.rating).toFixed(1)} ★`
                : "Not rated"
            }
            hint={
              profile?.reviewCount > 0
                ? `From ${profile.reviewCount} published review${
                    profile.reviewCount === 1 ? "" : "s"
                  }`
                : "No published reviews yet"
            }
            tone="bg-amber-50 text-amber-600"
            icon={Star}
          />

          <StatCard
            label="Status"
            /*
             * The real verification flag. It used to read
             * "Verified guide" unconditionally, which was a claim
             * the page had not checked and could not have known.
             */
            value={
              profile?.isVerified ? "Verified" : "Unverified"
            }
            hint={
              profile?.isVerified
                ? "Visible to travellers in search"
                : "Not yet visible to travellers"
            }
            tone="bg-teal-50 text-teal-600"
            icon={profile?.isVerified ? CheckCircle : AlertTriangle}
          />
        </div>

        <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-sm font-bold uppercase tracking-wider text-slate-900">
            Tour bookings
          </h2>

          {reservations.length === 0 ? (
            <p className="mt-4 text-sm text-slate-500">
              No tours booked yet. When a traveller adds you to
              their trip and pays, the tour appears here.
            </p>
          ) : (
            <ul className="mt-4 divide-y divide-slate-100">
              {reservations.map((r) => (
                <li
                  key={r.reservationId}
                  className="flex flex-wrap items-center justify-between gap-3 py-3"
                >
                  <div>
                    <p className="text-sm font-semibold text-slate-900">
                      {r.tourDate}
                      {r.partySize
                        ? ` · ${r.partySize} traveller${
                            r.partySize === 1 ? "" : "s"
                          }`
                        : ""}
                    </p>
                    <p className="text-xs text-slate-500">
                      {r.bookingReference} ·{" "}
                      {r.bookingStatus?.replace(/_/g, " ")}
                    </p>
                  </div>

                  <div className="text-right">
                    <p className="text-sm font-semibold text-slate-900">
                      {money(r.amount, r.currency)}
                    </p>
                    <p className="text-xs text-slate-500">
                      {r.paymentStatus}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-sm font-bold uppercase tracking-wider text-slate-900">
            Your listing
          </h2>

          <dl className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div>
              <dt className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Daily rate
              </dt>
              <dd className="mt-1 text-sm text-slate-900">
                {money(profile?.dailyRate, profile?.currencyCode)}
              </dd>
            </div>

            <div>
              <dt className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Experience
              </dt>
              <dd className="mt-1 text-sm text-slate-900">
                {profile?.yearsOfExperience ?? 0} years
              </dd>
            </div>

            <div>
              <dt className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Languages
              </dt>
              <dd className="mt-1 flex flex-wrap gap-1">
                {(profile?.languages ?? []).length === 0 ? (
                  <span className="text-sm text-slate-500">
                    None listed
                  </span>
                ) : (
                  profile.languages.map((l) => (
                    <span
                      key={l}
                      className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700"
                    >
                      {l}
                    </span>
                  ))
                )}
              </dd>
            </div>
          </dl>

          {profile?.bio && (
            <p className="mt-4 text-sm text-slate-600">
              {profile.bio}
            </p>
          )}
        </section>

        <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="inline-flex items-center gap-2 text-sm font-bold uppercase tracking-wider text-slate-900">
            <Users className="h-4 w-4" />
            Who can book you
          </h2>
          <p className="mt-3 text-sm text-slate-600">
            {profile?.isVerified
              ? "Your listing is visible to travellers, and a guide is reserved for a date the moment their trip is paid for. A date already taken cannot be sold twice."
              : "Your listing is not yet visible to travellers. An admin needs to verify your documents before you can be booked."}
          </p>
        </section>
      </div>
    </div>
  );
}