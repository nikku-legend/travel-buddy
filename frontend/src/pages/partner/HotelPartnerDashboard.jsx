import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  BedDouble,
  Building2,
  CalendarCheck,
  CheckCircle2,
  DoorOpen,
  Loader2,
  Users,
} from "lucide-react";

import hotelPartnerService from "../../services/hotelPartnerService";
import InventoryTab from "../../components/partner/InventoryTab";
import ReservationsTab from "../../components/partner/ReservationsTab";
import FrontDeskTab from "../../components/partner/FrontDeskTab";
import { useAuth } from "../../context/useAuth";

/*
 * The hotel partner portal. (FR-20, FR-21, FR-22, FR-23)
 *
 * This used to render four fixed numbers — zero revenue, zero
 * bookings, 0% occupancy — plus four tabs reading "module
 * initialized and connected", which described the file rather than
 * the product. Nothing on it called an API.
 *
 * Every figure below is now the backend's answer, and where the
 * backend has none the card says so rather than inventing one.
 */

function money(amount, currency) {
  if (amount === null || amount === undefined) return null;
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

const TABS = [
  { id: "overview", label: "Overview" },
  { id: "properties", label: "My Properties" },
  { id: "inventory", label: "Inventory" },
  { id: "bookings", label: "Reservations" },
  { id: "frontdesk", label: "Front Desk" },
];

export default function HotelPartnerDashboard() {
  const { user } = useAuth();

  const [tab, setTab] = useState("overview");
  const [properties, setProperties] = useState([]);
  const [reservations, setReservations] = useState([]);
  const [stays, setStays] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const [p, r, f] = await Promise.all([
        hotelPartnerService.getMyProperties(),
        hotelPartnerService.getReservations(),
        hotelPartnerService.getFrontDesk(),
      ]);

      setProperties(p);
      setReservations(r);
      setStays(f);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "Unable to load your hotel dashboard."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  /*
   * Revenue counts only settled money. A booking that was paid and
   * then refunded is not income, and one still awaiting payment is
   * not income either — summing the raw column reports both as
   * takings.
   */
  const summary = useMemo(() => {
    const settled = reservations.filter(
      (r) => r.paymentStatus === "PAID" && r.bookingStatus !== "CANCELLED"
    );

    const owed = reservations.filter((r) => r.roomsStillToAssign > 0);

    const today = new Date().toISOString().slice(0, 10);

    return {
      live: properties.filter((p) => p.isLive).length,
      pending: properties.filter(
        (p) => p.status === "PENDING_APPROVAL"
      ).length,
      revenue: settled.reduce(
        (total, r) => total + Number(r.totalAmount ?? 0),
        0
      ),
      reservations: reservations.length,
      roomsOutstanding: owed.reduce(
        (total, r) => total + r.roomsStillToAssign,
        0
      ),
      arrivalsToday: reservations.filter((r) => r.checkIn === today)
        .length,
      inHouse: stays.filter((s) => s.status === "CHECKED_IN").length,
      owed,
    };
  }, [properties, reservations, stays]);

  async function act(label, call) {
    setBusyId(label);
    setError("");

    try {
      await call();
      await load();
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          "That action could not be completed."
      );
    } finally {
      setBusyId(null);
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
          <div className="inline-flex items-center gap-2 rounded-full bg-cyan-50 px-3 py-1 text-xs font-semibold text-cyan-700">
            <Building2 className="h-3.5 w-3.5" />
            Hotel Partner Operations
          </div>

          <h1 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Welcome back, {user?.fullName || "Partner"}
          </h1>

          <p className="mt-1 text-sm text-slate-500">
            {summary.live} live · {summary.pending} awaiting approval
          </p>

          <div className="mt-8 flex flex-wrap gap-2 border-b border-slate-200">
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                className={`border-b-2 px-4 py-3 text-sm font-semibold transition ${
                  tab === t.id
                    ? "border-cyan-600 text-cyan-700"
                    : "border-transparent text-slate-500 hover:text-slate-900"
                }`}
              >
                {t.label}
                {t.id === "bookings" && summary.owed.length > 0 && (
                  <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">
                    {summary.owed.length}
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
                label="Settled revenue"
                hint="Paid, not cancelled"
                value={money(summary.revenue) ?? "INR 0.00"}
                tone="bg-emerald-50 text-emerald-600"
                icon={CheckCircle2}
              />
              <StatCard
                label="Open reservations"
                hint="Arriving or in-house"
                value={summary.reservations}
                tone="bg-blue-50 text-blue-600"
                icon={CalendarCheck}
              />
              <StatCard
                label="Rooms still owed"
                hint="Guests without a room number"
                value={summary.roomsOutstanding}
                tone="bg-purple-50 text-purple-600"
                icon={BedDouble}
              />
              <StatCard
                label="Arrivals today"
                hint="Checking in"
                value={summary.arrivalsToday}
                tone="bg-cyan-50 text-cyan-600"
                icon={Users}
              />
            </div>

            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
              <StatCard
                label="In house"
                hint="Currently checked in"
                value={summary.inHouse}
                tone="bg-emerald-50 text-emerald-600"
                icon={DoorOpen}
              />
              <StatCard
                label="Awaiting a room"
                hint="Reservations needing assignment"
                value={summary.owed.length}
                tone="bg-amber-50 text-amber-600"
                icon={AlertTriangle}
              />
            </div>

            <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="text-sm font-bold uppercase tracking-wider text-slate-900">
                Needs your attention
              </h2>

              {summary.owed.length === 0 ? (
                <p className="mt-3 text-sm text-slate-600">
                  {properties.length === 0
                    ? "Add a property and a room type to start selling."
                    : "Nothing outstanding — every confirmed guest has a room number."}
                </p>
              ) : (
                <ul className="mt-4 divide-y divide-slate-100">
                  {summary.owed.slice(0, 5).map((r) => (
                    <li
                      key={r.bookingId}
                      className="flex items-center justify-between py-3"
                    >
                      <div>
                        <p className="text-sm font-semibold text-slate-900">
                          {r.guestName || "Guest"}
                        </p>
                        <p className="text-xs text-slate-500">
                          {r.propertyName} · {r.roomCategory} ·{" "}
                          {r.roomsStillToAssign} room
                          {r.roomsStillToAssign === 1 ? "" : "s"} to assign
                        </p>
                      </div>

                      <button
                        type="button"
                        onClick={() => setTab("bookings")}
                        className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white hover:bg-slate-800"
                      >
                        Assign
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}

        {tab === "properties" && (
          <div className="space-y-4">
            {properties.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
                <Building2 className="mx-auto h-10 w-10 text-slate-400" />
                <h3 className="mt-3 text-sm font-semibold text-slate-900">
                  No properties yet
                </h3>
                <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
                  A property needs at least one room type before it can be
                  submitted, because a property with no rooms has nothing
                  to sell.
                </p>
              </div>
            ) : (
              properties.map((p) => (
                <div
                  key={p.propertyId}
                  className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h3 className="text-base font-semibold text-slate-900">
                        {p.name}
                      </h3>
                      <p className="mt-1 text-sm text-slate-500">
                        {String(p.propertyType).replace(/_/g, " ")} ·{" "}
                        {p.stateName} · {p.roomTypeCount} room type
                        {p.roomTypeCount === 1 ? "" : "s"}
                      </p>
                    </div>

                    <span
                      className={`rounded-full px-3 py-1 text-xs font-semibold ${
                        p.isLive
                          ? "bg-emerald-100 text-emerald-800"
                          : "bg-slate-100 text-slate-700"
                      }`}
                    >
                      {p.statusLabel ??
                        String(p.status).replace(/_/g, " ")}
                    </span>
                  </div>

                  {p.rejectionReason && (
                    <div className="mt-4 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                      <span>
                        <strong>Not approved:</strong>{" "}
                        {p.rejectionReason}
                      </span>
                    </div>
                  )}

                  <p className="mt-3 text-xs text-slate-500">
                    {p.isLive
                      ? "Visible to travellers and bookable."
                      : p.canSubmit
                        ? "Ready to submit for review."
                        : "Add at least one room type before submitting."}
                  </p>
                </div>
              ))
            )}
          </div>
        )}

        {tab === "inventory" && <InventoryTab properties={properties} />}

        {tab === "bookings" && (
          <ReservationsTab
            reservations={reservations}
            busyId={busyId}
            onAct={act}
          />
        )}

        {tab === "frontdesk" && (
          <FrontDeskTab stays={stays} busyId={busyId} onAct={act} />
        )}
      </div>
    </div>
  );
}
