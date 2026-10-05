import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  Clock3,
  CreditCard,
  Hotel,
  Search,
  SlidersHorizontal,
  XCircle,
  RotateCcw,
  ChevronRight,
  MapPin,
  ReceiptText,
  Ban,
  ShieldCheck,
  RefreshCcw,
  AlertTriangle,
  Loader2,
} from "lucide-react";

import bookingService from "../services/bookingService";
import { useAuth } from "../context/useAuth";

/* =========================================================
   FORMATTERS
========================================================= */

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

function formatCurrency(amount, currency = "INR") {
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

function normalizeStatus(value) {
  return String(value ?? "")
    .trim()
    .toUpperCase();
}

/* =========================================================
   DATE / CANCELLATION HELPERS
========================================================= */

function getTodayDateString() {
  const now = new Date();

  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

function canCancelBooking(booking) {
  if (!booking) {
    return false;
  }

  const status = normalizeStatus(booking.bookingStatus);

  if (
    status !== "PENDING" &&
    status !== "CONFIRMED"
  ) {
    return false;
  }

  if (!booking.checkIn) {
    return false;
  }

  const today = getTodayDateString();

  return booking.checkIn > today;
}

function isCheckInStartedOrPassed(booking) {
  if (!booking?.checkIn) {
    return false;
  }

  return booking.checkIn <= getTodayDateString();
}

function getCancellationReasonLabel(reason) {
  const labels = {
    CHANGE_OF_PLANS: "Change of plans",
    FOUND_ANOTHER_STAY: "Found another stay",
    TRAVEL_DATE_CHANGED: "Travel date changed",
    TRIP_CANCELLED: "Trip cancelled",
    BOOKED_BY_MISTAKE: "Booked by mistake",
    PRICE_CONCERN: "Price concern",
    PERSONAL_REASON: "Personal reason",
    OTHER: "Other",
  };

  return (
    labels[normalizeStatus(reason)] ||
    reason ||
    "Not specified"
  );
}

function getErrorMessage(error, fallback) {
  const data = error?.response?.data;

  if (typeof data === "string") {
    return data;
  }

  if (data?.message) {
    return data.message;
  }

  if (error?.message) {
    return error.message;
  }

  return fallback;
}

/* =========================================================
   BOOKING STATUS
========================================================= */

function getBookingStatusMeta(status) {
  const normalized = normalizeStatus(status);

  switch (normalized) {
    case "CONFIRMED":
      return {
        label: "Confirmed",
        icon: CheckCircle2,
        wrapper:
          "border-emerald-200 bg-emerald-50 text-emerald-700",
        dot: "bg-emerald-500",
      };

    case "PENDING":
      return {
        label: "Pending",
        icon: Clock3,
        wrapper:
          "border-amber-200 bg-amber-50 text-amber-700",
        dot: "bg-amber-500",
      };

    case "CANCELLED":
      return {
        label: "Cancelled",
        icon: XCircle,
        wrapper:
          "border-rose-200 bg-rose-50 text-rose-700",
        dot: "bg-rose-500",
      };

    case "COMPLETED":
      return {
        label: "Completed",
        icon: CheckCircle2,
        wrapper:
          "border-blue-200 bg-blue-50 text-blue-700",
        dot: "bg-blue-500",
      };

    default:
      return {
        label: status || "Unknown",
        icon: Clock3,
        wrapper:
          "border-slate-200 bg-slate-50 text-slate-600",
        dot: "bg-slate-400",
      };
  }
}

function getPaymentStatusMeta(status) {
  const normalized = normalizeStatus(status);

  switch (normalized) {
    case "PAID":
      return {
        label: "Paid",
        wrapper:
          "border-emerald-200 bg-emerald-50 text-emerald-700",
      };

    case "REFUNDED":
      return {
        label: "Refunded",
        wrapper:
          "border-purple-200 bg-purple-50 text-purple-700",
      };

    case "UNPAID":
      return {
        label: "Unpaid",
        wrapper:
          "border-amber-200 bg-amber-50 text-amber-700",
      };

    default:
      return {
        label: status || "Unknown",
        wrapper:
          "border-slate-200 bg-slate-50 text-slate-600",
      };
  }
}

function StatusBadge({ status }) {
  const meta = getBookingStatusMeta(status);
  const Icon = meta.icon;

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold ${meta.wrapper}`}
    >
      <Icon size={13} />
      {meta.label}
    </span>
  );
}

function PaymentBadge({ status }) {
  const meta = getPaymentStatusMeta(status);

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold ${meta.wrapper}`}
    >
      <CreditCard size={13} />
      {meta.label}
    </span>
  );
}

/* =========================================================
   CANCELLATION MODAL
========================================================= */

