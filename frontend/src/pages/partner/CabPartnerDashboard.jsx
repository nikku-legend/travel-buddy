import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  Car,
  IndianRupee,
  Loader2,
  Navigation,
  Shield,
  Star,
} from "lucide-react";

import cabPartnerService from "../../services/cabPartnerService";
import { useAuth } from "../../context/useAuth";

/*
 * The cab partner portal. (FR-25)
 *
 * This rendered four fixed numbers — 0 rides, 0 vehicles, ₹0.00,
 * and a "Verified" KYC badge — plus two tabs reading "add vehicle
 * registrations and set pricing". The zeros and the badge were
 * literals in the markup, so a partner with a full fleet and a
 * suspended licence both saw the same page.
 *
 * Every figure below is the backend's answer. Verification in
 * particular is read per vehicle: a partner with one unverified cab
 * should not be told, in aggregate, that everything is verified.
 */

function money(amount) {
  if (amount === null || amount === undefined) return null;
  return Number(amount).toFixed(2);
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

const TABS = [
  { id: "overview", label: "Overview" },
  { id: "rides", label: "Assigned Rides" },
  { id: "vehicles", label: "Fleet & Vehicles" },
  { id: "documents", label: "Driver KYC & Docs" },
  { id: "earnings", label: "Settlement & Payouts" },
];

export default function CabPartnerDashboard() {
  const { user } = useAuth();

  const [tab, setTab] = useState("overview");
  const [vehicles, setVehicles] = useState([]);
  const [rides, setRides] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const [v, r] = await Promise.all([
        cabPartnerService.getMyVehicles(),
        cabPartnerService.getMyRides(),
      ]);

      setVehicles(v);
      setRides(r);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to load your cab dashboard."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const summary = useMemo(() => {
    const today = new Date().toISOString().slice(0, 10);

    const pickupsToday = rides.filter(
      (r) => String(r.pickupTime ?? "").slice(0, 10) === today
    );

    const live = rides.filter((r) => !cabPartnerService.isFinished(r.status));

    /*
     * "Earnings" here is fares for completed rides only. Adding up
     * cancelled and in-progress fares would report money that is
     * either never going to arrive or still belongs to the passenger.
     */
    const earned = rides
      .filter((r) => r.status === "COMPLETED")
      .reduce((total, r) => total + Number(r.fareAmount ?? 0), 0);

    return {
      today: pickupsToday.length,
      vehicles: vehicles.length,
      available: vehicles.filter((v) => v.isAvailable).length,
      verified: vehicles.filter((v) => v.isVerified).length,
      unverified: vehicles.filter((v) => !v.isVerified).length,
      live: live.length,
      active: live.filter((r) => r.status === "IN_PROGRESS").length,
      next: live.find((r) => r.status === "DRIVER_ASSIGNED"),
      earned,
    };
  }, [vehicles, rides]);

  async function moveRide(rideId, status) {
    setBusy(`${rideId}-${status}`);
    setError("");

    try {
      await cabPartnerService.updateRideStatus(rideId, status);
      await load();
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "That ride update could not be saved."
      );
    } finally {
      setBusy(null);
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50">
        <Loader2 className="h-7 w-7 animate-spin text-slate-400" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="border-b border-slate-200 bg-white">
        <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          <div className="inline-flex items-center gap-2 rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700">
            <Car className="h-3.5 w-3.5" />
            Transport & Cab Partner Operations
          </div>

          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Cab Partner — {user?.fullName || "Partner"}
          </h1>

          <p className="mt-1 text-sm text-slate-500">
            {summary.vehicles} vehicle
            {summary.vehicles === 1 ? "" : "s"} · {summary.live} ride
            {summary.live === 1 ? "" : "s"} in progress
          </p>

          <div className="mt-8 flex flex-wrap gap-2 border-b border-slate-200">
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                className={`border-b-2 px-4 py-3 text-sm font-semibold transition ${
                  tab === t.id
                    ? "border-amber-600 text-amber-700"
                    : "border-transparent text-slate-500 hover:text-slate-900"
                }`}
              >
                {t.label}
                {t.id === "rides" && summary.next && (
                  <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">
                    next
                  </span>
                )}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        {error && (
          <div className="mb-6 flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {tab === "overview" && (
          <div className="space-y-5">
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
              <StatCard
                label="Pickups today"
                hint="Scheduled departures"
                value={summary.today}
                tone="bg-amber-50 text-amber-600"
                icon={Navigation}
              />
              <StatCard
                label="Registered vehicles"
                hint={`${summary.available} marked available`}
                value={summary.vehicles}
                tone="bg-blue-50 text-blue-600"
                icon={Car}
              />
              <StatCard
                label="Completed fares"
                hint="Finished rides only"
                value={`INR ${money(summary.earned) ?? "0.00"}`}
                tone="bg-emerald-50 text-emerald-600"
                icon={IndianRupee}
              />
              <StatCard
                label="Verified vehicles"
                hint={
                  summary.unverified > 0
                    ? `${summary.unverified} awaiting review`
                    : "All vehicles cleared"
                }
                value={`${summary.verified}/${summary.vehicles}`}
                tone="bg-purple-50 text-purple-600"
                icon={Shield}
              />
            </div>

            {summary.unverified > 0 && (
              <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                <span>
                  {summary.unverified} of your vehicles have not been
                  verified. An unverified cab cannot be booked, so
                  travellers will not see it in search results.
                </span>
              </div>
            )}

            <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="text-sm font-bold uppercase tracking-wider text-slate-900">
                Next job
              </h2>

              {!summary.next ? (
                <p className="mt-3 text-sm text-slate-600">
                  {rides.length === 0
                    ? "No rides assigned yet. Trips book a cab automatically and it will appear here."
                    : "No pickup waiting to start."}
                </p>
              ) : (
                <div className="mt-4 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-amber-200 bg-amber-50 p-4">
                  <div>
                    <p className="text-sm font-semibold text-slate-900">
                      {summary.next.pickupLocation} →{" "}
                      {summary.next.dropLocation}
                    </p>
                    <p className="mt-1 text-xs text-slate-600">
                      OTP {summary.next.otpCode} ·{" "}
                      {summary.next.registrationNumber} ·{" "}
                      {summary.next.distanceKm} km
                    </p>
                  </div>

                  <button
                    type="button"
                    disabled={busy === `${summary.next.rideId}-IN_PROGRESS`}
                    onClick={() =>
                      moveRide(summary.next.rideId, "IN_PROGRESS")
                    }
                    className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-40"
                  >
                    {busy === `${summary.next.rideId}-IN_PROGRESS`
                      ? "Saving…"
                      : "Passenger on board"}
                  </button>
                </div>
              )}
            </section>
          </div>
        )}

        {tab === "rides" && (
          <div className="space-y-4">
            {rides.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
                <Navigation className="mx-auto h-10 w-10 text-slate-400" />
                <h3 className="mt-3 text-sm font-semibold text-slate-900">
                  No rides assigned
                </h3>
                <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
                  Rides are created when a traveller books a trip that
                  includes transport.
                </p>
              </div>
            ) : (
              rides.map((ride) => {
                const options = cabPartnerService.nextStatuses(
                  ride.status
                );

                return (
                  <article
                    key={ride.rideId}
                    className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <h3 className="text-base font-semibold text-slate-900">
                          {ride.pickupLocation} → {ride.dropLocation}
                        </h3>

                        <p className="mt-1 text-sm text-slate-500">
                          {ride.vehicleName} ·{" "}
                          {ride.registrationNumber}
                        </p>

                        <p className="mt-1 text-xs text-slate-500">
                          OTP <span className="font-mono">{ride.otpCode}</span>{" "}
                          · {ride.distanceKm} km ·{" "}
                          {String(ride.pickupTime ?? "").replace("T", " ")}
                        </p>
                      </div>

                      <div className="text-right">
                        <p className="text-sm font-semibold text-slate-900">
                          INR {money(ride.fareAmount) ?? "0.00"}
                        </p>
                        <span className="mt-1 inline-block rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                          {String(ride.status).replace(/_/g, " ")}
                        </span>
                      </div>
                    </div>

                    {options.length > 0 ? (
                      <div className="mt-4 flex flex-wrap gap-3">
                        {options.map((next) => (
                          <button
                            key={next}
                            type="button"
                            disabled={busy === `${ride.rideId}-${next}`}
                            onClick={() =>
                              moveRide(ride.rideId, next)
                            }
                            className={`rounded-lg px-4 py-2 text-sm font-semibold disabled:opacity-40 ${
                              next === "CANCELLED"
                                ? "border border-red-300 text-red-700 hover:bg-red-50"
                                : "bg-slate-900 text-white hover:bg-slate-800"
                            }`}
                          >
                            {busy === `${ride.rideId}-${next}`
                              ? "Saving…"
                              : cabPartnerService.statusLabel(next)}
                          </button>
                        ))}
                      </div>
                    ) : (
                      <p className="mt-3 text-xs text-slate-400">
                        {ride.status === "CANCELLED"
                          ? "Cancelled — this ride is closed."
                          : "Completed — this ride is closed."}
                      </p>
                    )}
                  </article>
                );
              })
            )}
          </div>
        )}

        {tab === "vehicles" && (
          <div className="space-y-4">
            {vehicles.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
                <Car className="mx-auto h-10 w-10 text-slate-400" />
                <h3 className="mt-3 text-sm font-semibold text-slate-900">
                  No vehicles registered
                </h3>
                <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
                  Register a vehicle with its registration number, driver
                  and pricing so it can be offered to travellers.
                </p>
              </div>
            ) : (
              vehicles.map((v) => (
                <div
                  key={v.cabId}
                  className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h3 className="text-base font-semibold text-slate-900">
                        {v.vehicleName}
                      </h3>

                      <p className="mt-1 text-sm text-slate-500">
                        {v.registrationNumber} ·{" "}
                        {String(v.vehicleType).replace(/_/g, " ")} ·{" "}
                        {v.seatingCapacity} seats
                      </p>

                      <p className="mt-1 text-xs text-slate-500">
                        {v.driverName} · {v.driverPhone}
                        {v.stateName ? ` · ${v.stateName}` : ""}
                      </p>
                    </div>

                    <div className="text-right">
                      {v.isVerified ? (
                        <span className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-semibold text-emerald-800">
                          Verified
                        </span>
                      ) : (
                        <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-800">
                          Pending review
                        </span>
                      )}

                      {v.rating !== null && (
                        <p className="mt-2 inline-flex items-center gap-1 text-xs text-slate-500">
                          <Star className="h-3.5 w-3.5 text-amber-500" />
                          {Number(v.rating).toFixed(1)}
                          <span className="text-slate-400">
                            ({v.reviewCount ?? 0} review
                            {v.reviewCount === 1 ? "" : "s"})
                          </span>
                        </p>
                      )}
                    </div>
                  </div>

                  <div className="mt-4 grid grid-cols-2 gap-4 border-t border-slate-100 pt-4 text-sm sm:grid-cols-4">
                    <div>
                      <p className="text-xs text-slate-500">Base fare</p>
                      <p className="font-semibold text-slate-900">
                        INR {money(v.baseFare) ?? "0.00"}
                      </p>
                    </div>

                    <div>
                      <p className="text-xs text-slate-500">Per km</p>
                      <p className="font-semibold text-slate-900">
                        INR {money(v.pricePerKm) ?? "0.00"}
                      </p>
                    </div>

                    <div>
                      <p className="text-xs text-slate-500">Availability</p>
                      <p className="font-semibold text-slate-900">
                        {v.isAvailable ? "Available" : "Not available"}
                      </p>
                    </div>

                    <div>
                      <p className="text-xs text-slate-500">Bookable</p>
                      <p className="font-semibold text-slate-900">
                        {v.isVerified && v.isAvailable
                          ? "Yes"
                          : "No — not yet"}
                      </p>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {tab === "documents" && (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
            <Shield className="mx-auto h-10 w-10 text-slate-400" />
            <h3 className="mt-3 text-sm font-semibold text-slate-900">
              Document uploads are not connected yet
            </h3>
            <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
              There is no document endpoint behind this tab, so nothing is
              shown rather than inventing a licence or insurance state.
              Your vehicles' real verification status is on the Fleet tab.
            </p>
          </div>
        )}

        {tab === "earnings" && (
          <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
            <IndianRupee className="mx-auto h-10 w-10 text-slate-400" />
            <h3 className="mt-3 text-sm font-semibold text-slate-900">
              Payouts are not connected yet
            </h3>
            <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
              There is no settlement endpoint, so no payout schedule or
              remittance history is shown here. Fares from completed rides
              are summed on the Overview tab.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}