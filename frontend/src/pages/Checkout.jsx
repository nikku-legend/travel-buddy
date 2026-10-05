import { Link, useLocation, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import {
  ArrowLeft,
  BedDouble,
  CalendarDays,
  CheckCircle2,
  CreditCard,
  Loader2,
  MapPin,
  ShieldCheck,
  Users,
  AlertCircle,
  Clock3,
  XCircle,
  LockKeyhole,
} from "lucide-react";

import bookingService from "../services/bookingService";
import { useAuth } from "../context/useAuth";

function displayDate(value) {
  if (!value) return "—";

  const date = new Date(`${value}T00:00:00`);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(date);
}

function money(value, currency = "INR") {
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(Number(value || 0));
  } catch {
    return `${currency} ${Number(value || 0).toFixed(2)}`;
  }
}

function displayDateTime(value) {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function errorMessage(error, fallback) {
  const data = error?.response?.data;

  if (typeof data === "string") {
    return data;
  }

  return (
    data?.message ||
    data?.error ||
    data?.detail ||
    error?.message ||
    fallback
  );
}

function BookingConfirmed({ booking }) {
  return (
    <div className="mx-auto max-w-3xl">

      <div className="overflow-hidden rounded-[2rem] border border-emerald-200 bg-white shadow-xl">

        <div className="bg-gradient-to-br from-emerald-950 via-emerald-900 to-teal-900 px-6 py-10 text-center text-white sm:px-10">

          <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-white/10 ring-8 ring-white/5">
            <CheckCircle2 size={42} />
          </div>

          <p className="mt-7 text-xs font-bold uppercase tracking-[0.25em] text-emerald-200">
            Payment successful
          </p>

          <h1 className="mt-3 text-3xl font-black sm:text-4xl">
            Your stay is confirmed
          </h1>

          <p className="mx-auto mt-4 max-w-xl text-sm leading-6 text-emerald-100/80">
            Your payment was successfully verified by the Travel Buddy
            backend and your reservation is now confirmed.
          </p>

        </div>

        <div className="p-6 sm:p-8">

          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5">

            <div className="flex items-center justify-between gap-4">

              <div>
                <p className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Booking reference
                </p>

                <p className="mt-1 text-lg font-black text-slate-950">
                  {booking.bookingReference}
                </p>
              </div>

              <span className="rounded-full bg-emerald-100 px-3 py-1.5 text-xs font-bold text-emerald-700">
                CONFIRMED
              </span>

            </div>

            <div className="mt-6 grid gap-5 sm:grid-cols-2">

              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Property
                </p>

                <p className="mt-1 font-bold text-slate-950">
                  {booking.propertyName}
                </p>
              </div>

              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Room
                </p>

                <p className="mt-1 font-bold text-slate-950">
                  {booking.roomTypeName}
                </p>
              </div>

              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Check-in
                </p>

                <p className="mt-1 font-bold text-slate-950">
                  {displayDate(booking.checkIn)}
                </p>
              </div>

              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Check-out
                </p>

                <p className="mt-1 font-bold text-slate-950">
                  {displayDate(booking.checkOut)}
                </p>
              </div>

              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Guests
                </p>

                <p className="mt-1 font-bold text-slate-950">
                  {booking.guests}
                </p>
              </div>

              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Rooms
                </p>

                <p className="mt-1 font-bold text-slate-950">
                  {booking.rooms}
                </p>
              </div>

            </div>

            <div className="mt-6 flex items-center justify-between border-t border-slate-200 pt-5">

              <span className="font-semibold text-slate-600">
                Total paid
              </span>

              <strong className="text-2xl font-black text-emerald-700">
                {money(
                  booking.totalAmount,
                  booking.currency
                )}
              </strong>

            </div>

          </div>

          <div className="mt-7 grid gap-3 sm:grid-cols-2">

            <Link
              to={`/bookings/${booking.bookingId}`}
              className="
                inline-flex
                items-center
                justify-center
                rounded-xl
                bg-slate-950
                px-5
                py-3.5
                text-sm
                font-bold
                text-white
                hover:bg-slate-800
              "
            >
              View booking details
            </Link>

            <Link
              to="/bookings"
              className="
                inline-flex
                items-center
                justify-center
                rounded-xl
                border
                border-slate-200
                px-5
                py-3.5
                text-sm
                font-bold
                text-slate-700
                hover:bg-slate-50
              "
            >
              My Bookings
            </Link>

          </div>

        </div>

      </div>

    </div>
  );
}

function MockPaymentPanel({
  booking,
  onSuccess,
  onFailure,
}) {
  const [processing, setProcessing] =
    useState(false);

  const [error, setError] =
    useState("");

  async function pay(success) {
    if (processing) {
      return;
    }

    try {
      setProcessing(true);
      setError("");

      const response =
        await bookingService.verifyMockPayment(
          booking.bookingId,
          success
        );

      if (success) {
        onSuccess(response);
      } else {
        onFailure(response);
      }

    } catch (requestError) {
      setError(
        errorMessage(
          requestError,
          "Payment could not be processed."
        )
      );
    } finally {
      setProcessing(false);
    }
  }

  return (
    <div className="rounded-3xl border border-slate-200 bg-white shadow-sm">

      <div className="rounded-t-3xl bg-gradient-to-br from-slate-950 to-teal-950 px-6 py-7 text-white">

        <p className="text-xs font-bold uppercase tracking-[0.2em] text-teal-200">
          Development payment
        </p>

        <h2 className="mt-3 text-2xl font-black">
          Complete your payment
        </h2>

        <p className="mt-2 text-sm leading-6 text-white/70">
          No real money is charged. This is the development payment
          gateway used until production Razorpay is enabled.
        </p>

      </div>

      <div className="p-6 sm:p-8">

        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5">

          <div className="flex items-center justify-between gap-4">

            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Amount
              </p>

              <p className="mt-1 text-sm text-slate-500">
                Booking #{booking.bookingId}
              </p>
            </div>

            <strong className="text-2xl font-black text-slate-950">
              {money(
                booking.totalAmount,
                booking.currency
              )}
            </strong>

          </div>

        </div>

        {booking.holdExpiresAt && (
          <div className="mt-5 flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4">

            <Clock3
              size={19}
              className="mt-0.5 shrink-0 text-amber-700"
            />

            <div>

              <p className="text-sm font-bold text-amber-900">
                Room temporarily held
              </p>

              <p className="mt-1 text-xs leading-5 text-amber-800">
                Hold expires at{" "}
                {displayDateTime(
                  booking.holdExpiresAt
                )}
              </p>

            </div>

          </div>
        )}

        {error && (
          <div className="mt-5 flex gap-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">

            <AlertCircle
              size={18}
              className="shrink-0"
            />

            <span>{error}</span>

          </div>
        )}

        <div className="mt-7 grid gap-3 sm:grid-cols-2">

          <button
            type="button"
            disabled={processing}
            onClick={() => pay(false)}
            className="
              inline-flex
              items-center
              justify-center
              gap-2
              rounded-2xl
              border
              border-slate-200
              px-5
              py-4
              text-sm
              font-bold
              text-slate-700
              hover:bg-slate-50
              disabled:opacity-50
            "
          >
            <XCircle size={18} />
            Simulate failure
          </button>

          <button
            type="button"
            disabled={processing}
            onClick={() => pay(true)}
            className="
              inline-flex
              items-center
              justify-center
              gap-2
              rounded-2xl
              bg-slate-950
              px-5
              py-4
              text-sm
              font-bold
              text-white
              hover:bg-slate-800
              disabled:opacity-50
            "
          >
            {processing ? (
              <>
                <Loader2
                  size={18}
                  className="animate-spin"
                />
                Processing...
              </>
            ) : (
              <>
                <CheckCircle2 size={18} />
                Pay successfully
              </>
            )}
          </button>

        </div>

        <div className="mt-7 grid gap-3 sm:grid-cols-3">

          <div className="rounded-2xl bg-slate-50 p-4">
            <ShieldCheck size={18} />
            <p className="mt-2 text-xs font-bold text-slate-900">
              Backend verified
            </p>
          </div>

          <div className="rounded-2xl bg-slate-50 p-4">
            <LockKeyhole size={18} />
            <p className="mt-2 text-xs font-bold text-slate-900">
              No real charge
            </p>
          </div>

          <div className="rounded-2xl bg-slate-50 p-4">
            <Clock3 size={18} />
            <p className="mt-2 text-xs font-bold text-slate-900">
              Hold protected
            </p>
          </div>

        </div>

      </div>

    </div>
  );
}

function PaymentFailed({
  booking,
}) {
  return (
    <div className="mx-auto max-w-2xl rounded-3xl border border-amber-200 bg-white p-8 text-center shadow-sm">

      <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-amber-100 text-amber-700">
        <XCircle size={34} />
      </div>

      <h1 className="mt-6 text-3xl font-black text-slate-950">
        Payment was not completed
      </h1>

      <p className="mt-3 text-sm leading-6 text-slate-500">
        The development payment failed and the backend cancelled
        this booking and released its held inventory.
      </p>

      <div className="mt-7 rounded-2xl bg-slate-50 p-5 text-left">

        <div className="flex justify-between gap-4 text-sm">
          <span className="text-slate-500">
            Booking
          </span>

          <strong>
            {booking.bookingReference}
          </strong>
        </div>

        <div className="mt-4 flex justify-between gap-4 text-sm">
          <span className="text-slate-500">
            Status
          </span>

          <strong className="text-rose-600">
            {booking.bookingStatus}
          </strong>
        </div>

      </div>

      <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">

        <Link
          to="/stays"
          className="rounded-xl bg-slate-950 px-5 py-3 text-sm font-bold text-white hover:bg-slate-800"
        >
          Search another stay
        </Link>

        <Link
          to="/bookings"
          className="rounded-xl border border-slate-200 px-5 py-3 text-sm font-bold text-slate-700 hover:bg-slate-50"
        >
          My bookings
        </Link>

      </div>

    </div>
  );
}

function ExistingBookingPayment({
  booking,
  onSuccess,
  onFailure,
}) {
  return (
    <div className="mx-auto max-w-4xl">

      <div className="mb-7">

        <p className="text-xs font-bold uppercase tracking-[0.2em] text-teal-600">
          Travel Buddy · Payment
        </p>

        <h1 className="mt-2 text-3xl font-black text-slate-950 sm:text-4xl">
          Complete your booking
        </h1>

        <p className="mt-2 text-sm leading-6 text-slate-500">
          Your existing booking is already holding the selected room.
        </p>

      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">

        <MockPaymentPanel
          booking={booking}
          onSuccess={onSuccess}
          onFailure={onFailure}
        />

        <aside className="h-fit rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

          <p className="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">
            Reservation
          </p>

          <h2 className="mt-2 text-xl font-black text-slate-950">
            {booking.propertyName}
          </h2>

          <div className="mt-6 space-y-4 border-y border-slate-100 py-5">

            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Room
              </p>

              <p className="mt-1 text-sm font-bold">
                {booking.roomTypeName}
              </p>
            </div>

            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Dates
              </p>

              <p className="mt-1 text-sm font-bold">
                {displayDate(
                  booking.checkIn
                )}
              </p>

              <p className="text-xs text-slate-500">
                to{" "}
                {displayDate(
                  booking.checkOut
                )}
              </p>
            </div>

            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Guests
              </p>

              <p className="mt-1 text-sm font-bold">
                {booking.guests}
              </p>
            </div>

          </div>

          <div className="mt-5 flex items-end justify-between">

            <span className="text-sm font-semibold text-slate-500">
              Total
            </span>

            <strong className="text-2xl font-black">
              {money(
                booking.totalAmount,
                booking.currency
              )}
            </strong>

          </div>

        </aside>

      </div>

    </div>
  );
}

export default function Checkout() {
  const location =
    useLocation();

  const navigate =
    useNavigate();

  const {
    user,
    loading: authLoading,
  } = useAuth();

  const existingBooking =
    location.state?.existingBooking;

  const selection =
    location.state;

  const [
    booking,
    setBooking,
  ] = useState(
    existingBooking || null
  );

  const [
    failure,
    setFailure,
  ] = useState(null);

  const [
    submitting,
    setSubmitting,
  ] = useState(false);

  const [
    error,
    setError,
  ] = useState("");

  useEffect(() => {
    if (existingBooking) {
      setBooking(
        existingBooking
      );
    }
  }, [existingBooking]);

  /*
   * ----------------------------------------------------------
   * EXISTING BOOKING PAYMENT
   * ----------------------------------------------------------
   */

  if (booking) {

    if (
      booking.bookingStatus ===
        "CONFIRMED" &&
      booking.paymentStatus ===
        "PAID"
    ) {
      return (
        <div className="min-h-screen bg-slate-50 px-5 py-12">
          <BookingConfirmed
            booking={booking}
          />
        </div>
      );
    }

    if (
      failure ||
      booking.bookingStatus ===
        "CANCELLED"
    ) {
      return (
        <div className="min-h-screen bg-slate-50 px-5 py-12">
          <PaymentFailed
            booking={
              failure || booking
            }
          />
        </div>
      );
    }

    return (
      <div className="min-h-screen bg-slate-50 px-5 py-12 sm:py-16">

        <ExistingBookingPayment
          booking={booking}
          onSuccess={(result) => {
            setFailure(null);
            setBooking(result);
          }}
          onFailure={(result) => {
            setFailure(result);
            setBooking(result);
          }}
        />

      </div>
    );
  }

  /*
   * ----------------------------------------------------------
   * NEW BOOKING CHECKOUT
   * ----------------------------------------------------------
   */

  if (
    !selection?.room ||
    !selection?.property
  ) {
    return (
      <div className="min-h-screen bg-slate-50 px-5 py-20 text-center">

        <h1 className="text-3xl font-black text-slate-950">
          Choose a room first
        </h1>

        <p className="mt-3 text-sm text-slate-500">
          Select an available room before entering checkout.
        </p>

        <Link
          to="/stays"
          className="mt-7 inline-flex rounded-xl bg-slate-950 px-5 py-3 text-sm font-bold text-white"
        >
          Browse stays
        </Link>

      </div>
    );
  }

  const checkIn =
    new Date(
      `${selection.checkIn}T00:00:00`
    );

  const checkOut =
    new Date(
      `${selection.checkOut}T00:00:00`
    );

  const nights = Math.max(
    1,
    Math.round(
      (checkOut - checkIn) /
        86400000
    )
  );

  async function submit(event) {
    event.preventDefault();

    if (!user) {
      navigate("/login", {
        state: {
          from: "/checkout",
          checkoutSelection:
            selection,
        },
      });

      return;
    }

    if (
      !selection.guestName?.trim() ||
      !selection.guestEmail?.trim() ||
      !selection.guestPhone?.trim()
    ) {
      setError(
        "Please complete all required guest information."
      );

      return;
    }

    try {
      setSubmitting(true);
      setError("");

      const response =
        await bookingService.createHotelBooking({
          roomTypeId:
            selection.room.roomTypeId,

          checkIn:
            selection.checkIn,

          checkOut:
            selection.checkOut,

          guests:
            selection.guests,

          rooms:
            selection.rooms,

          guestName:
            selection.guestName.trim(),

          guestEmail:
            selection.guestEmail.trim(),

          guestPhone:
            selection.guestPhone.trim(),

          specialRequests:
            selection.specialRequests ||
            null,
        });

      setBooking(response);

    } catch (requestError) {
      setError(
        errorMessage(
          requestError,
          "Unable to create the booking."
        )
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 py-10">

      <div className="mx-auto max-w-6xl px-5">

        <button
          type="button"
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-2 text-sm font-bold text-slate-500 hover:text-slate-950"
        >
          <ArrowLeft size={16} />
          Back
        </button>

        <div className="mt-7">

          <p className="text-xs font-bold uppercase tracking-[0.2em] text-teal-600">
            Travel Buddy Checkout
          </p>

          <h1 className="mt-2 text-3xl font-black text-slate-950">
            Review your stay
          </h1>

        </div>

        <form
          onSubmit={submit}
          className="mt-8 grid gap-6 lg:grid-cols-[1fr_350px]"
        >

          <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

            <h2 className="text-xl font-black text-slate-950">
              Guest information
            </h2>

            {error && (
              <div className="mt-5 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                {error}
              </div>
            )}

            <div className="mt-7 grid gap-5 sm:grid-cols-2">

              <label className="sm:col-span-2">
                <span className="mb-2 block text-sm font-bold text-slate-700">
                  Guest name
                </span>

                <input
                  required
                  name="guestName"
                  defaultValue={
                    user?.fullName || ""
                  }
                  onChange={(event) => {
                    selection.guestName =
                      event.target.value;
                  }}
                  className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-slate-900"
                  placeholder="Full name"
                />
              </label>

              <label>
                <span className="mb-2 block text-sm font-bold text-slate-700">
                  Email
                </span>

                <input
                  required
                  type="email"
                  name="guestEmail"
                  defaultValue={
                    user?.email || ""
                  }
                  onChange={(event) => {
                    selection.guestEmail =
                      event.target.value;
                  }}
                  className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-slate-900"
                  placeholder="Email"
                />
              </label>

              <label>
                <span className="mb-2 block text-sm font-bold text-slate-700">
                  Phone
                </span>

                <input
                  required
                  type="tel"
                  name="guestPhone"
                  onChange={(event) => {
                    selection.guestPhone =
                      event.target.value;
                  }}
                  className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-slate-900"
                  placeholder="Phone"
                />
              </label>

              <label className="sm:col-span-2">

                <span className="mb-2 block text-sm font-bold text-slate-700">
                  Special requests
                </span>

                <textarea
                  rows={4}
                  onChange={(event) => {
                    selection.specialRequests =
                      event.target.value;
                  }}
                  className="w-full resize-none rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-slate-900"
                  placeholder="Optional"
                />

              </label>

            </div>

            <div className="mt-7 rounded-2xl border border-teal-100 bg-teal-50 p-4">

              <div className="flex gap-3">

                <Clock3
                  size={19}
                  className="mt-0.5 text-teal-700"
                />

                <div>

                  <p className="text-sm font-bold text-teal-900">
                    Temporary booking hold
                  </p>

                  <p className="mt-1 text-xs leading-5 text-teal-800">
                    Your room will be held for the payment period after
                    the backend creates the booking.
                  </p>

                </div>

              </div>

            </div>

            <button
              type="submit"
              disabled={
                submitting ||
                authLoading
              }
              className="mt-6 flex w-full items-center justify-center gap-2 rounded-2xl bg-slate-950 px-5 py-4 text-sm font-bold text-white hover:bg-slate-800 disabled:opacity-50"
            >

              {submitting ? (
                <>
                  <Loader2
                    size={18}
                    className="animate-spin"
                  />
                  Creating booking...
                </>
              ) : (
                <>
                  <CreditCard size={18} />
                  Hold room and continue
                </>
              )}

            </button>

          </div>

          <aside className="h-fit rounded-3xl border border-slate-200 bg-white p-6 shadow-sm lg:sticky lg:top-24">

            <p className="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">
              Stay summary
            </p>

            <h2 className="mt-2 text-xl font-black text-slate-950">
              {selection.property.name}
            </h2>

            <p className="mt-2 flex gap-2 text-sm text-slate-500">
              <MapPin size={16} />
              {selection.property.address}
            </p>

            <div className="mt-6 space-y-5 border-y border-slate-100 py-5">

              <div className="flex gap-3">
                <BedDouble size={18} />

                <div>
                  <p className="text-xs font-bold uppercase text-slate-400">
                    Room
                  </p>

                  <p className="mt-1 text-sm font-bold">
                    {selection.room.categoryName}
                  </p>
                </div>
              </div>

              <div className="flex gap-3">
                <CalendarDays size={18} />

                <div>
                  <p className="text-xs font-bold uppercase text-slate-400">
                    Dates
                  </p>

                  <p className="mt-1 text-sm font-bold">
                    {displayDate(
                      selection.checkIn
                    )}
                  </p>

                  <p className="text-xs text-slate-500">
                    to{" "}
                    {displayDate(
                      selection.checkOut
                    )}
                  </p>
                </div>
              </div>

              <div className="flex gap-3">
                <Users size={18} />

                <div>
                  <p className="text-xs font-bold uppercase text-slate-400">
                    Guests
                  </p>

                  <p className="mt-1 text-sm font-bold">
                    {selection.guests}
                  </p>
                </div>
              </div>

            </div>

            <div className="mt-5 flex items-end justify-between gap-4">

              <div>
                <p className="text-sm font-bold">
                  Total
                </p>

                <p className="text-xs text-slate-400">
                  {nights}{" "}
                  {nights === 1
                    ? "night"
                    : "nights"}
                </p>
              </div>

              <strong className="text-2xl font-black">
                {money(
                  selection.room.totalPrice,
                  selection.room.currency ||
                    "INR"
                )}
              </strong>

            </div>

          </aside>

        </form>

      </div>

    </div>
  );
}