function CancellationModal({
  booking,
  onClose,
  onConfirm,
  submitting,
  error,
}) {
  const [reason, setReason] = useState("");
  const [note, setNote] = useState("");

  const reasons = [
    {
      value: "CHANGE_OF_PLANS",
      label: "Change of plans",
    },
    {
      value: "FOUND_ANOTHER_STAY",
      label: "Found another stay",
    },
    {
      value: "TRAVEL_DATE_CHANGED",
      label: "Travel date changed",
    },
    {
      value: "TRIP_CANCELLED",
      label: "Trip cancelled",
    },
    {
      value: "BOOKED_BY_MISTAKE",
      label: "Booked by mistake",
    },
    {
      value: "PRICE_CONCERN",
      label: "Price concern",
    },
    {
      value: "PERSONAL_REASON",
      label: "Personal reason",
    },
    {
      value: "OTHER",
      label: "Other",
    },
  ];

  function handleSubmit(event) {
    event.preventDefault();

    if (!reason) {
      return;
    }

    onConfirm({
      reason,
      note: note.trim() || null,
    });
  }

  if (!booking) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm">
      <div className="relative flex max-h-[92vh] w-full max-w-xl flex-col overflow-hidden rounded-[2rem] border border-white/20 bg-white shadow-2xl">

        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-100 p-5 sm:p-6">
          <div className="flex items-start gap-3">

            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-rose-100 text-rose-600">
              <Ban size={21} />
            </div>

            <div>
              <h2 className="text-lg font-black text-slate-950">
                Cancel booking
              </h2>

              <p className="mt-1 text-xs font-semibold text-slate-400">
                {booking.bookingReference
                  ? booking.bookingReference
                  : "Reservation"}
              </p>
            </div>

          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded-xl p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40"
            aria-label="Close"
          >
            <XCircle size={21} />
          </button>
        </div>

        {/* Body */}
        <div className="overflow-y-auto p-5 sm:p-6">

          {error && (
            <div className="mb-5 flex items-start gap-3 rounded-2xl border border-rose-200 bg-rose-50 p-4">

              <AlertTriangle
                size={18}
                className="mt-0.5 shrink-0 text-rose-600"
              />

              <div>
                <p className="text-sm font-bold text-rose-900">
                  Cancellation failed
                </p>

                <p className="mt-1 text-xs leading-5 text-rose-700">
                  {error}
                </p>
              </div>

            </div>
          )}

          {/* Booking summary */}
          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">

            <div className="flex items-start justify-between gap-4">

              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Stay
                </p>

                <p className="mt-1 text-sm font-bold text-slate-900">
                  {booking.propertyName ||
                    "Hotel reservation"}
                </p>
              </div>

              <p className="text-base font-black text-slate-950">
                {formatCurrency(
                  booking.totalAmount,
                  booking.currency || "INR"
                )}
              </p>

            </div>

            <div className="mt-4 grid gap-3 sm:grid-cols-2">

              <div>
                <p className="text-xs text-slate-400">
                  Check-in
                </p>

                <p className="mt-1 text-sm font-bold text-slate-800">
                  {formatDate(booking.checkIn)}
                </p>
              </div>

              <div>
                <p className="text-xs text-slate-400">
                  Check-out
                </p>

                <p className="mt-1 text-sm font-bold text-slate-800">
                  {formatDate(booking.checkOut)}
                </p>
              </div>

            </div>

          </div>

          {/* Reason */}
          <form
            onSubmit={handleSubmit}
            className="mt-5"
          >

            <label
              htmlFor="cancellation-reason"
              className="block text-sm font-bold text-slate-900"
            >
              Why are you cancelling?
            </label>

            <select
              id="cancellation-reason"
              value={reason}
              onChange={(event) =>
                setReason(event.target.value)
              }
              disabled={submitting}
              className="mt-2 h-12 w-full rounded-xl border border-slate-200 bg-white px-4 text-sm font-medium text-slate-800 outline-none transition focus:border-slate-400 disabled:bg-slate-50"
            >
              <option value="">
                Select a reason
              </option>

              {reasons.map((item) => (
                <option
                  key={item.value}
                  value={item.value}
                >
                  {item.label}
                </option>
              ))}
            </select>

            {/* Note */}
            <label
              htmlFor="cancellation-note"
              className="mt-5 block text-sm font-bold text-slate-900"
            >
              Additional note
              <span className="ml-1 font-normal text-slate-400">
                (optional)
              </span>
            </label>

            <textarea
              id="cancellation-note"
              value={note}
              onChange={(event) =>
                setNote(event.target.value)
              }
              disabled={submitting}
              maxLength={500}
              rows={4}
              placeholder="Tell us anything else about your cancellation..."
              className="mt-2 w-full resize-none rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-slate-400 disabled:bg-slate-50"
            />

            <div className="mt-1 text-right text-[11px] text-slate-400">
              {note.length}/500
            </div>

            {/* Warning */}
            <div className="mt-5 flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4">

              <AlertTriangle
                size={18}
                className="mt-0.5 shrink-0 text-amber-600"
              />

              <div>
                <p className="text-sm font-bold text-amber-900">
                  Please review before cancelling
                </p>

                <p className="mt-1 text-xs leading-5 text-amber-700">
                  Cancellation releases the reserved room
                  inventory. Once cancelled, this booking
                  cannot be restored from this screen.
                </p>
              </div>

            </div>

            {/* Actions */}
            <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">

              <button
                type="button"
                onClick={onClose}
                disabled={submitting}
                className="inline-flex h-12 items-center justify-center rounded-xl border border-slate-200 px-5 text-sm font-bold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Keep booking
              </button>

              <button
                type="submit"
                disabled={!reason || submitting}
                className="inline-flex h-12 items-center justify-center gap-2 rounded-xl bg-rose-600 px-5 text-sm font-bold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {submitting ? (
                  <>
                    <Loader2
                      size={16}
                      className="animate-spin"
                    />
                    Cancelling...
                  </>
                ) : (
                  <>
                    <Ban size={16} />
                    Confirm cancellation
                  </>
                )}
              </button>

            </div>

          </form>

        </div>
      </div>
    </div>
  );
}

