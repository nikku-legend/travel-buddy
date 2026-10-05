import { useCallback, useEffect, useRef, useState } from "react";
import {
  CheckCircle2,
  CreditCard,
  Loader2,
  RefreshCw,
  ShieldCheck,
  XCircle,
} from "lucide-react";

import bookingService from "../../services/bookingService";

const RAZORPAY_SCRIPT_URL = "https://checkout.razorpay.com/v1/checkout.js";
const VERIFY_TIMEOUT_MS = 20000;

/* ---------- Razorpay script loader (singleton) ---------- */
let razorpayScriptPromise = null;

function loadRazorpayScript() {
  if (typeof window === "undefined") return Promise.resolve(false);
  if (window.Razorpay) return Promise.resolve(true);
  if (razorpayScriptPromise) return razorpayScriptPromise;

  razorpayScriptPromise = new Promise((resolve) => {
    const existing = document.querySelector(
      `script[src="${RAZORPAY_SCRIPT_URL}"]`
    );
    if (existing) {
      existing.addEventListener("load", () => resolve(true));
      existing.addEventListener("error", () => resolve(false));
      return;
    }
    const script = document.createElement("script");
    script.src = RAZORPAY_SCRIPT_URL;
    script.async = true;
    script.onload = () => resolve(true);
    script.onerror = () => {
      razorpayScriptPromise = null; // allow retry
      resolve(false);
    };
    document.body.appendChild(script);
  });

  return razorpayScriptPromise;
}

