import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  Link,
  useNavigate,
  useParams,
} from "react-router-dom";

import {
  ArrowLeft,
  Ban,
  BedDouble,
  CalendarDays,
  Check,
  CheckCircle2,
  Clock3,
  CreditCard,
  Hotel,
  Loader2,
  LockKeyhole,
  Mail,
  MapPin,
  ReceiptText,
  RefreshCw,
  ShieldCheck,
  UserRound,
  Users,
  X,
  XCircle,
} from "lucide-react";

import bookingService from "../services/bookingService";
import { useAuth } from "../context/useAuth";

/* ============================================================
   HELPERS
   ============================================================ */

function normalize(value) {
  return String(value ?? "")
    .trim()
    .toUpperCase();
}

function formatDate(value) {
  if (!value) {
    return "—";
  }

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

function formatDateTime(value) {
  if (!value) {
    return "—";
  }

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

function formatCurrency(
  amount,
  currency = "INR"
) {
  const numericAmount = Number(amount ?? 0);

  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(numericAmount);
  } catch {
    return `${currency} ${numericAmount.toFixed(2)}`;
  }
}

function getErrorMessage(
  error,
  fallback = "Something went wrong."
) {
  const data = error?.response?.data;

  if (typeof data === "string") {
    return data;
  }

  if (data && typeof data === "object") {
    return (
      data.message ||
      data.error ||
      data.detail ||
      fallback
    );
  }

  return error?.message || fallback;
}

function getCancellationReasonLabel(
  reason
) {
  const labels = {
    CHANGE_OF_PLANS: "Change of plans",
    FOUND_ANOTHER_STAY:
      "Found another stay",
    TRAVEL_DATE_CHANGED:
      "Travel date changed",
    TRIP_CANCELLED:
      "Trip cancelled",
    BOOKED_BY_MISTAKE:
      "Booked by mistake",
    PRICE_CONCERN:
      "Price concern",
    PERSONAL_REASON:
      "Personal reason",
    OTHER: "Other",
  };

  return (
    labels[normalize(reason)] ||
    reason ||
    "—"
  );
}

/* ============================================================
   STATUS BADGES
   ============================================================ */

function BookingStatusBadge({
  status,
}) {
  const value = normalize(status);

  if (value === "CONFIRMED") {
    return (
      <span className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-bold text-emerald-700">
        <CheckCircle2 size={14} />
        Confirmed
      </span>
    );
  }

  if (value === "PENDING") {
    return (
      <span className="inline-flex items-center gap-2 rounded-full border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs font-bold text-amber-700">
        <Clock3 size={14} />
        Pending
      </span>
    );
  }

  if (value === "CANCELLED") {
    return (
      <span className="inline-flex items-center gap-2 rounded-full border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-bold text-rose-700">
        <XCircle size={14} />
        Cancelled
      </span>
    );
  }

  if (value === "COMPLETED") {
    return (
      <span className="inline-flex items-center gap-2 rounded-full border border-blue-200 bg-blue-50 px-3 py-1.5 text-xs font-bold text-blue-700">
        <CheckCircle2 size={14} />
        Completed
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-bold text-slate-600">
      <Clock3 size={14} />
      {status || "Unknown"}
    </span>
  );
}

function PaymentStatusBadge({
  status,
}) {
  const value = normalize(status);

  if (value === "PAID") {
    return (
      <span className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-bold text-emerald-700">
        <CreditCard size={14} />
        Paid
      </span>
    );
  }

  if (value === "REFUNDED") {
    return (
      <span className="inline-flex items-center gap-2 rounded-full border border-purple-200 bg-purple-50 px-3 py-1.5 text-xs font-bold text-purple-700">
        <RefreshCw size={14} />
        Refunded
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs font-bold text-amber-700">
      <CreditCard size={14} />
      Unpaid
    </span>
  );
}

/* ============================================================
   LOADING
   ============================================================ */

function LoadingPage() {
  return (
    <main className="min-h-screen bg-slate-50">

      <div className="mx-auto max-w-7xl px-5 py-10 lg:px-8">

        <div className="animate-pulse">

          <div className="h-4 w-28 rounded bg-slate-200" />

          <div className="mt-6 h-10 w-80 max-w-full rounded bg-slate-200" />

          <div className="mt-3 h-4 w-96 max-w-full rounded bg-slate-100" />

          <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">

            <div className="space-y-5">
              <div className="h-64 rounded-3xl bg-slate-200" />
              <div className="h-72 rounded-3xl bg-slate-200" />
              <div className="h-60 rounded-3xl bg-slate-200" />
            </div>

            <div className="h-96 rounded-3xl bg-slate-200" />

          </div>

        </div>

      </div>

    </main>
  );
}

/* ============================================================
   ERROR
   ============================================================ */

function ErrorState({
  message,
  onRetry,
}) {
  return (
    <main className="min-h-screen bg-slate-50 px-5 py-20">

      <div className="mx-auto max-w-xl rounded-3xl border border-rose-200 bg-white p-8 text-center shadow-sm">

        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-rose-50 text-rose-600">
          <XCircle size={28} />
        </div>

        <h1 className="mt-6 text-2xl font-black text-slate-950">
          Booking unavailable
        </h1>

        <p className="mt-3 text-sm leading-6 text-slate-500">
          {message ||
            "We couldn't load this booking."}
        </p>

        <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">

          <button
            type="button"
            onClick={onRetry}
            className="
              inline-flex
              items-center
              justify-center
              gap-2
              rounded-xl
              bg-slate-950
              px-5
              py-3
              text-sm
              font-bold
              text-white
              transition
              hover:bg-slate-800
            "
          >
            <RefreshCw size={16} />
            Try again
          </button>

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
              py-3
              text-sm
              font-bold
              text-slate-700
              transition
              hover:bg-slate-50
            "
          >
            My bookings
          </Link>

        </div>

      </div>

    </main>
  );
}

/* ============================================================
   COUNTDOWN
   ============================================================ */

function HoldCountdown({
  expiresAt,
  onExpired,
}) {
  const calculateRemaining = useCallback(() => {
    if (!expiresAt) {
      return 0;
    }

    const expiry =
      new Date(expiresAt).getTime();

    const now = Date.now();

    return Math.max(
      0,
      expiry - now
    );
  }, [expiresAt]);

  const [remaining, setRemaining] =
    useState(calculateRemaining);

  useEffect(() => {
    setRemaining(calculateRemaining());

    const timer = window.setInterval(() => {
      const next =
        calculateRemaining();

      setRemaining(next);

      if (next <= 0) {
        window.clearInterval(timer);

        if (onExpired) {
          onExpired();
        }
      }
    }, 1000);

    return () =>
      window.clearInterval(timer);
  }, [
    calculateRemaining,
    onExpired,
  ]);

  if (!expiresAt) {
    return null;
  }

  if (remaining <= 0) {
    return (
      <div className="flex items-center gap-2 rounded-xl bg-rose-100 px-3 py-2 text-xs font-bold text-rose-700">
        <XCircle size={15} />
        Hold expired
      </div>
    );
  }

  const totalSeconds =
    Math.floor(remaining / 1000);

  const minutes =
    Math.floor(totalSeconds / 60);

  const seconds =
    totalSeconds % 60;

  return (
    <div className="flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-bold text-amber-700">

      <Clock3 size={15} />

      <span>
        Payment hold:{" "}
        {String(minutes).padStart(
          2,
          "0"
        )}
        :
        {String(seconds).padStart(
          2,
          "0"
        )}
      </span>

    </div>
  );
}

/* ============================================================
   BOOKING TIMELINE
   ============================================================ */

function BookingTimeline({
  booking,
}) {
  const bookingStatus =
    normalize(
      booking.bookingStatus
    );

  const paymentStatus =
    normalize(
      booking.paymentStatus
    );

  const cancelled =
    bookingStatus === "CANCELLED";

  const paid =
    paymentStatus === "PAID" ||
    paymentStatus === "REFUNDED";

  const confirmed =
    bookingStatus === "CONFIRMED" ||
    bookingStatus === "COMPLETED";

  const steps = [
    {
      title: "Booking created",
      description:
        formatDateTime(
          booking.createdAt
        ),
      state: "complete",
    },
    {
      title: "Payment",
      description: paid
        ? "Payment received"
        : cancelled
          ? "Payment not completed"
          : "Payment pending",
      state: paid
        ? "complete"
        : cancelled
          ? "cancelled"
          : "pending",
    },
    {
      title: "Reservation",
      description: confirmed
        ? "Reservation confirmed"
        : cancelled
          ? "Reservation cancelled"
          : "Waiting for payment",
      state: confirmed
        ? "complete"
        : cancelled
          ? "cancelled"
          : "pending",
    },
  ];

  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-7">

      <div className="flex items-center gap-3">

        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-950 text-white">
          <ReceiptText size={20} />
        </div>

        <div>

          <h2 className="font-black text-slate-950">
            Booking timeline
          </h2>

          <p className="mt-1 text-xs text-slate-500">
            Current lifecycle of this reservation.
          </p>

        </div>

      </div>

      <div className="mt-7">

        {steps.map(
          (step, index) => {
            const last =
              index ===
              steps.length - 1;

            return (
              <div
                key={step.title}
                className="relative flex gap-4"
              >

                {!last && (
                  <div className="absolute left-5 top-10 h-[calc(100%-8px)] w-px bg-slate-200" />
                )}

                <div
                  className={`
                    relative
                    z-10
                    flex
                    h-10
                    w-10
                    shrink-0
                    items-center
                    justify-center
                    rounded-full
                    ${
                      step.state ===
                      "complete"
                        ? "bg-emerald-100 text-emerald-700"
                        : step.state ===
                            "cancelled"
                          ? "bg-rose-100 text-rose-700"
                          : "bg-amber-100 text-amber-700"
                    }
                  `}
                >

                  {step.state ===
                  "complete" ? (
                    <Check size={18} />
                  ) : step.state ===
                    "cancelled" ? (
                    <X size={18} />
                  ) : (
                    <Clock3 size={18} />
                  )}

                </div>

                <div
                  className={
                    last
                      ? "pb-1"
                      : "pb-7"
                  }
                >

                  <p className="text-sm font-bold text-slate-950">
                    {step.title}
                  </p>

                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    {step.description}
                  </p>

                </div>

              </div>
            );
          }
        )}

      </div>

    </section>
  );
}

/* ============================================================
   STAY INFORMATION
   ============================================================ */

function StayInformation({
  booking,
}) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-7">

      <div className="flex items-center gap-3">

        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-800">
          <Hotel size={20} />
        </div>

        <div>

          <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">
            Accommodation
          </p>

          <h2 className="mt-1 text-xl font-black text-slate-950">
            {booking.propertyName}
          </h2>

        </div>

      </div>

      <div className="mt-7 grid gap-4 sm:grid-cols-2">

        <InfoItem
          icon={<BedDouble size={18} />}
          label="Room"
          value={booking.roomTypeName}
        />

        <InfoItem
          icon={<Users size={18} />}
          label="Guests"
          value={`${booking.guests} ${
            booking.guests === 1
              ? "guest"
              : "guests"
          }`}
        />

        <InfoItem
          icon={<CalendarDays size={18} />}
          label="Check-in"
          value={formatDate(
            booking.checkIn
          )}
        />

        <InfoItem
          icon={<CalendarDays size={18} />}
          label="Check-out"
          value={formatDate(
            booking.checkOut
          )}
        />

        <InfoItem
          icon={<Clock3 size={18} />}
          label="Duration"
          value={`${booking.numberOfNights} ${
            booking.numberOfNights === 1
              ? "night"
              : "nights"
          }`}
        />

        <InfoItem
          icon={<BedDouble size={18} />}
          label="Rooms"
          value={`${booking.rooms} ${
            booking.rooms === 1
              ? "room"
              : "rooms"
          }`}
        />

      </div>

    </section>
  );
}