/* =========================================================
   CANCELLATION DETAILS
========================================================= */

function CancellationSummary({
  cancellation,
  booking,
}) {
  if (!cancellation) {
    return null;
  }

  const refundStatus =
    normalizeStatus(cancellation.refundStatus);

  const isRefundPending =
    refundStatus === "PENDING" ||
    refundStatus === "PROCESSING";

  const isRefundCompleted =
    refundStatus === "COMPLETED";

  const isRefundFailed =
    refundStatus === "FAILED";

  return (
    <div className="mt-5 rounded-3xl border border-rose-200 bg-white p-5 shadow-sm sm:p-6">

      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">

        <div className="flex items-start gap-3">

          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-rose-100 text-rose-600">
            <XCircle size={21} />
          </div>

          <div>
            <h3 className="text-base font-black text-slate-950">
              Cancellation details
            </h3>

            <p className="mt-1 text-xs text-slate-500">
              Cancelled{" "}
              {formatDateTime(
                cancellation.cancelledAt
              )}
            </p>
          </div>

        </div>

        <span className="inline-flex w-fit items-center rounded-full border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-bold text-rose-700">
          Cancelled
        </span>

      </div>

      <div className="mt-5 grid gap-3 sm:grid-cols-2">

        <div className="rounded-2xl bg-slate-50 p-4">

          <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Reason
          </p>

          <p className="mt-2 text-sm font-bold text-slate-800">
            {getCancellationReasonLabel(
              cancellation.cancellationReason
            )}
          </p>

        </div>

        <div className="rounded-2xl bg-slate-50 p-4">

          <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Cancelled at
          </p>

          <p className="mt-2 text-sm font-bold text-slate-800">
            {formatDateTime(
              cancellation.cancelledAt
            )}
          </p>

        </div>

      </div>

      {cancellation.cancellationNote && (
        <div className="mt-3 rounded-2xl border border-slate-200 bg-white p-4">

          <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Your note
          </p>

          <p className="mt-2 text-sm leading-6 text-slate-700">
            {cancellation.cancellationNote}
          </p>

        </div>
      )}

      {/* Refund */}
      <div className="mt-3 rounded-2xl border border-slate-200 bg-slate-50 p-4">

        <div className="flex items-start gap-3">

          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-slate-700 shadow-sm">
            <RefreshCcw size={18} />
          </div>

          <div className="min-w-0 flex-1">

            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">

              <div>
                <p className="text-sm font-bold text-slate-900">
                  Refund status
                </p>

                <p className="mt-1 text-xs text-slate-500">
                  {refundStatus === "NOT_APPLICABLE"
                    ? "No refund is required for this booking."
                    : refundStatus === "PENDING"
                      ? "Refund has been queued for processing."
                      : refundStatus === "PROCESSING"
                        ? "Refund is currently being processed."
                        : refundStatus === "COMPLETED"
                          ? "Refund has been completed."
                          : refundStatus === "FAILED"
                            ? "Refund processing failed."
                            : "Refund status is being updated."}
                </p>
              </div>

              <span
                className={`inline-flex w-fit rounded-full border px-3 py-1.5 text-xs font-bold ${
                  refundStatus === "NOT_APPLICABLE"
                    ? "border-slate-200 bg-white text-slate-600"
                    : isRefundCompleted
                      ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                      : isRefundFailed
                        ? "border-rose-200 bg-rose-50 text-rose-700"
                        : "border-amber-200 bg-amber-50 text-amber-700"
                }`}
              >
                {cancellation.refundStatus ||
                  "Unknown"}
              </span>

            </div>

            {cancellation.refundAmount != null && (
              <div className="mt-4 flex items-center justify-between border-t border-slate-200 pt-4">

                <span className="text-sm font-semibold text-slate-500">
                  Refund amount
                </span>

                <span className="text-lg font-black text-slate-950">
                  {formatCurrency(
                    cancellation.refundAmount,
                    cancellation.refundCurrency ||
                      booking?.currency ||
                      "INR"
                  )}
                </span>

              </div>
            )}

            {cancellation.refundReference && (
              <div className="mt-3 text-xs text-slate-400">
                Refund reference:{" "}
                <span className="font-semibold text-slate-600">
                  {cancellation.refundReference}
                </span>
              </div>
            )}

          </div>

        </div>

      </div>

    </div>
  );
}

