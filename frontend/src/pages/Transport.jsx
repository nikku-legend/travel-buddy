import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  AlertCircle,
  Car,
  CheckCircle2,
  Clock,
  Compass,
  Key,
  MapPin,
  Navigation,
  Phone,
  Search,
  ShieldCheck,
  Star,
  Users,
  X,
} from "lucide-react";
import cabService from "../services/cabService";
import destinationService from "../services/destinationService";
import { useAuth } from "../context/useAuth";

const VEHICLE_TYPES = [
  { id: "", label: "All Vehicles" },
  { id: "HATCHBACK", label: "Hatchback", subtitle: "4 Seats • Budget" },
  { id: "SEDAN", label: "Sedan", subtitle: "4 Seats • Comfort" },
  { id: "SUV", label: "SUV", subtitle: "6-7 Seats • Family" },
  { id: "TEMPO_TRAVELLER", label: "Tempo", subtitle: "12-16 Seats • Groups" },
];

export default function Transport() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [cabs, setCabs] = useState([]);
  const [states, setStates] = useState([]);
  const [selectedState, setSelectedState] = useState("");
  const [selectedType, setSelectedType] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // My rides tab / modal
  const [myRides, setMyRides] = useState([]);
  const [viewingMyRides, setViewingMyRides] = useState(false);

  // Booking modal
  const [selectedCab, setSelectedCab] = useState(null);
  const [bookingForm, setBookingForm] = useState({
    pickupLocation: "",
    dropLocation: "",
    pickupTime: "",
    distanceKm: "15",
  });
  const [bookingLoading, setBookingLoading] = useState(false);
  const [bookingSuccess, setBookingSuccess] = useState(null);
  const [bookingError, setBookingError] = useState("");

  useEffect(() => {
    async function init() {
      try {
        setLoading(true);
        setError("");
        const [cabsData, statesData] = await Promise.all([
          cabService.searchCabs(),
          destinationService.getStates(1).catch(() => []),
        ]);
        setCabs(cabsData || []);
        setStates(statesData || []);
      } catch (err) {
        console.error(err);
        setError("Unable to load vehicles right now.");
      } finally {
        setLoading(false);
      }
    }
    init();
  }, []);

  async function handleFilterChange(stateId, type) {
    try {
      setLoading(true);
      const params = {};
      if (stateId) params.stateId = stateId;
      if (type) params.vehicleType = type;
      const data = await cabService.searchCabs(params);
      setCabs(data || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }

  async function loadMyRides() {
    if (!user) {
      navigate("/login");
      return;
    }
    try {
      setViewingMyRides(true);
      const rides = await cabService.getMyRides();
      setMyRides(rides || []);
    } catch (err) {
      console.error(err);
    }
  }

  function calculateEstimatedFare() {
    if (!selectedCab) return 0;
    const base = Number(selectedCab.baseFare || 0);
    const perKm = Number(selectedCab.pricePerKm || 0);
    const dist = Number(bookingForm.distanceKm || 0);
    return (base + dist * perKm).toFixed(2);
  }

  async function handleBookRide(e) {
    e.preventDefault();
    if (!user) {
      navigate("/login");
      return;
    }

    try {
      setBookingLoading(true);
      setBookingError("");
      const payload = {
        cabId: selectedCab.cabId,
        pickupLocation: bookingForm.pickupLocation,
        dropLocation: bookingForm.dropLocation,
        pickupTime: bookingForm.pickupTime,
        distanceKm: Number(bookingForm.distanceKm),
      };

      const result = await cabService.bookRide(payload);
      setBookingSuccess(result);
    } catch (err) {
      console.error(err);
      setBookingError(err.response?.data?.message || err.message || "Failed to book ride.");
    } finally {
      setBookingLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-950 text-white">
      {/* Hero Banner */}
      <section className="relative overflow-hidden border-b border-white/10 bg-slate-900/60 py-16 sm:py-20">
        <div className="pointer-events-none absolute inset-0">
          <div className="absolute -left-40 top-0 h-96 w-96 rounded-full bg-amber-500/10 blur-3xl" />
          <div className="absolute right-0 top-1/2 h-96 w-96 rounded-full bg-cyan-500/10 blur-3xl" />
        </div>

        <div className="relative mx-auto max-w-7xl px-5 sm:px-6 lg:px-8">
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
            <div className="max-w-3xl">
              <div className="inline-flex items-center gap-2 rounded-full border border-amber-400/30 bg-amber-400/10 px-4 py-1.5 text-xs font-semibold text-amber-300 backdrop-blur-xl">
                <Car className="h-3.5 w-3.5" />
                Intercity, Airport & Local Cabs
              </div>
              <h1 className="mt-4 text-4xl font-extrabold tracking-tight sm:text-5xl lg:text-6xl">
                Reliable travel rides,{" "}
                <span className="bg-gradient-to-r from-amber-300 via-orange-300 to-yellow-300 bg-clip-text text-transparent">
                  anywhere you explore
                </span>
              </h1>
              <p className="mt-4 text-base leading-7 text-slate-300 sm:text-lg">
                Transparent distance pricing, verified commercial drivers, and OTP-secured ride start.
              </p>
            </div>

            {user && (
              <button
                type="button"
                onClick={loadMyRides}
                className="inline-flex items-center gap-2 rounded-2xl border border-white/10 bg-white/[0.04] px-5 py-3 text-sm font-semibold text-white shadow-xl backdrop-blur-xl hover:bg-white/10"
              >
                <Navigation className="h-4 w-4 text-amber-400" />
                View My Booked Rides
              </button>
            )}
          </div>

          {/* Filters & Vehicle Types */}
          <div className="mt-10 space-y-4">
            <div className="flex flex-wrap gap-2">
              {VEHICLE_TYPES.map((vt) => (
                <button
                  key={vt.id}
                  type="button"
                  onClick={() => {
                    setSelectedType(vt.id);
                    handleFilterChange(selectedState, vt.id);
                  }}
                  className={`rounded-2xl border px-4 py-2.5 text-left transition ${
                    selectedType === vt.id
                      ? "border-amber-400 bg-amber-400/15 text-white ring-2 ring-amber-400/30"
                      : "border-white/10 bg-white/[0.02] text-slate-300 hover:border-white/20 hover:bg-white/[0.05]"
                  }`}
                >
                  <div className="text-xs font-bold">{vt.label}</div>
                  {vt.subtitle && (
                    <div className="text-[10px] text-slate-400">{vt.subtitle}</div>
                  )}
                </button>
              ))}
            </div>

            <div className="max-w-xs">
              <select
                value={selectedState}
                onChange={(e) => {
                  setSelectedState(e.target.value);
                  handleFilterChange(e.target.value, selectedType);
                }}
                className="w-full rounded-2xl border border-white/10 bg-black/40 px-4 py-3 text-sm text-white outline-none focus:border-amber-400 cursor-pointer"
              >
                <option value="" className="bg-slate-900 text-white">
                  All Regions / States
                </option>
                {states.map((st) => (
                  <option
                    key={st.stateId}
                    value={st.stateId}
                    className="bg-slate-900 text-white"
                  >
                    {st.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>
      </section>

      {/* Vehicles Grid */}
      <section className="mx-auto max-w-7xl px-5 py-12 sm:px-6 lg:px-8">
        {loading ? (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div
                key={i}
                className="h-72 animate-pulse rounded-3xl border border-white/10 bg-white/[0.02]"
              />
            ))}
          </div>
        ) : error ? (
          <div className="rounded-3xl border border-rose-500/20 bg-rose-500/10 p-8 text-center text-rose-300">
            {error}
          </div>
        ) : cabs.length === 0 ? (
          <div className="rounded-3xl border border-white/10 bg-white/[0.02] p-16 text-center">
            <Car className="mx-auto h-12 w-12 text-slate-500" />
            <h3 className="mt-4 text-lg font-semibold text-white">
              No vehicles available in this category
            </h3>
            <p className="mt-2 text-sm text-slate-400">
              Try switching vehicle types or picking another region.
            </p>
          </div>
        ) : (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {cabs.map((cab) => (
              <div
                key={cab.cabId}
                className="group flex flex-col justify-between rounded-3xl border border-white/10 bg-white/[0.03] p-6 shadow-xl backdrop-blur-xl transition hover:border-amber-400/40 hover:bg-white/[0.05]"
              >
                <div>
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <span className="inline-flex rounded-lg bg-amber-400/10 px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-amber-300 border border-amber-400/20">
                        {cab.vehicleType?.replace("_", " ")}
                      </span>
                      <h3 className="mt-2 font-bold text-white text-lg">
                        {cab.vehicleName}
                      </h3>
                      <p className="text-xs text-slate-400 font-mono">
                        {cab.registrationNumber}
                      </p>
                    </div>

                    {/*
                     * No rating is shown when there is none. This
                     * used to read cab.rating || "5.0", which turned
                     * the absence of reviews into a perfect score
                     * on the client, undoing the backend's decision
                     * not to invent one.
                     */}
                    {cab.rating != null && (
                      <div className="flex items-center gap-1 rounded-xl bg-amber-400/10 px-2.5 py-1 text-xs font-bold text-amber-300 border border-amber-400/20">
                        <Star className="h-3 w-3 fill-amber-300" />
                        <span>{cab.rating}</span>
                        {cab.reviewCount > 0 && (
                          <span className="font-normal text-amber-200/60">
                            ({cab.reviewCount})
                          </span>
                        )}
                      </div>
                    )}
                  </div>

                  <div className="mt-4 space-y-2 border-t border-white/5 pt-3 text-xs text-slate-300">
                    <div className="flex items-center gap-2">
                      <Users className="h-3.5 w-3.5 text-slate-400" />
                      <span>Capacity: {cab.seatingCapacity} Passengers</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <ShieldCheck className="h-3.5 w-3.5 text-emerald-400" />
                      <span>Driver: {cab.driverName}</span>
                    </div>
                    {cab.stateName && (
                      <div className="flex items-center gap-2">
                        <MapPin className="h-3.5 w-3.5 text-slate-400" />
                        <span>Base: {cab.stateName}</span>
                      </div>
                    )}
                  </div>
                </div>

                <div className="mt-6 border-t border-white/10 pt-4 flex items-center justify-between">
                  <div>
                    <span className="text-[11px] text-slate-400">Rate Plan</span>
                    <div className="text-base font-bold text-white">
                      ₹{cab.baseFare} + ₹{cab.pricePerKm}/km
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      setSelectedCab(cab);
                      setBookingSuccess(null);
                      setBookingError("");
                      setBookingForm({
                        pickupLocation: "",
                        dropLocation: "",
                        pickupTime: "",
                        distanceKm: "15",
                      });
                    }}
                    className="flex items-center gap-1.5 rounded-xl bg-amber-400 px-4 py-2.5 text-xs font-semibold text-slate-950 transition hover:bg-amber-300 cursor-pointer"
                  >
                    <Car className="h-3.5 w-3.5" />
                    Book Ride
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Book Ride Modal */}
      {selectedCab && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm">
          <div className="w-full max-w-lg rounded-3xl border border-white/10 bg-slate-900 p-6 shadow-2xl">
            <div className="flex items-center justify-between border-b border-white/10 pb-4">
              <div>
                <h3 className="font-bold text-white text-lg">
                  Book {selectedCab.vehicleName}
                </h3>
                <p className="text-xs text-slate-400">
                  {selectedCab.driverName} • {selectedCab.registrationNumber}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setSelectedCab(null)}
                className="rounded-xl p-1.5 text-slate-400 hover:bg-white/10 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {bookingSuccess ? (
              <div className="mt-6 text-center">
                <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-400" />
                <h4 className="mt-3 text-xl font-bold text-white">Ride Confirmed!</h4>
                <p className="mt-1 text-sm text-slate-300">
                  Your ride has been booked. Share this OTP with the driver upon pickup:
                </p>
                <div className="my-5 inline-block rounded-2xl border border-amber-400/40 bg-amber-400/10 px-6 py-3">
                  <div className="text-xs font-semibold uppercase tracking-widest text-amber-300">
                    START RIDE OTP
                  </div>
                  <div className="mt-1 font-mono text-3xl font-black text-white tracking-widest">
                    {bookingSuccess.otpCode}
                  </div>
                </div>

                <div className="rounded-2xl border border-white/10 bg-black/30 p-4 text-left text-xs text-slate-300 space-y-1.5">
                  <div className="flex justify-between">
                    <span className="text-slate-400">Total Fare:</span>
                    <span className="font-bold text-white">₹{bookingSuccess.fareAmount}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Driver Contact:</span>
                    <span className="font-bold text-white">{bookingSuccess.driverPhone}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Pickup:</span>
                    <span className="text-white">{bookingSuccess.pickupLocation}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Drop:</span>
                    <span className="text-white">{bookingSuccess.dropLocation}</span>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => setSelectedCab(null)}
                  className="mt-6 w-full rounded-xl bg-white px-4 py-3 text-sm font-semibold text-slate-950 hover:bg-slate-200"
                >
                  Done
                </button>
              </div>
            ) : (
              <form onSubmit={handleBookRide} className="mt-5 space-y-4">
                {bookingError && (
                  <div className="rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs text-rose-300">
                    {bookingError}
                  </div>
                )}

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                    Pickup Location
                  </label>
                  <input
                    required
                    type="text"
                    value={bookingForm.pickupLocation}
                    onChange={(e) =>
                      setBookingForm({ ...bookingForm, pickupLocation: e.target.value })
                    }
                    placeholder="e.g. Bhubaneswar Airport Terminal 1"
                    className="w-full rounded-xl border border-white/10 bg-black/40 px-4 py-2.5 text-sm text-white outline-none focus:border-amber-400"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                    Drop Location
                  </label>
                  <input
                    required
                    type="text"
                    value={bookingForm.dropLocation}
                    onChange={(e) =>
                      setBookingForm({ ...bookingForm, dropLocation: e.target.value })
                    }
                    placeholder="e.g. Puri Grand Heritage Hotel"
                    className="w-full rounded-xl border border-white/10 bg-black/40 px-4 py-2.5 text-sm text-white outline-none focus:border-amber-400"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                      Pickup Time
                    </label>
                    <input
                      required
                      type="datetime-local"
                      value={bookingForm.pickupTime}
                      onChange={(e) =>
                        setBookingForm({ ...bookingForm, pickupTime: e.target.value })
                      }
                      className="w-full rounded-xl border border-white/10 bg-black/40 px-3 py-2.5 text-sm text-white outline-none focus:border-amber-400"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                      Est. Distance (km)
                    </label>
                    <input
                      required
                      type="number"
                      min="1"
                      value={bookingForm.distanceKm}
                      onChange={(e) =>
                        setBookingForm({ ...bookingForm, distanceKm: e.target.value })
                      }
                      className="w-full rounded-xl border border-white/10 bg-black/40 px-3 py-2.5 text-sm text-white outline-none focus:border-amber-400"
                    />
                  </div>
                </div>

                <div className="rounded-2xl border border-white/10 bg-black/30 p-3.5 text-xs text-slate-300 space-y-1">
                  <div className="flex justify-between">
                    <span>Base Fare:</span>
                    <span>₹{selectedCab.baseFare}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Distance Fare ({bookingForm.distanceKm} km × ₹{selectedCab.pricePerKm}):</span>
                    <span>₹{(Number(bookingForm.distanceKm || 0) * Number(selectedCab.pricePerKm || 0)).toFixed(2)}</span>
                  </div>
                  <div className="flex justify-between border-t border-white/10 pt-1.5 font-bold text-white text-sm">
                    <span>Estimated Total:</span>
                    <span>₹{calculateEstimatedFare()}</span>
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={bookingLoading}
                  className="w-full rounded-xl bg-amber-400 py-3 text-sm font-semibold text-slate-950 transition hover:bg-amber-300 disabled:opacity-50 cursor-pointer"
                >
                  {bookingLoading ? "Confirming Ride..." : `Confirm Ride (₹${calculateEstimatedFare()})`}
                </button>
              </form>
            )}
          </div>
        </div>
      )}

      {/* My Rides Drawer / Modal */}
      {viewingMyRides && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm">
          <div className="w-full max-w-2xl max-h-[85vh] overflow-y-auto rounded-3xl border border-white/10 bg-slate-900 p-6 shadow-2xl">
            <div className="flex items-center justify-between border-b border-white/10 pb-4">
              <div className="flex items-center gap-2">
                <Navigation className="h-5 w-5 text-amber-400" />
                <h3 className="font-bold text-white text-lg">My Cab Rides</h3>
              </div>
              <button
                type="button"
                onClick={() => setViewingMyRides(false)}
                className="rounded-xl p-1.5 text-slate-400 hover:bg-white/10 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-5 space-y-4">
              {myRides.length === 0 ? (
                <div className="p-8 text-center text-sm text-slate-400">
                  You haven't booked any rides yet.
                </div>
              ) : (
                myRides.map((ride) => (
                  <div
                    key={ride.rideId}
                    className="rounded-2xl border border-white/10 bg-black/30 p-4 text-xs space-y-2"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-white text-sm">
                        {ride.vehicleName} ({ride.vehicleType})
                      </span>
                      <span className="rounded-lg bg-emerald-400/10 px-2 py-0.5 font-bold uppercase tracking-wider text-emerald-300">
                        {ride.status}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-slate-300">
                      <div>From: <span className="text-white">{ride.pickupLocation}</span></div>
                      <div>To: <span className="text-white">{ride.dropLocation}</span></div>
                      <div>Driver: <span className="text-white">{ride.driverName} ({ride.driverPhone})</span></div>
                      <div>Fare: <span className="font-bold text-white">₹{ride.fareAmount}</span></div>
                    </div>

                    <div className="flex items-center justify-between border-t border-white/5 pt-2">
                      <span className="text-slate-400">OTP for Driver:</span>
                      <span className="font-mono text-base font-bold text-amber-400">
                        {ride.otpCode}
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