function InfoItem({
  icon,
  label,
  value,
}) {
  return (
    <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4">

      <div className="flex items-start gap-3">

        <div className="mt-0.5 text-slate-500">
          {icon}
        </div>

        <div className="min-w-0">

          <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            {label}
          </p>

          <p className="mt-1 break-words text-sm font-bold text-slate-900">
            {value || "—"}
          </p>

        </div>

      </div>

    </div>
  );
}

/* ============================================================
   PAYMENT INFORMATION
   ============================================================ */

function PaymentInformation({
  booking,
}) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-7">

      <div className="flex items-center gap-3">

        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-800">
          <CreditCard size={20} />
        </div>

        <div>

          <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">
            Payment
          </p>

          <h2 className="mt-1 text-xl font-black text-slate-950">
            Payment information
          </h2>

        </div>

      </div>

      <div className="mt-7 rounded-2xl border border-slate-100 bg-slate-50 p-5">

        <div className="flex items-center justify-between gap-4">

          <span className="text-sm text-slate-500">
            Payment status
          </span>

          <PaymentStatusBadge
            status={
              booking.paymentStatus
            }
          />

        </div>

        <div className="mt-5 flex items-center justify-between gap-4 border-t border-slate-200 pt-5">

          <span className="text-sm font-semibold text-slate-600">
            Total amount
          </span>

          <strong className="text-2xl font-black text-slate-950">
            {formatCurrency(
              booking.totalAmount,
              booking.currency
            )}
          </strong>

        </div>

      </div>

      <div className="mt-5 flex items-start gap-3 rounded-2xl border border-slate-200 bg-white p-4">

        <ShieldCheck
          size={19}
          className="mt-0.5 shrink-0 text-slate-700"
        />

        <div>

          <p className="text-sm font-bold text-slate-900">
            Payment is controlled by the backend
          </p>

          <p className="mt-1 text-xs leading-5 text-slate-500">
            Booking confirmation is only shown after the backend
            updates the booking and payment status.
          </p>

        </div>

      </div>

    </section>
  );
}

