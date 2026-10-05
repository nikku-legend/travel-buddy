import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import {
  ArrowLeft,
  BedDouble,
  Building2,
  CalendarDays,
  CheckCircle2,
  MapPin,
  Search,
  Users,
} from "lucide-react";

import propertyService from "../services/propertyService";
import Loading from "../components/Loading";

/*
 * Format Date → YYYY-MM-DD
 */
function formatDate(date) {
  const year = date.getFullYear();

  const month = String(
    date.getMonth() + 1
  ).padStart(2, "0");

  const day = String(
    date.getDate()
  ).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

/*
 * Get the date immediately after YYYY-MM-DD.
 */
function getNextDate(dateString) {
  const date =
    new Date(`${dateString}T00:00:00`);

  date.setDate(
    date.getDate() + 1
  );

  return formatDate(date);
}

/*
 * Format YYYY-MM-DD for UI.
 */
function formatDisplayDate(dateString) {
  if (!dateString) {
    return "";
  }

  const date =
    new Date(`${dateString}T00:00:00`);

  return date.toLocaleDateString(
    "en-IN",
    {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }
  );
}

/*
 * Safe money formatting.
 */
function formatMoney(value) {
  const number = Number(value);

  if (Number.isNaN(number)) {
    return value;
  }

  return number.toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export default function StayDetails() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [property, setProperty] = useState(null);

  const [checkIn, setCheckIn] = useState(() => {
    const defaultDate = new Date();
    defaultDate.setDate(defaultDate.getDate() + 1);
    return formatDate(defaultDate);
  });

  const [checkOut, setCheckOut] = useState(() => {
    const defaultDate = new Date();
    defaultDate.setDate(defaultDate.getDate() + 3);
    return formatDate(defaultDate);
  });

  const [guests, setGuests] = useState(2);
  const [rooms, setRooms] = useState(1);

  const [availability, setAvailability] = useState([]);

  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);

  const [error, setError] = useState("");
  const [availabilityError, setAvailabilityError] = useState("");

  useEffect(() => {
    let isMounted = true;

    async function loadProperty() {
      try {
        setLoading(true);
        setError("");

        const data = await propertyService.getPropertyById(id);

        if (isMounted) {
          setProperty(data);
        }
      } catch (err) {
        console.error("Failed to load property:", err);

        if (isMounted) {
          if (err.response?.status === 404) {
            setError("Property not found.");
          } else if (err.response?.status === 403) {
            setError(
              "You are not allowed to access this property."
            );
          } else {
            setError(
              err.response?.data?.message ||
                "Unable to load property details."
            );
          }
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    loadProperty();

    return () => {
      isMounted = false;
    };
  }, [id]);

  /*
   * Check real-time room availability.
   */
  async function handleAvailabilitySearch(event) {
    event.preventDefault();

    setAvailability([]);
    setAvailabilityError("");

    if (!checkIn || !checkOut) {
      setAvailabilityError(
        "Please select both check-in and check-out dates."
      );
      return;
    }

    if (checkIn >= checkOut) {
      setAvailabilityError(
        "Check-out must be after check-in."
      );
      return;
    }

    if (guests < 1) {
      setAvailabilityError(
        "At least one guest is required."
      );
      return;
    }

    if (rooms < 1) {
      setAvailabilityError(
        "At least one room is required."
      );
      return;
    }

    try {
      setSearching(true);

      const results =
        await propertyService.checkAvailability(
          id,
          {
            checkIn,
            checkOut,
            guests,
            rooms,
          }
        );

      setAvailability(results);

      if (!results || results.length === 0) {
        setAvailabilityError(
          "No rooms are available for the selected dates and guest count."
        );
      }
    } catch (err) {
      console.error(
        "Availability check failed:",
        err
      );

      const message =
        err.response?.data?.message ||
        "Unable to check availability.";

      setAvailabilityError(message);
    } finally {
      setSearching(false);
    }
  }

  /*
   * Handle room selection.
   */
  function handleSelectRoom(room) {
    navigate("/checkout", {
      state: {
        property: {
          propertyId: property.propertyId,
          name: property.name,
          address: property.address,
          stateName: property.stateName,
        },
        room,
        checkIn,
        checkOut,
        guests,
        rooms,
      },
    });
  }

  if (loading) {
    return <Loading />;
  }

  if (error || !property) {
    return (
      <div className="min-h-screen bg-slate-50">
        <div className="mx-auto max-w-3xl px-5 py-20 text-center">

          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-slate-100">
            <Building2
              size={28}
              className="text-slate-400"
            />
          </div>

          <h1 className="mt-6 text-3xl font-bold text-slate-950">
            Property unavailable
          </h1>

          <p className="mt-3 text-slate-500">
            {error || "Property could not be found."}
          </p>

          <Link
            to="/stays"
            className="mt-7 inline-flex items-center gap-2 rounded-xl bg-slate-950 px-5 py-3 text-sm font-semibold text-white transition hover:bg-slate-700"
          >
            <ArrowLeft size={17} />
            Back to stays
          </Link>

        </div>
      </div>
    );
  }

  const today = formatDate(new Date());

  return (
    <div className="min-h-screen bg-slate-50">

      {/* Back navigation */}
      <div className="mx-auto max-w-7xl px-5 pt-7 lg:px-8">

        <Link
          to="/stays"
          className="inline-flex items-center gap-2 text-sm font-medium text-slate-500 transition hover:text-slate-950"
        >
          <ArrowLeft size={16} />
          Back to stays
        </Link>

      </div>

      {/* Property hero */}
      <section className="mx-auto max-w-7xl px-5 pb-10 pt-6 lg:px-8">

        <div className="overflow-hidden rounded-[2rem] bg-slate-950">

          <div className="relative flex min-h-[400px] items-end overflow-hidden bg-gradient-to-br from-slate-900 via-slate-800 to-slate-950">

            {/* Decorative icon */}
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <Building2
                size={120}
                strokeWidth={0.7}
                className="text-white/10"
              />
            </div>

            {/* Gradient overlay */}
            <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent" />

            <div className="relative z-10 w-full p-7 sm:p-10 lg:p-12">

              <div className="mb-4 flex flex-wrap gap-2">

                <span className="rounded-full border border-white/15 bg-white/10 px-3 py-1.5 text-xs font-semibold text-white backdrop-blur-xl">
                  {property.propertyType}
                </span>

                {property.verified && (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-white px-3 py-1.5 text-xs font-semibold text-slate-900">
                    <CheckCircle2 size={14} />
                    Verified property
                  </span>
                )}

              </div>

              <h1 className="max-w-4xl text-4xl font-bold tracking-tight text-white sm:text-5xl lg:text-6xl">
                {property.name}
              </h1>

              <div className="mt-5 flex max-w-2xl items-start gap-2 text-sm text-slate-300">

                <MapPin
                  size={17}
                  className="mt-0.5 shrink-0"
                />

                <span>
                  {property.address}

                  {property.stateName
                    ? `, ${property.stateName}`
                    : ""}
                </span>

              </div>

            </div>

          </div>

        </div>

      </section>

      {/* Main content */}
      <main className="mx-auto max-w-7xl px-5 pb-20 lg:px-8">

        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_400px]">

          {/* LEFT CONTENT */}
          <div className="space-y-8">

            {/* About */}
            <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">

              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                About the property
              </p>

              <h2 className="mt-2 text-2xl font-bold text-slate-950">
                {property.name}
              </h2>

              <p className="mt-5 text-base leading-8 text-slate-600">
                {property.description ||
                  "Enjoy a comfortable stay at this verified Travel Buddy property."}
              </p>

            </section>

            {/* Availability */}
            <section>

              <div className="mb-6">

                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                  Availability
                </p>

                <h2 className="mt-2 text-2xl font-bold text-slate-950">
                  Rooms for your stay
                </h2>

                <p className="mt-2 text-sm text-slate-500">
                  Search for available rooms using your dates,
                  guests and room count.
                </p>

              </div>

              {/* Loading availability */}
              {searching && (
                <div className="rounded-3xl border border-slate-200 bg-white p-10 text-center shadow-sm">

                  <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-slate-900" />

                  <p className="mt-4 text-sm font-medium text-slate-600">
                    Checking room availability...
                  </p>

                </div>
              )}

              {/* Availability error */}
              {!searching &&
                availabilityError && (
                  <div className="rounded-3xl border border-amber-200 bg-amber-50 p-6">

                    <div className="flex items-start gap-3">

                      <Search
                        size={20}
                        className="mt-0.5 shrink-0 text-amber-600"
                      />

                      <div>

                        <h3 className="font-semibold text-amber-900">
                          No availability
                        </h3>

                        <p className="mt-1 text-sm leading-6 text-amber-800">
                          {availabilityError}
                        </p>

                      </div>

                    </div>

                  </div>
                )}

              {/* Results */}
              {!searching &&
                availability.length > 0 && (
                  <div className="space-y-4">

                    <div className="rounded-2xl border border-slate-200 bg-white px-5 py-4 shadow-sm">

                      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">

                        <span className="font-semibold text-slate-900">
                          {formatDisplayDate(checkIn)}
                        </span>

                        <span className="text-slate-300">
                          →
                        </span>

                        <span className="font-semibold text-slate-900">
                          {formatDisplayDate(checkOut)}
                        </span>

                        <span className="text-slate-300">
                          •
                        </span>

                        <span className="text-slate-500">
                          {guests}{" "}
                          {guests === 1
                            ? "guest"
                            : "guests"}
                        </span>

                        <span className="text-slate-300">
                          •
                        </span>

                        <span className="text-slate-500">
                          {rooms}{" "}
                          {rooms === 1
                            ? "room"
                            : "rooms"}
                        </span>

                      </div>

                    </div>

                    {availability.map((room) => (
                      <AvailabilityCard
                        key={room.roomTypeId}
                        room={room}
                        onSelect={handleSelectRoom}
                      />
                    ))}

                  </div>
                )}

              {/* Initial state */}
              {!searching &&
                !availabilityError &&
                availability.length === 0 && (
                  <div className="rounded-3xl border border-dashed border-slate-300 bg-white p-10 text-center">

                    <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100">
                      <CalendarDays
                        size={27}
                        className="text-slate-400"
                      />
                    </div>

                    <h3 className="mt-5 text-lg font-bold text-slate-900">
                      Check your dates
                    </h3>

                    <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
                      Select your dates, number of guests and
                      rooms, then check availability.
                    </p>

                  </div>
                )}

            </section>

          </div>

          {/* RIGHT SIDEBAR */}
          <aside>

            <div className="sticky top-24 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100">
                <CalendarDays size={21} />
              </div>

              <h2 className="mt-5 text-xl font-bold text-slate-950">
                Check availability
              </h2>

              <p className="mt-2 text-sm leading-6 text-slate-500">
                Choose your dates and guests to find rooms
                that are available.
              </p>

              <form
                onSubmit={handleAvailabilitySearch}
                className="mt-6 space-y-4"
              >

                {/* Check-in */}
                <div>

                  <label
                    htmlFor="checkIn"
                    className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-500"
                  >
                    Check-in
                  </label>

                  <input
                    id="checkIn"
                    type="date"
                    value={checkIn}
                    min={today}
                    onChange={(event) => {
                      const value =
                        event.target.value;

                      setCheckIn(value);

                      if (
                        checkOut &&
                        value >= checkOut
                      ) {

                        const nextDay =
                          new Date(
                            `${value}T00:00:00`
                          );

                        nextDay.setDate(
                          nextDay.getDate() + 1
                        );

                        setCheckOut(
                          formatDate(nextDay)
                        );
                      }

                      setAvailability([]);
                      setAvailabilityError("");
                    }}
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none transition focus:border-slate-900 focus:bg-white"
                  />

                </div>

                {/* Check-out */}
                <div>

                  <label
                    htmlFor="checkOut"
                    className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-500"
                  >
                    Check-out
                  </label>

                  <input
                    id="checkOut"
                    type="date"
                    value={checkOut}
                    min={
                      checkIn
                        ? getNextDate(checkIn)
                        : today
                    }
                    onChange={(event) => {
                      setCheckOut(
                        event.target.value
                      );

                      setAvailability([]);
                      setAvailabilityError("");
                    }}
                    className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none transition focus:border-slate-900 focus:bg-white"
                  />

                </div>

                {/* Guests + Rooms */}
                <div className="grid grid-cols-2 gap-3">

                  <div>

                    <label
                      htmlFor="guests"
                      className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-500"
                    >
                      Guests
                    </label>

                    <select
                      id="guests"
                      value={guests}
                      onChange={(event) => {
                        setGuests(
                          Number(
                            event.target.value
                          )
                        );

                        setAvailability([]);
                        setAvailabilityError("");
                      }}
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none transition focus:border-slate-900"
                    >

                      {Array.from(
                        { length: 10 },
                        (_, index) =>
                          index + 1
                      ).map((value) => (
                        <option
                          key={value}
                          value={value}
                        >
                          {value}{" "}
                          {value === 1
                            ? "Guest"
                            : "Guests"}
                        </option>
                      ))}

                    </select>

                  </div>

                  <div>

                    <label
                      htmlFor="rooms"
                      className="mb-2 block text-xs font-semibold uppercase tracking-wider text-slate-500"
                    >
                      Rooms
                    </label>

                    <select
                      id="rooms"
                      value={rooms}
                      onChange={(event) => {
                        setRooms(
                          Number(
                            event.target.value
                          )
                        );

                        setAvailability([]);
                        setAvailabilityError("");
                      }}
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none transition focus:border-slate-900"
                    >

                      {Array.from(
                        { length: 5 },
                        (_, index) =>
                          index + 1
                      ).map((value) => (
                        <option
                          key={value}
                          value={value}
                        >
                          {value}{" "}
                          {value === 1
                            ? "Room"
                            : "Rooms"}
                        </option>
                      ))}

                    </select>

                  </div>

                </div>

                {/* Search button */}
                <button
                  type="submit"
                  disabled={searching}
                  className="flex w-full items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 py-3.5 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
                >

                  <Search size={17} />

                  {searching
                    ? "Checking..."
                    : "Check availability"}

                </button>

              </form>

              {/* Occupancy information */}
              <div className="mt-6 rounded-2xl bg-slate-50 p-4">

                <div className="flex items-start gap-3">

                  <Users
                    size={18}
                    className="mt-0.5 shrink-0 text-slate-500"
                  />

                  <div>

                    <div className="text-sm font-semibold text-slate-800">
                      Occupancy rules
                    </div>

                    <div className="mt-1 text-xs leading-5 text-slate-500">
                      Travel Buddy checks the maximum occupancy
                      of each room type and its daily inventory
                      before showing it as available.
                    </div>

                  </div>

                </div>

              </div>

            </div>

          </aside>

        </div>

      </main>

    </div>
  );
}

/*
 * Room availability card.
 */
function AvailabilityCard({
  room,
  onSelect,
}) {
  return (
    <article className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm transition hover:shadow-md">

      <div className="p-5 sm:p-6">

        <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">

          {/* Room information */}
          <div className="flex items-start gap-4">

            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-slate-100">
              <BedDouble size={21} />
            </div>

            <div>

              <h3 className="text-lg font-bold text-slate-950">
                {room.categoryName}
              </h3>

              <div className="mt-2 flex flex-wrap gap-x-4 gap-y-2 text-sm text-slate-500">

                <span className="inline-flex items-center gap-1.5">
                  <Users size={15} />
                  Up to {room.maxOccupancy} guests
                </span>

                <span>
                  {room.availableRooms} available
                </span>

              </div>

            </div>

          </div>

          {/* Price */}
          <div className="sm:text-right">

            <div className="text-2xl font-bold text-slate-950">
              {room.currency}{" "}
              {formatMoney(room.pricePerNight)}
            </div>

            <div className="text-xs text-slate-400">
              per night
            </div>

          </div>

        </div>

        {/* Pricing breakdown */}
        <div className="mt-5 rounded-2xl bg-slate-50 p-4">

          <div className="flex items-center justify-between gap-4 text-sm">

            <span className="text-slate-500">

              {room.numberOfNights}{" "}
              {room.numberOfNights === 1
                ? "night"
                : "nights"}

              {" × "}

              {room.roomsRequested}{" "}
              {room.roomsRequested === 1
                ? "room"
                : "rooms"}

            </span>

            <span className="font-bold text-slate-950">

              {room.currency}{" "}
              {formatMoney(room.totalPrice)}

            </span>

          </div>

          <div className="mt-2 text-xs text-slate-400">
            Taxes and additional charges will be
            calculated during checkout.
          </div>

        </div>

        {/* Inventory status */}
        <div className="mt-4 flex items-center gap-2 text-xs font-medium text-slate-500">

          <span className="h-2 w-2 rounded-full bg-emerald-500" />

          Available for all selected nights

        </div>

        {/* Continue */}
        <button
          type="button"
          onClick={() => onSelect(room)}
          className="mt-5 flex w-full items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 py-3.5 text-sm font-semibold text-white transition hover:bg-slate-700 active:scale-[0.99]"
        >
          Continue to booking
        </button>

      </div>

    </article>
  );
}