/* =========================================================
   CANCELLATION UNAVAILABLE
========================================================= */

function CancellationUnavailable({ booking }) {
  if (
    !booking ||
    !isCheckInStartedOrPassed(booking)
  ) {
    return null;
  }

  const status = normalizeStatus(
    booking.bookingStatus
  );

  if (
    status !== "PENDING" &&
    status !== "CONFIRMED"
  ) {
    return null;
  }

  return (
    <div className="mt-5 flex items-start gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4">

      <Clock3
        size={18}
        className="mt-0.5 shrink-0 text-slate-500"
      />

      <div className="flex-1">

        <p className="text-sm font-bold text-slate-800">
          Cancellation unavailable
        </p>

        <p className="mt-1 text-xs leading-5 text-slate-500">
          Cancellation is no longer available because
          your check-in date has started or passed.
        </p>

      </div>

    </div>
  );
}

/* =========================================================
   BOOKING CARD
========================================================= */

function BookingCard({
  booking,
  cancellation,
  onCancel,
}) {
  const bookingStatus = normalizeStatus(
    booking.bookingStatus
  );

  const paymentStatus = normalizeStatus(
    booking.paymentStatus
  );

  const isPending =
    bookingStatus === "PENDING";

  const isConfirmed =
    bookingStatus === "CONFIRMED";

  const isCompleted =
    bookingStatus === "COMPLETED";

  const isCancelled =
    bookingStatus === "CANCELLED";

  const cancellationAllowed =
    canCancelBooking(booking);

  return (
    <article className="group overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm transition duration-300 hover:-translate-y-1 hover:border-slate-300 hover:shadow-xl">

      {/* Top accent */}
      <div
        className={`h-1 w-full ${
          isCancelled
            ? "bg-rose-500"
            : isConfirmed
              ? "bg-emerald-500"
              : isPending
                ? "bg-amber-500"
                : "bg-slate-950"
        }`}
      />

      <div className="p-5 sm:p-6">

        {/* Header */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">

          <div className="flex min-w-0 items-start gap-4">

            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-slate-950 text-white shadow-sm">
              <Hotel size={21} />
            </div>

            <div className="min-w-0">

              <div className="flex flex-wrap items-center gap-2">

                <p className="text-xs font-bold uppercase tracking-[0.14em] text-slate-400">
                  Booking
                </p>

                {booking.bookingReference && (
                  <span className="text-xs font-bold text-slate-700">
                    #{booking.bookingReference}
                  </span>
                )}

              </div>

              <h2 className="mt-1 truncate text-lg font-bold text-slate-950 sm:text-xl">
                {booking.propertyName ||
                  "Hotel reservation"}
              </h2>

              {booking.roomTypeName && (
                <p className="mt-1 truncate text-sm text-slate-500">
                  {booking.roomTypeName}
                </p>
              )}

            </div>

          </div>

          <div className="flex flex-wrap gap-2 sm:justify-end">

            <StatusBadge
              status={booking.bookingStatus}
            />

            <PaymentBadge
              status={booking.paymentStatus}
            />

          </div>

        </div>

        {/* Details */}
        <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">

          <div className="rounded-2xl bg-slate-50 p-4">

            <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
              <CalendarDays size={14} />
              Check-in
            </div>

            <p className="mt-2 text-sm font-bold text-slate-900">
              {formatDate(booking.checkIn)}
            </p>

          </div>

          <div className="rounded-2xl bg-slate-50 p-4">

            <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
              <CalendarDays size={14} />
              Check-out
            </div>

            <p className="mt-2 text-sm font-bold text-slate-900">
              {formatDate(booking.checkOut)}
            </p>

          </div>

          <div className="rounded-2xl bg-slate-50 p-4">

            <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
              <Hotel size={14} />
              Rooms
            </div>

            <p className="mt-2 text-sm font-bold text-slate-900">
              {booking.rooms ?? "—"}
            </p>

          </div>

          <div className="rounded-2xl bg-slate-50 p-4">

            <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
              <ReceiptText size={14} />
              Total
            </div>

            <p className="mt-2 text-sm font-bold text-slate-900">
              {formatCurrency(
                booking.totalAmount,
                booking.currency || "INR"
              )}
            </p>

          </div>

        </div>

        {/* Pending warning */}
        {isPending && (
          <div className="mt-5 flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 p-4">

            <Clock3
              size={18}
              className="mt-0.5 shrink-0 text-amber-600"
            />

            <div>
              <p className="text-sm font-bold text-amber-900">
                Payment required
              </p>

              <p className="mt-1 text-xs leading-5 text-amber-700">
                This booking is currently pending.
                Open the booking to continue payment
                before the room hold expires.
              </p>
            </div>

          </div>
        )}

        {/* Confirmed message */}
        {isConfirmed &&
          paymentStatus === "PAID" && (
            <div className="mt-5 flex items-start gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 p-4">

              <CheckCircle2
                size={18}
                className="mt-0.5 shrink-0 text-emerald-600"
              />

              <div>
                <p className="text-sm font-bold text-emerald-900">
                  Booking confirmed
                </p>

                <p className="mt-1 text-xs leading-5 text-emerald-700">
                  Your payment has been recorded and
                  the reservation is confirmed.
                </p>
              </div>

            </div>
          )}

        {/* Cancellation unavailable */}
        <CancellationUnavailable
          booking={booking}
        />

        {/* Cancellation details */}
        {isCancelled && cancellation && (
          <CancellationSummary
            cancellation={cancellation}
            booking={booking}
          />
        )}

        {/* Cancellation record missing */}
        {isCancelled && !cancellation && (
          <div className="mt-5 flex items-start gap-3 rounded-2xl border border-slate-200 bg-slate-50 p-4">

            <ReceiptText
              size={18}
              className="mt-0.5 shrink-0 text-slate-500"
            />

            <div>
              <p className="text-sm font-bold text-slate-800">
                Booking cancelled
              </p>

              <p className="mt-1 text-xs leading-5 text-slate-500">
                This booking is cancelled. Cancellation
                history is currently unavailable.
              </p>
            </div>

          </div>
        )}

        {/* Footer */}
        <div className="mt-6 flex flex-col gap-4 border-t border-slate-100 pt-5">

          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">

            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-slate-400">

              <span>
                Booked{" "}
                {formatDateTime(
                  booking.createdAt
                )}
              </span>

              {booking.numberOfNights != null && (
                <span>
                  {booking.numberOfNights}{" "}
                  {booking.numberOfNights === 1
                    ? "night"
                    : "nights"}
                </span>
              )}

            </div>

            <div className="flex flex-col gap-2 sm:flex-row">

              {/* Cancel */}
              {(isPending || isConfirmed) &&
                cancellationAllowed && (
                  <button
                    type="button"
                    onClick={() => onCancel(booking)}
                    className="inline-flex items-center justify-center gap-2 rounded-xl border border-rose-200 bg-white px-4 py-3 text-sm font-bold text-rose-600 transition hover:bg-rose-50"
                  >
                    <Ban size={16} />
                    Cancel booking
                  </button>
                )}

              {/* View */}
              <Link
                to={`/bookings/${booking.bookingId}`}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-slate-950 px-4 py-3 text-sm font-bold text-white transition hover:bg-slate-800"
              >
                {isPending
                  ? "Continue booking"
                  : isCompleted
                    ? "View stay"
                    : "View booking"}

                <ArrowRight
                  size={16}
                  className="transition-transform group-hover:translate-x-0.5"
                />
              </Link>

            </div>

          </div>

        </div>

      </div>

    </article>
  );
}

/* =========================================================
   LOADING
========================================================= */

function LoadingCard() {
  return (
    <div className="animate-pulse rounded-3xl border border-slate-200 bg-white p-6">

      <div className="flex gap-4">

        <div className="h-12 w-12 rounded-2xl bg-slate-200" />

        <div className="flex-1">

          <div className="h-3 w-24 rounded bg-slate-200" />

          <div className="mt-3 h-5 w-64 max-w-full rounded bg-slate-200" />

          <div className="mt-2 h-3 w-40 rounded bg-slate-200" />

        </div>

      </div>

      <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">

        <div className="h-20 rounded-2xl bg-slate-100" />
        <div className="h-20 rounded-2xl bg-slate-100" />
        <div className="h-20 rounded-2xl bg-slate-100" />
        <div className="h-20 rounded-2xl bg-slate-100" />

      </div>

    </div>
  );
}

/* =========================================================
   EMPTY STATE
========================================================= */

function EmptyState({ filtered }) {
  return (
    <div className="rounded-3xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">

      <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-slate-100 text-slate-500">

        {filtered ? (
          <Search size={26} />
        ) : (
          <Hotel size={26} />
        )}

      </div>

      <h2 className="mt-5 text-xl font-bold text-slate-950">
        {filtered
          ? "No bookings match your filters"
          : "No bookings yet"}
      </h2>

      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
        {filtered
          ? "Try changing your search or status filter to find another booking."
          : "Your hotel reservations will appear here once you make your first booking."}
      </p>

      {!filtered && (
        <Link
          to="/stays"
          className="mt-6 inline-flex items-center gap-2 rounded-xl bg-slate-950 px-5 py-3 text-sm font-bold text-white transition hover:bg-slate-800"
        >
          Explore stays
          <ArrowRight size={16} />
        </Link>
      )}

    </div>
  );
}

/* =========================================================
   MAIN PAGE
========================================================= */

export default function Bookings() {
  const {
    user,
    loading: authLoading,
  } = useAuth();

  const [bookings, setBookings] = useState([]);

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState("");

  const [search, setSearch] =
    useState("");

  const [statusFilter, setStatusFilter] =
    useState("ALL");

  const [paymentFilter, setPaymentFilter] =
    useState("ALL");

  const [cancellations, setCancellations] =
    useState({});

  const [selectedBooking, setSelectedBooking] =
    useState(null);

  const [cancellationSubmitting, setCancellationSubmitting] =
    useState(false);

  const [cancellationError, setCancellationError] =
    useState("");

  /* =======================================================
     LOAD BOOKINGS
  ======================================================= */

  async function loadBookings() {
    setLoading(true);
    setError("");

    try {
      const data =
        await bookingService.getMyBookings();

      const bookingList =
        Array.isArray(data) ? data : [];

      setBookings(bookingList);
    } catch (requestError) {
      console.error(
        "Failed to load bookings:",
        requestError
      );

      setError(
        getErrorMessage(
          requestError,
          "Unable to load your bookings."
        )
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (authLoading) {
      return;
    }

    if (!user) {
      setLoading(false);
      return;
    }

    loadBookings();
  }, [authLoading, user]);

  /* =======================================================
     LOAD CANCELLATION HISTORY
     
     IMPORTANT:
     Only cancelled bookings request
     GET /bookings/{id}/cancellation
  ======================================================= */

  useEffect(() => {
    if (!bookings.length) {
      return;
    }

    const cancelledBookings =
      bookings.filter(
        (booking) =>
          normalizeStatus(
            booking.bookingStatus
          ) === "CANCELLED"
      );

    if (!cancelledBookings.length) {
      return;
    }

    let cancelled = false;

    async function loadCancellationHistory() {
      const results = {};

      await Promise.all(
        cancelledBookings.map(async (booking) => {
          try {
            const cancellation =
              await bookingService.getCancellation(
                booking.bookingId
              );

            results[booking.bookingId] =
              cancellation;
          } catch (requestError) {
            /*
             * A cancelled booking without a cancellation
             * history record should not break the entire
             * My Bookings page.
             *
             * We intentionally do NOT display this as a
             * console error because the booking itself can
             * still be displayed correctly.
             */
            console.warn(
              `Cancellation history unavailable for booking ${booking.bookingId}`,
              requestError?.response?.data ||
                requestError?.message ||
                requestError
            );
          }
        })
      );

      if (!cancelled) {
        setCancellations((previous) => ({
          ...previous,
          ...results,
        }));
      }
    }

    loadCancellationHistory();

    return () => {
      cancelled = true;
    };
  }, [bookings]);

  /* =======================================================
     FILTERED BOOKINGS
  ======================================================= */

  const filteredBookings = useMemo(() => {
    const query =
      search.trim().toLowerCase();

    return bookings.filter((booking) => {
      const bookingStatus =
        normalizeStatus(
          booking.bookingStatus
        );

      const paymentStatus =
        normalizeStatus(
          booking.paymentStatus
        );

      const matchesStatus =
        statusFilter === "ALL" ||
        bookingStatus === statusFilter;

      const matchesPayment =
        paymentFilter === "ALL" ||
        paymentStatus === paymentFilter;

      const searchableText = [
        booking.bookingReference,
        booking.propertyName,
        booking.roomTypeName,
        booking.bookingStatus,
        booking.paymentStatus,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();

      const matchesSearch =
        !query ||
        searchableText.includes(query);

      return (
        matchesStatus &&
        matchesPayment &&
        matchesSearch
      );
    });
  }, [
    bookings,
    search,
    statusFilter,
    paymentFilter,
  ]);

  /* =======================================================
     STATS
  ======================================================= */

  const stats = useMemo(() => {
    return {
      total: bookings.length,

      confirmed: bookings.filter(
        (booking) =>
          normalizeStatus(
            booking.bookingStatus
          ) === "CONFIRMED"
      ).length,

      pending: bookings.filter(
        (booking) =>
          normalizeStatus(
            booking.bookingStatus
          ) === "PENDING"
      ).length,

      cancelled: bookings.filter(
        (booking) =>
          normalizeStatus(
            booking.bookingStatus
          ) === "CANCELLED"
      ).length,
    };
  }, [bookings]);

  /* =======================================================
     FILTER RESET
  ======================================================= */

  function resetFilters() {
    setSearch("");
    setStatusFilter("ALL");
    setPaymentFilter("ALL");
  }

  const hasActiveFilters =
    Boolean(search.trim()) ||
    statusFilter !== "ALL" ||
    paymentFilter !== "ALL";

  /* =======================================================
     OPEN CANCELLATION
  ======================================================= */

  function handleOpenCancellation(booking) {
    /*
     * Never open cancellation for a booking whose
     * check-in has started or passed.
     */
    if (!canCancelBooking(booking)) {
      return;
    }

    setCancellationError("");
    setSelectedBooking(booking);
  }

  /* =======================================================
     CLOSE CANCELLATION
  ======================================================= */

  function handleCloseCancellation() {
    if (cancellationSubmitting) {
      return;
    }

    setSelectedBooking(null);
    setCancellationError("");
  }

  /* =======================================================
     CONFIRM CANCELLATION
  ======================================================= */

  async function handleConfirmCancellation(
    cancellationData
  ) {
    if (!selectedBooking) {
      return;
    }

    if (!canCancelBooking(selectedBooking)) {
      setCancellationError(
        "This booking can no longer be cancelled because check-in has started or passed."
      );
      return;
    }

    setCancellationSubmitting(true);
    setCancellationError("");

    try {
      const updatedBooking =
        await bookingService.cancelBooking(
          selectedBooking.bookingId,
          cancellationData
        );

      /*
       * Update the booking immediately so the UI changes
       * without requiring a page refresh.
       */
      setBookings((previous) =>
        previous.map((booking) =>
          booking.bookingId ===
          selectedBooking.bookingId
            ? updatedBooking
            : booking
        )
      );

      /*
       * After cancellation, fetch the cancellation record.
       */
      try {
        const cancellation =
          await bookingService.getCancellation(
            selectedBooking.bookingId
          );

        setCancellations((previous) => ({
          ...previous,
          [selectedBooking.bookingId]:
            cancellation,
        }));
      } catch (historyError) {
        /*
         * Cancellation succeeded even if the history
         * request is temporarily unavailable.
         */
        console.warn(
          "Cancellation succeeded but cancellation history could not be loaded:",
          historyError?.response?.data ||
            historyError?.message ||
            historyError
        );
      }

      setSelectedBooking(null);
      setCancellationError("");
    } catch (requestError) {
      console.error(
        "Failed to cancel booking:",
        requestError
      );

      setCancellationError(
        getErrorMessage(
          requestError,
          "Unable to cancel this booking."
        )
      );
    } finally {
      setCancellationSubmitting(false);
    }
  }

  /* =======================================================
     AUTH LOADING
  ======================================================= */

  if (authLoading || loading) {
    return (
      <main className="min-h-screen bg-slate-50">

        <section className="border-b border-slate-200 bg-white">

          <div className="mx-auto max-w-7xl px-5 py-12 lg:px-8">

            <div className="h-4 w-32 animate-pulse rounded bg-slate-200" />

            <div className="mt-4 h-10 w-72 animate-pulse rounded bg-slate-200" />

            <div className="mt-3 h-4 w-96 max-w-full animate-pulse rounded bg-slate-100" />

          </div>

        </section>

        <section className="mx-auto max-w-7xl space-y-5 px-5 py-8 lg:px-8">

          <LoadingCard />
          <LoadingCard />

        </section>

      </main>
    );
  }

  /* =======================================================
     NOT AUTHENTICATED
  ======================================================= */

  if (!user) {
    return (
      <main className="min-h-screen bg-slate-50 px-5 py-20">

        <div className="mx-auto max-w-xl rounded-3xl border border-slate-200 bg-white p-8 text-center shadow-sm">

          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-slate-950 text-white">
            <Hotel size={26} />
          </div>

          <h1 className="mt-6 text-2xl font-bold text-slate-950">
            Sign in to view your bookings
          </h1>

          <p className="mt-3 text-sm leading-6 text-slate-500">
            Your booking history is available
            after signing in to your Travel Buddy
            account.
          </p>

          <Link
            to="/login"
            className="mt-6 inline-flex items-center gap-2 rounded-xl bg-slate-950 px-5 py-3 text-sm font-bold text-white transition hover:bg-slate-800"
          >
            Sign in
            <ArrowRight size={16} />
          </Link>

        </div>

      </main>
    );
  }

  /* =======================================================
     PAGE
  ======================================================= */

  return (
    <main className="min-h-screen bg-slate-50">

      {/* ===================================================
          HERO
      =================================================== */}

      <section className="border-b border-slate-200 bg-white">

        <div className="mx-auto max-w-7xl px-5 py-10 lg:px-8 lg:py-14">

          <div className="flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between">

            <div>

              <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-bold uppercase tracking-[0.14em] text-slate-500">
                <ReceiptText size={13} />
                Your travel activity
              </div>

              <h1 className="mt-4 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">
                My Bookings
              </h1>

              <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-500 sm:text-base">
                Manage your reservations, check
                payment status, review cancellations,
                and quickly open any booking.
              </p>

            </div>

            <Link
              to="/stays"
              className="inline-flex items-center justify-center gap-2 rounded-xl bg-slate-950 px-5 py-3 text-sm font-bold text-white transition hover:bg-slate-800"
            >
              Explore stays
              <ArrowRight size={16} />
            </Link>

          </div>

          {/* Stats */}
          <div className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">

            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">

              <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Total
              </p>

              <p className="mt-2 text-2xl font-black text-slate-950">
                {stats.total}
              </p>

            </div>

            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4">

              <p className="text-xs font-semibold uppercase tracking-wider text-emerald-600">
                Confirmed
              </p>

              <p className="mt-2 text-2xl font-black text-emerald-800">
                {stats.confirmed}
              </p>

            </div>

            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4">

              <p className="text-xs font-semibold uppercase tracking-wider text-amber-600">
                Pending
              </p>

              <p className="mt-2 text-2xl font-black text-amber-800">
                {stats.pending}
              </p>

            </div>

            <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4">

              <p className="text-xs font-semibold uppercase tracking-wider text-rose-600">
                Cancelled
              </p>

              <p className="mt-2 text-2xl font-black text-rose-800">
                {stats.cancelled}
              </p>

            </div>

          </div>

        </div>

      </section>

      {/* ===================================================
          MAIN
      =================================================== */}

      <section className="mx-auto max-w-7xl px-5 py-8 lg:px-8">

        {/* Filters */}
        <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">

          <div className="flex items-center gap-2 text-sm font-bold text-slate-900">
            <SlidersHorizontal size={17} />
            Find a booking
          </div>

          <div className="mt-4 grid gap-3 lg:grid-cols-[1fr_auto_auto_auto]">

            {/* Search */}
            <div className="relative">

              <Search
                size={17}
                className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"
              />

              <input
                type="text"
                value={search}
                onChange={(event) =>
                  setSearch(event.target.value)
                }
                placeholder="Search booking, hotel or room..."
                className="h-12 w-full rounded-xl border border-slate-200 bg-slate-50 pl-11 pr-4 text-sm font-medium text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-slate-400 focus:bg-white"
              />

            </div>

            {/* Booking status */}
            <select
              value={statusFilter}
              onChange={(event) =>
                setStatusFilter(
                  event.target.value
                )
              }
              className="h-12 rounded-xl border border-slate-200 bg-slate-50 px-4 text-sm font-semibold text-slate-700 outline-none focus:border-slate-400"
            >

              <option value="ALL">
                All booking statuses
              </option>

              <option value="PENDING">
                Pending
              </option>

              <option value="CONFIRMED">
                Confirmed
              </option>

              <option value="COMPLETED">
                Completed
              </option>

              <option value="CANCELLED">
                Cancelled
              </option>

            </select>

            {/* Payment */}
            <select
              value={paymentFilter}
              onChange={(event) =>
                setPaymentFilter(
                  event.target.value
                )
              }
              className="h-12 rounded-xl border border-slate-200 bg-slate-50 px-4 text-sm font-semibold text-slate-700 outline-none focus:border-slate-400"
            >

              <option value="ALL">
                All payments
              </option>

              <option value="PAID">
                Paid
              </option>

              <option value="UNPAID">
                Unpaid
              </option>

              <option value="REFUNDED">
                Refunded
              </option>

            </select>

            {/* Reset */}
            <button
              type="button"
              onClick={resetFilters}
              disabled={!hasActiveFilters}
              className="inline-flex h-12 items-center justify-center gap-2 rounded-xl border border-slate-200 px-4 text-sm font-bold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              <RotateCcw size={15} />
              Reset
            </button>

          </div>

          {/* Result count */}
          <div className="mt-4 flex flex-col gap-2 border-t border-slate-100 pt-4 text-xs text-slate-400 sm:flex-row sm:items-center sm:justify-between">

            <span>
              Showing{" "}
              <strong className="text-slate-700">
                {filteredBookings.length}
              </strong>{" "}
              of{" "}
              <strong className="text-slate-700">
                {bookings.length}
              </strong>{" "}
              bookings
            </span>

            {hasActiveFilters && (
              <button
                type="button"
                onClick={resetFilters}
                className="inline-flex items-center gap-1.5 font-bold text-slate-700 hover:text-slate-950"
              >
                Clear filters
                <XCircle size={14} />
              </button>
            )}

          </div>

        </div>

        {/* Error */}
        {error && (
          <div className="mt-5 rounded-3xl border border-rose-200 bg-rose-50 p-5">

            <div className="flex items-start gap-3">

              <XCircle
                size={20}
                className="mt-0.5 shrink-0 text-rose-600"
              />

              <div className="flex-1">

                <h2 className="text-sm font-bold text-rose-900">
                  Unable to load bookings
                </h2>

                <p className="mt-1 text-sm leading-6 text-rose-700">
                  {error}
                </p>

                <button
                  type="button"
                  onClick={loadBookings}
                  className="mt-4 inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2.5 text-xs font-bold text-white transition hover:bg-rose-700"
                >
                  Try again
                </button>

              </div>

            </div>

          </div>
        )}

        {/* Booking list */}
        {!error && (
          <div className="mt-6 space-y-5">

            {filteredBookings.length > 0 ? (
              filteredBookings.map(
                (booking) => (
                  <BookingCard
                    key={booking.bookingId}
                    booking={booking}
                    cancellation={
                      cancellations[
                        booking.bookingId
                      ]
                    }
                    onCancel={
                      handleOpenCancellation
                    }
                  />
                )
              )
            ) : (
              <EmptyState
                filtered={
                  bookings.length > 0 &&
                  hasActiveFilters
                }
              />
            )}

          </div>
        )}

        {/* Bottom hint */}
        {filteredBookings.length > 0 && (
          <div className="mt-8 flex items-center justify-center gap-2 text-xs text-slate-400">

            <ShieldCheck size={14} />

            Your booking information is securely
            tied to your Travel Buddy account.

            <ChevronRight size={13} />

          </div>
        )}

      </section>

      {/* ===================================================
          CANCELLATION MODAL
      =================================================== */}

      {selectedBooking && (
        <CancellationModal
          booking={selectedBooking}
          onClose={handleCloseCancellation}
          onConfirm={
            handleConfirmCancellation
          }
          submitting={
            cancellationSubmitting
          }
          error={cancellationError}
        />
      )}

    </main>
  );
}