/* ============================================================
   GUEST INFORMATION
   ============================================================ */

function GuestInformation({
  booking,
}) {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-7">

      <div className="flex items-center gap-3">

        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-800">
          <UserRound size={20} />
        </div>

        <div>

          <p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">
            Reservation
          </p>

          <h2 className="mt-1 text-xl font-black text-slate-950">
            Guest information
          </h2>

        </div>

      </div>

      <div className="mt-7 grid gap-4 sm:grid-cols-2">

        <InfoItem
          icon={<UserRound size={18} />}
          label="Booking reference"
          value={
            booking.bookingReference
          }
        />

        <InfoItem
          icon={<Users size={18} />}
          label="Guests"
          value={`${booking.guests} ${
            booking.guests === 1
              ? "guest"
              : "guests"
          }`}
        />

      </div>

    </section>
  );
}

/* ============================================================
   CANCELLATION INFORMATION
   ============================================================ */

function CancellationInformation({
  cancellation,
}) {
  if (!cancellation) {
    return null;
  }

  return (
    <section className="rounded-3xl border border-rose-200 bg-white p-6 shadow-sm sm:p-7">

      <div className="flex items-center gap-3">

        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-rose-50 text-rose-600">
          <Ban size={20} />
        </div>

        <div>

          <p className="text-xs font-bold uppercase tracking-[0.18em] text-rose-500">
            Cancellation
          </p>

          <h2 className="mt-1 text-xl font-black text-slate-950">
            Cancellation details
          </h2>

        </div>

      </div>

      <div className="mt-7 space-y-4">

        <div className="rounded-2xl bg-rose-50 p-4">

          <p className="text-xs font-semibold uppercase tracking-wider text-rose-500">
            Reason
          </p>

          <p className="mt-1 text-sm font-bold text-rose-900">
            {getCancellationReasonLabel(
              cancellation.cancellationReason
            )}
          </p>

        </div>

        {cancellation.cancellationNote && (
          <div className="rounded-2xl bg-slate-50 p-4">

            <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Note
            </p>

            <p className="mt-1 text-sm leading-6 text-slate-700">
              {cancellation.cancellationNote}
            </p>

          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-2">

          <InfoItem
            icon={<CalendarDays size={18} />}
            label="Cancelled at"
            value={formatDateTime(
              cancellation.cancelledAt
            )}
          />

          <InfoItem
            icon={<RefreshCw size={18} />}
            label="Refund status"
            value={
              cancellation.refundStatus ||
              "—"
            }
          />

          <InfoItem
            icon={<CreditCard size={18} />}
            label="Refund amount"
            value={
              cancellation.refundAmount != null
                ? formatCurrency(
                    cancellation.refundAmount,
                    cancellation.refundCurrency ||
                      "INR"
                  )
                : "—"
            }
          />

          <InfoItem
            icon={<ReceiptText size={18} />}
            label="Refund reference"
            value={
              cancellation.refundReference ||
              "Not processed yet"
            }
          />

        </div>

      </div>

    </section>
  );
}

/* ============================================================
   ACTION PANEL
   ============================================================ */

function ActionPanel({
  booking,
  onCancel,
  onPayment,
  holdExpired,
}) {
  const status =
    normalize(booking.bookingStatus);

  const payment =
    normalize(booking.paymentStatus);

  const canPay =
    status === "PENDING" &&
    payment === "UNPAID" &&
    !holdExpired;

  const canCancel =
    status === "PENDING" ||
    status === "CONFIRMED";

  return (
    <aside className="h-fit rounded-3xl border border-slate-200 bg-white p-6 shadow-sm lg:sticky lg:top-24">

      <p className="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">
        Reservation
      </p>

      <p className="mt-2 break-all text-lg font-black text-slate-950">
        {booking.bookingReference}
      </p>

      <div className="mt-6 space-y-4">

        <div className="flex items-center justify-between gap-3">

          <span className="text-sm text-slate-500">
            Booking
          </span>

          <BookingStatusBadge
            status={
              booking.bookingStatus
            }
          />

        </div>

        <div className="flex items-center justify-between gap-3">

          <span className="text-sm text-slate-500">
            Payment
          </span>

          <PaymentStatusBadge
            status={
              booking.paymentStatus
            }
          />

        </div>

      </div>

      <div className="my-6 border-t border-slate-100" />

      <div>

        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
          Total
        </p>

        <p className="mt-1 text-3xl font-black text-slate-950">
          {formatCurrency(
            booking.totalAmount,
            booking.currency
          )}
        </p>

      </div>

      {canPay && (
        <div className="mt-6">

          <button
            type="button"
            onClick={onPayment}
            className="
              flex
              w-full
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
              shadow-lg
              shadow-slate-950/10
              transition
              hover:bg-slate-800
            "
          >
            <CreditCard size={18} />
            Continue payment
          </button>

        </div>
      )}

      {status === "PENDING" &&
        payment === "UNPAID" &&
        holdExpired && (
          <div className="mt-6 rounded-2xl border border-rose-200 bg-rose-50 p-4">

            <div className="flex items-start gap-3">

              <XCircle
                size={18}
                className="mt-0.5 shrink-0 text-rose-600"
              />

              <div>

                <p className="text-sm font-bold text-rose-900">
                  Payment hold expired
                </p>

                <p className="mt-1 text-xs leading-5 text-rose-700">
                  This booking can no longer be paid.
                  The backend will release the held inventory.
                </p>

              </div>

            </div>

          </div>
        )}

      {canCancel && (
        <button
          type="button"
          onClick={onCancel}
          className="
            mt-3
            flex
            w-full
            items-center
            justify-center
            gap-2
            rounded-2xl
            border
            border-rose-200
            bg-white
            px-5
            py-3.5
            text-sm
            font-bold
            text-rose-600
            transition
            hover:bg-rose-50
          "
        >
          <Ban size={17} />
          Cancel booking
        </button>
      )}

      {status === "CANCELLED" && (
        <div className="mt-6 rounded-2xl border border-rose-200 bg-rose-50 p-4">

          <p className="text-sm font-bold text-rose-900">
            Booking cancelled
          </p>

          <p className="mt-1 text-xs leading-5 text-rose-700">
            This reservation can no longer be paid or cancelled again.
          </p>

        </div>
      )}

      {status === "COMPLETED" && (
        <div className="mt-6 rounded-2xl border border-blue-200 bg-blue-50 p-4">

          <p className="text-sm font-bold text-blue-900">
            Trip completed
          </p>

          <p className="mt-1 text-xs leading-5 text-blue-700">
            Review and rating functionality will be connected in the
            later Reviews & Ratings phase.
          </p>

        </div>
      )}

    </aside>
  );
}

/* ============================================================
   CANCELLATION MODAL
   ============================================================ */

function CancellationModal({
  onClose,
  onConfirm,
  submitting,
  error,
}) {
  const [
    reason,
    setReason,
  ] = useState(
    "CHANGE_OF_PLANS"
  );

  const [
    note,
    setNote,
  ] = useState("");

  function submit(event) {
    event.preventDefault();

    onConfirm({
      reason,
      note,
    });
  }

  return (
    <div className="fixed inset-0 z-[100] flex items-end justify-center bg-slate-950/60 p-0 backdrop-blur-sm sm:items-center sm:p-5">

      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-t-3xl bg-white shadow-2xl sm:rounded-3xl">

        <div className="sticky top-0 flex items-center justify-between border-b border-slate-100 bg-white px-6 py-5">

          <div>

            <p className="text-xs font-bold uppercase tracking-[0.18em] text-rose-500">
              Cancellation
            </p>

            <h2 className="mt-1 text-xl font-black text-slate-950">
              Cancel this booking?
            </h2>

          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="flex h-10 w-10 items-center justify-center rounded-full text-slate-500 transition hover:bg-slate-100 hover:text-slate-950 disabled:opacity-50"
          >
            <X size={20} />
          </button>

        </div>

        <form
          onSubmit={submit}
          className="p-6"
        >

          <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4">

            <p className="text-sm font-bold text-amber-900">
              Please confirm your cancellation
            </p>

            <p className="mt-1 text-xs leading-5 text-amber-800">
              Cancelling a confirmed paid booking creates the refund
              foundation record. Actual payment-provider refund
              processing will be connected later.
            </p>

          </div>

          {error && (
            <div className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
              {error}
            </div>
          )}

          <label className="mt-6 block">

            <span className="mb-2 block text-sm font-bold text-slate-700">
              Cancellation reason
            </span>

            <select
              value={reason}
              onChange={(event) =>
                setReason(
                  event.target.value
                )
              }
              disabled={submitting}
              className="
                w-full
                rounded-xl
                border
                border-slate-200
                bg-white
                px-4
                py-3
                text-sm
                text-slate-900
                outline-none
                focus:border-slate-900
                focus:ring-4
                focus:ring-slate-900/5
              "
            >

              <option value="CHANGE_OF_PLANS">
                Change of plans
              </option>

              <option value="FOUND_ANOTHER_STAY">
                Found another stay
              </option>

              <option value="TRAVEL_DATE_CHANGED">
                Travel date changed
              </option>

              <option value="TRIP_CANCELLED">
                Trip cancelled
              </option>

              <option value="BOOKED_BY_MISTAKE">
                Booked by mistake
              </option>

              <option value="PRICE_CONCERN">
                Price concern
              </option>

              <option value="PERSONAL_REASON">
                Personal reason
              </option>

              <option value="OTHER">
                Other
              </option>

            </select>

          </label>

          <label className="mt-5 block">

            <span className="mb-2 block text-sm font-bold text-slate-700">
              Additional note
              <span className="ml-1 font-normal text-slate-400">
                (optional)
              </span>
            </span>

            <textarea
              value={note}
              onChange={(event) =>
                setNote(
                  event.target.value
                )
              }
              disabled={submitting}
              rows={4}
              maxLength={500}
              className="
                w-full
                resize-none
                rounded-xl
                border
                border-slate-200
                bg-white
                px-4
                py-3
                text-sm
                text-slate-900
                outline-none
                focus:border-slate-900
                focus:ring-4
                focus:ring-slate-900/5
              "
              placeholder="Tell us why you are cancelling..."
            />

          </label>

          <div className="mt-7 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">

            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="
                rounded-xl
                border
                border-slate-200
                px-5
                py-3
                text-sm
                font-bold
                text-slate-700
                transition
                hover:bg-slate-50
                disabled:opacity-50
              "
            >
              Keep booking
            </button>

            <button
              type="submit"
              disabled={submitting}
              className="
                inline-flex
                items-center
                justify-center
                gap-2
                rounded-xl
                bg-rose-600
                px-5
                py-3
                text-sm
                font-bold
                text-white
                transition
                hover:bg-rose-700
                disabled:cursor-not-allowed
                disabled:opacity-60
              "
            >

              {submitting ? (
                <>
                  <Loader2
                    size={17}
                    className="animate-spin"
                  />
                  Cancelling...
                </>
              ) : (
                <>
                  <Ban size={17} />
                  Confirm cancellation
                </>
              )}

            </button>

          </div>

        </form>

      </div>

    </div>
  );
}

/* ============================================================
   MAIN
   ============================================================ */

export default function BookingDetails() {
  const {
    bookingId,
  } = useParams();

  const navigate =
    useNavigate();

  const {
    user,
    loading: authLoading,
  } = useAuth();

  const [
    booking,
    setBooking,
  ] = useState(null);

  const [
    cancellation,
    setCancellation,
  ] = useState(null);

  const [
    loading,
    setLoading,
  ] = useState(true);

  const [
    error,
    setError,
  ] = useState("");

  const [
    cancellationError,
    setCancellationError,
  ] = useState("");

  const [
    cancellationOpen,
    setCancellationOpen,
  ] = useState(false);

  const [
    cancellationSubmitting,
    setCancellationSubmitting,
  ] = useState(false);

  const [
    holdExpired,
    setHoldExpired,
  ] = useState(false);

  /* ==========================================================
     LOAD BOOKING
     ========================================================== */

  const loadBooking =
    useCallback(async () => {
      if (!bookingId) {
        setError(
          "Booking ID is missing."
        );

        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setError("");

        const data =
          await bookingService.getBooking(
            bookingId
          );

        setBooking(data);

        if (
          normalize(
            data?.bookingStatus
          ) === "PENDING" &&
          normalize(
            data?.paymentStatus
          ) === "UNPAID" &&
          data?.holdExpiresAt
        ) {
          setHoldExpired(
            new Date(
              data.holdExpiresAt
            ).getTime() <= Date.now()
          );
        } else {
          setHoldExpired(false);
        }

        try {
          const cancellationData =
            await bookingService.getCancellation(
              bookingId
            );

          setCancellation(
            cancellationData || null
          );
        } catch {
          /*
           * A booking without cancellation history is valid.
           * Do not make the entire Booking Details page fail.
           */
          setCancellation(null);
        }

      } catch (requestError) {
        setError(
          getErrorMessage(
            requestError,
            "We couldn't load this booking."
          )
        );
      } finally {
        setLoading(false);
      }
    }, [bookingId]);

  useEffect(() => {
    if (!authLoading && user) {
      loadBooking();
    }

    if (!authLoading && !user) {
      setLoading(false);
    }
  }, [
    authLoading,
    user,
    loadBooking,
  ]);

  /* ==========================================================
     PAYMENT ACTION
     ========================================================== */

  function continuePayment() {
    if (!booking) {
      return;
    }

    /*
     * Reuse the premium Checkout screen with
     * an already-created booking.
     *
     * The current development flow keeps the actual
     * mock payment action on Checkout.
     */
    navigate("/checkout", {
      state: {
        existingBooking: booking,
      },
    });
  }

  /* ==========================================================
     * CANCEL
     ========================================================== */

  async function confirmCancellation(
    cancellationData
  ) {
    if (!booking) {
      return;
    }

    try {
      setCancellationSubmitting(
        true
      );

      setCancellationError("");

      await bookingService.cancelBooking(
        booking.bookingId,
        cancellationData
      );

      /*
       * Reload from backend rather than
       * manually mutating booking status.
       *
       * This keeps the UI synchronized with
       * the actual database state.
       */
      await loadBooking();

      setCancellationOpen(false);

    } catch (requestError) {
      setCancellationError(
        getErrorMessage(
          requestError,
          "The booking could not be cancelled."
        )
      );
    } finally {
      setCancellationSubmitting(
        false
      );
    }
  }

  /* ==========================================================
     * HOLD EXPIRED
     * ========================================================== */

  const handleHoldExpired =
    useCallback(() => {
      setHoldExpired(true);

      /*
       * Reload after the timer expires so the UI
       * receives the authoritative backend status.
       */
      loadBooking();
    }, [loadBooking]);

  /* ==========================================================
     * DERIVED STATE
     * ========================================================== */

  const isPending =
    normalize(
      booking?.bookingStatus
    ) === "PENDING";

  const isPaid =
    normalize(
      booking?.paymentStatus
    ) === "PAID";

  const isCancelled =
    normalize(
      booking?.bookingStatus
    ) === "CANCELLED";

  const isConfirmed =
    normalize(
      booking?.bookingStatus
    ) === "CONFIRMED";

  const showPayment =
    isPending &&
    !isPaid &&
    !isCancelled;

  const pageTitle =
    useMemo(() => {
      if (!booking) {
        return "Booking Details";
      }

      if (isCancelled) {
        return "Cancelled booking";
      }

      if (isConfirmed) {
        return "Your stay is confirmed";
      }

      if (isPending) {
        return "Complete your booking";
      }

      return "Your trip";
    }, [
      booking,
      isCancelled,
      isConfirmed,
      isPending,
    ]);

  /* ==========================================================
     AUTH
     ========================================================== */

  if (authLoading) {
    return <LoadingPage />;
  }

  if (!user) {
    return (
      <main className="min-h-screen bg-slate-50 px-5 py-20">

        <div className="mx-auto max-w-xl rounded-3xl border border-slate-200 bg-white p-8 text-center shadow-sm">

          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-slate-100 text-slate-700">
            <LockKeyhole size={28} />
          </div>

          <h1 className="mt-6 text-2xl font-black text-slate-950">
            Sign in required
          </h1>

          <p className="mt-3 text-sm leading-6 text-slate-500">
            Please sign in to view your booking details.
          </p>

          <Link
            to="/login"
            className="
              mt-6
              inline-flex
              items-center
              justify-center
              rounded-xl
              bg-slate-950
              px-5
              py-3
              text-sm
              font-bold
              text-white
              transition
              hover:bg-slate-800
            "
          >
            Sign in
          </Link>

        </div>

      </main>
    );
  }

  /* ==========================================================
     LOADING
     ========================================================== */

  if (loading) {
    return <LoadingPage />;
  }

  /* ==========================================================
     ERROR
     ========================================================== */

  if (!booking) {
    return (
      <ErrorState
        message={error}
        onRetry={loadBooking}
      />
    );
  }

  /* ==========================================================
     PAGE
     ========================================================== */

  return (
    <main className="min-h-screen bg-slate-50">

      <div className="mx-auto max-w-7xl px-5 py-8 lg:px-8 lg:py-10">

        {/* ====================================================
            TOP NAVIGATION
           ==================================================== */}

        <div className="flex flex-wrap items-center justify-between gap-4">

          <button
            type="button"
            onClick={() =>
              navigate("/bookings")
            }
            className="
              inline-flex
              items-center
              gap-2
              text-sm
              font-bold
              text-slate-500
              transition
              hover:text-slate-950
            "
          >
            <ArrowLeft size={17} />
            My bookings
          </button>

          <button
            type="button"
            onClick={loadBooking}
            className="
              inline-flex
              items-center
              gap-2
              rounded-xl
              border
              border-slate-200
              bg-white
              px-4
              py-2.5
              text-xs
              font-bold
              text-slate-600
              shadow-sm
              transition
              hover:bg-slate-50
            "
          >
            <RefreshCw size={14} />
            Refresh
          </button>

        </div>

        {/* ====================================================
            HEADER
           ==================================================== */}

        <header className="mt-8">

          <div className="flex flex-col justify-between gap-5 md:flex-row md:items-end">

            <div>

              <p className="text-xs font-bold uppercase tracking-[0.22em] text-teal-600">
                Travel Buddy · My Trip
              </p>

              <h1 className="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
                {pageTitle}
              </h1>

              <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-500">
                Manage your reservation, payment status,
                cancellation and stay information from one place.
              </p>

            </div>

            <div className="flex flex-wrap gap-2">

              <BookingStatusBadge
                status={
                  booking.bookingStatus
                }
              />

              <PaymentStatusBadge
                status={
                  booking.paymentStatus
                }
              />

            </div>

          </div>

        </header>

        {/* ====================================================
            PENDING PAYMENT BANNER
           ==================================================== */}

        {showPayment && (
          <section className="mt-7 overflow-hidden rounded-3xl border border-amber-200 bg-white shadow-sm">

            <div className="bg-gradient-to-r from-amber-950 via-amber-900 to-orange-900 px-6 py-6 text-white sm:px-7">

              <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">

                <div>

                  <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-amber-200">
                    <Clock3 size={15} />
                    Payment required
                  </div>

                  <h2 className="mt-2 text-xl font-black">
                    Your room is temporarily held
                  </h2>

                  <p className="mt-2 max-w-xl text-sm leading-6 text-amber-100/80">
                    Complete the development payment before the hold expires.
                    The booking will become CONFIRMED + PAID after successful
                    backend verification.
                  </p>

                </div>

                <div className="shrink-0">

                  <HoldCountdown
                    expiresAt={
                      booking.holdExpiresAt
                    }
                    onExpired={
                      handleHoldExpired
                    }
                  />

                </div>

              </div>

            </div>

            <div className="flex flex-col gap-4 px-6 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-7">

              <div>

                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Amount payable
                </p>

                <p className="mt-1 text-2xl font-black text-slate-950">
                  {formatCurrency(
                    booking.totalAmount,
                    booking.currency
                  )}
                </p>

              </div>

              <button
                type="button"
                disabled={holdExpired}
                onClick={continuePayment}
                className="
                  inline-flex
                  items-center
                  justify-center
                  gap-2
                  rounded-2xl
                  bg-slate-950
                  px-6
                  py-3.5
                  text-sm
                  font-bold
                  text-white
                  transition
                  hover:bg-slate-800
                  disabled:cursor-not-allowed
                  disabled:opacity-50
                "
              >
                <CreditCard size={18} />
                Continue payment
              </button>

            </div>

          </section>
        )}

        {/* ====================================================
            MAIN GRID
           ==================================================== */}

        <div className="mt-7 grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">

          {/* LEFT */}
          <div className="space-y-6">

            <StayInformation
              booking={booking}
            />

            <GuestInformation
              booking={booking}
            />

            <PaymentInformation
              booking={booking}
            />

            <BookingTimeline
              booking={booking}
            />

            <CancellationInformation
              cancellation={
                cancellation
              }
            />

            {/* Future features */}
            {(isConfirmed ||
              normalize(
                booking.bookingStatus
              ) === "COMPLETED") && (
              <section className="rounded-3xl border border-dashed border-slate-300 bg-white p-6 shadow-sm sm:p-7">

                <div className="flex items-start gap-3">

                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                    <Hotel size={19} />
                  </div>

                  <div>

                    <h2 className="font-black text-slate-950">
                      Your complete trip will grow here
                    </h2>

                    <p className="mt-2 text-sm leading-6 text-slate-500">
                      Guide booking, physical room assignment,
                      vouchers, QR verification and the unified trip
                      builder are later Travel Buddy modules.
                    </p>

                  </div>

                </div>

              </section>
            )}

          </div>

          {/* RIGHT */}
          <ActionPanel
            booking={booking}
            onPayment={continuePayment}
            onCancel={() => {
              setCancellationError("");
              setCancellationOpen(true);
            }}
            holdExpired={
              holdExpired
            }
          />

        </div>

      </div>

      {/* ======================================================
          CANCELLATION MODAL
         ====================================================== */}

      {cancellationOpen && (
        <CancellationModal
          onClose={() => {
            if (
              !cancellationSubmitting
            ) {
              setCancellationOpen(
                false
              );
            }
          }}
          onConfirm={
            confirmCancellation
          }
          submitting={
            cancellationSubmitting
          }
          error={
            cancellationError
          }
        />
      )}

    </main>
  );
}