/* ---------- Helpers ---------- */
const uuid = () =>
  (crypto.randomUUID && crypto.randomUUID()) ||
  `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;

function formatMoney(minorUnits, currency = "INR") {
  const major = Number(minorUnits) / 100;
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(major);
  } catch {
    return `${major.toFixed(2)} ${currency}`;
  }
}

function withTimeout(promise, ms, message) {
  return Promise.race([
    promise,
    new Promise((_, reject) =>
      setTimeout(() => reject(new Error(message)), ms)
    ),
  ]);
}

function extractErrorMessage(err, fallback) {
  const data = err?.response?.data;
  if (typeof data === "string") return data;
  return data?.message || err?.message || fallback;
}

/* ---------- Component ---------- */
export default function RazorpayPayment({
  bookingId,
  booking,
  onSuccess,
  onFailure,
  onCancel,
}) {
  const [scriptReady, setScriptReady] = useState(false);
  const [loading, setLoading] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState("");

  const mountedRef = useRef(true);
  const rzpRef = useRef(null);
  const failureNotifiedRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;
    loadRazorpayScript().then((ok) => {
      if (!mountedRef.current) return;
      setScriptReady(ok);
      if (!ok) {
        setError(
          "Unable to load Razorpay Checkout. Please check your internet connection."
        );
      }
    });

    return () => {
      mountedRef.current = false;
      try {
        rzpRef.current?.close?.();
      } catch {
        /* noop */
      }
      rzpRef.current = null;
    };
  }, []);

  const notifyFailure = useCallback(
    (msg) => {
      if (failureNotifiedRef.current) return;
      failureNotifiedRef.current = true;
      onFailure?.(msg);
    },
    [onFailure]
  );

  const resetFailureFlag = () => {
    failureNotifiedRef.current = false;
  };

  const startPayment = async () => {
    if (!bookingId) {
      const msg = "Booking ID is missing.";
      setError(msg);
      notifyFailure(msg);
      return;
    }

    resetFailureFlag();
    setError("");
    setLoading(true);

    try {
      if (!scriptReady) {
        const ok = await loadRazorpayScript();
        if (!ok || !window.Razorpay) {
          throw new Error(
            "Razorpay Checkout could not be loaded. Please check your internet connection."
          );
        }
        setScriptReady(true);
      }

      const idempotencyKey = uuid();

      const order = await bookingService.createPaymentOrder(bookingId, {
        idempotencyKey,
      });

      if (!order?.orderId) throw new Error("Payment order was not created.");
      if (!order?.keyId) throw new Error("Razorpay key ID was not returned.");
      if (order.expiresAt && new Date(order.expiresAt) < new Date()) {
        throw new Error("Payment session expired. Please refresh and retry.");
      }

      // Server-provided minor units — never multiply on client
      const amountMinor = Number(order.amount);
      if (!Number.isFinite(amountMinor) || amountMinor <= 0) {
        throw new Error("Invalid payment amount received from the server.");
      }

      const options = {
        key: order.keyId,
        amount: amountMinor,
        currency: order.currency || "INR",
        name: "Travel Buddy",
        description: booking?.hotelName
          ? `Hotel booking at ${booking.hotelName}`
          : "Travel Buddy Hotel Booking",
        order_id: order.orderId,
        prefill: {
          name: booking?.guestName || booking?.userName || "",
          email: booking?.guestEmail || booking?.userEmail || "",
          contact: booking?.guestPhone || booking?.userPhone || "",
        },
        notes: {
          booking_id: String(bookingId),
          booking_reference: order.bookingReference || "",
        },
        theme: { color: "#0f766e" },

        modal: {
          ondismiss: () => {
            if (!mountedRef.current) return;
            setLoading(false);
            setProcessing(false);
            const msg = "Payment window was closed.";
            setError(msg);
            onCancel?.(msg);
          },
        },

        handler: async (response) => {
          if (!mountedRef.current) return;
          setLoading(false);
          setProcessing(true);
          setError("");

          try {
            if (
              !response?.razorpay_order_id ||
              !response?.razorpay_payment_id ||
              !response?.razorpay_signature
            ) {
              throw new Error(
                "Razorpay returned an incomplete payment response."
              );
            }

            const verification = await withTimeout(
              bookingService.verifyPayment(bookingId, {
                razorpayOrderId: response.razorpay_order_id,
                razorpayPaymentId: response.razorpay_payment_id,
                razorpaySignature: response.razorpay_signature,
              }),
              VERIFY_TIMEOUT_MS,
              "Payment verification timed out. We will confirm your booking shortly."
            );

            if (!mountedRef.current) return;
            setProcessing(false);
            onSuccess?.(verification);
          } catch (verifyErr) {
            if (!mountedRef.current) return;
            const msg = extractErrorMessage(
              verifyErr,
              "Payment verification failed. Please contact support if money was deducted."
            );
            setError(msg);
            setProcessing(false);
            notifyFailure(msg);
          }
        },
      };

      const rzp = new window.Razorpay(options);
      rzpRef.current = rzp;

      rzp.on("payment.failed", (response) => {
        if (!mountedRef.current) return;
        setLoading(false);
        setProcessing(false);
        const msg =
          response?.error?.description || "Payment failed. Please try again.";
        setError(msg);
        notifyFailure(msg);
      });

      rzp.on("payment.error", (response) => {
        if (!mountedRef.current) return;
        setLoading(false);
        setProcessing(false);
        const msg =
          response?.error?.description || "Payment error. Please try again.";
        setError(msg);
        notifyFailure(msg);
      });

      rzp.open();
      setLoading(false);
    } catch (err) {
      if (!mountedRef.current) return;
      setLoading(false);
      setProcessing(false);
      const msg = extractErrorMessage(err, "Unable to start payment. Please try again.");
      setError(msg);
      notifyFailure(msg);
    }
  };

  const amountMinor = Number(
    booking?.totalAmountMinor ??
      booking?.amountMinor ??
      booking?.totalAmountMinorUnits ??
      0
  );
  const currency = booking?.currency || "INR";

  return (
    <div className="mt-6 overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <div className="border-b border-slate-200 bg-gradient-to-br from-slate-950 via-slate-900 to-teal-950 px-6 py-7 text-white">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.16em] text-white/80">
              <ShieldCheck className="h-4 w-4" />
              Secure payment
            </div>
            <h3 className="text-xl font-bold">Complete your booking</h3>
            <p className="mt-2 max-w-xl text-sm leading-6 text-white/70">
              Pay securely through Razorpay. Your booking is confirmed only
              after server-side verification.
            </p>
          </div>
          <div className="hidden rounded-2xl border border-white/10 bg-white/10 p-3 sm:block">
            <CreditCard className="h-6 w-6 text-white" />
          </div>
        </div>
      </div>

      <div className="p-6">
        {amountMinor > 0 && (
          <div className="mb-5 flex items-center justify-between rounded-2xl bg-slate-50 px-4 py-4">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                Amount payable
              </p>
              <p className="mt-1 text-sm text-slate-600">Booking #{bookingId}</p>
            </div>
            <p className="text-xl font-extrabold text-slate-950">
              {formatMoney(amountMinor, currency)}
            </p>
          </div>
        )}

        {error && (
          <div className="mb-5 flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-700">
            <XCircle className="mt-0.5 h-5 w-5 shrink-0" />
            <div>
              <p className="font-semibold">Payment could not be completed</p>
              <p className="mt-1 leading-5">{error}</p>
            </div>
          </div>
        )}

        {processing ? (
          <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
            <div className="flex items-start gap-3">
              <Loader2 className="mt-0.5 h-5 w-5 shrink-0 animate-spin text-emerald-600" />
              <div>
                <p className="font-semibold text-emerald-800">
                  Verifying your payment...
                </p>
                <p className="mt-1 text-sm leading-5 text-emerald-700">
                  Please wait while Travel Buddy verifies the Razorpay payment.
                </p>
              </div>
            </div>
          </div>
        ) : (
          <button
            type="button"
            onClick={startPayment}
            disabled={loading || !scriptReady}
            className="flex w-full items-center justify-center gap-3 rounded-2xl bg-slate-950 px-5 py-4 text-sm font-bold text-white shadow-lg shadow-slate-950/10 transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {loading ? (
              <>
                <Loader2 className="h-5 w-5 animate-spin" />
                Preparing secure payment...
              </>
            ) : error ? (
              <>
                <RefreshCw className="h-5 w-5" />
                Retry payment
              </>
            ) : (
              <>
                <CreditCard className="h-5 w-5" />
                Pay securely with Razorpay
              </>
            )}
          </button>
        )}

        <div className="mt-4 flex flex-wrap items-center justify-center gap-x-5 gap-y-2 text-xs text-slate-500">
          <span className="inline-flex items-center gap-1.5">
            <ShieldCheck className="h-4 w-4" />
            Secure checkout
          </span>
          <span className="inline-flex items-center gap-1.5">
            <CheckCircle2 className="h-4 w-4" />
            Server verified
          </span>
          <span>UPI • Cards • Net Banking</span>
        </div>
      </div>
    </div>
  );
}