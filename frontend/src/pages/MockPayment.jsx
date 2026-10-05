import { Link, useLocation } from "react-router-dom";
import { useState } from "react";
import { CheckCircle2, CreditCard, ShieldCheck, XCircle } from "lucide-react";

import bookingService from "../services/bookingService";

function money(value, currency = "INR") {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(Number(value || 0));
}

export default function MockPayment() {
  const { state } = useLocation();
  const booking = state?.booking;
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const [processing, setProcessing] = useState(false);

  if (!booking) {
    return (
      <div className="mx-auto max-w-xl px-5 py-20 text-center">
        <h1 className="text-3xl font-bold text-slate-950">No payment selected</h1>
        <p className="mt-3 text-slate-500">Open payment from a newly created checkout hold.</p>
        <Link to="/stays" className="mt-7 inline-flex rounded-xl bg-slate-950 px-5 py-3 text-sm font-semibold text-white">Browse stays</Link>
      </div>
    );
  }

  async function processPayment(paymentSuccessful) {
    setError("");
    try {
      setProcessing(true);
      setResult(await bookingService.verifyMockPayment(
        booking.bookingId,
        paymentSuccessful
      ));
    } catch (err) {
      setError(err.response?.data?.message || "Payment could not be verified.");
    } finally {
      setProcessing(false);
    }
  }

  if (result) {
    const paid = result.bookingStatus === "CONFIRMED";
    return (
      <div className="bg-slate-50 px-5 py-16">
        <div className="mx-auto max-w-xl rounded-3xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <div className={`mx-auto flex h-16 w-16 items-center justify-center rounded-full ${paid ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700"}`}>
            {paid ? <CheckCircle2 size={32} /> : <XCircle size={32} />}
          </div>
          <h1 className="mt-6 text-3xl font-bold text-slate-950">{paid ? "Payment successful" : "Payment was not completed"}</h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            {paid ? `Booking ${result.bookingReference} is confirmed.` : "The hold was cancelled and your rooms have been released."}
          </p>
          <div className="mt-6 rounded-2xl bg-slate-50 p-5 text-left text-sm">
            <div className="flex justify-between gap-4"><span className="text-slate-500">Booking</span><strong>{result.bookingReference}</strong></div>
            <div className="mt-3 flex justify-between gap-4"><span className="text-slate-500">Total</span><strong>{money(result.totalAmount, result.currency)}</strong></div>
          </div>
          <Link to="/stays" className="mt-7 inline-flex rounded-xl bg-slate-950 px-5 py-3 text-sm font-semibold text-white">Explore more stays</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-slate-50 px-5 py-12 sm:py-16">
      <div className="mx-auto max-w-xl rounded-3xl border border-slate-200 bg-white p-7 shadow-sm sm:p-9">
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-950 text-white"><CreditCard size={22} /></div>
        <p className="mt-6 text-xs font-bold uppercase tracking-[0.2em] text-slate-400">Development gateway</p>
        <h1 className="mt-2 text-3xl font-bold text-slate-950">Mock payment</h1>
        <p className="mt-3 text-sm leading-6 text-slate-500">This test gateway lets you exercise both payment outcomes without processing real money.</p>
        <div className="mt-7 rounded-2xl bg-slate-50 p-5 text-sm">
          <div className="flex justify-between gap-4"><span className="text-slate-500">Booking</span><strong>{booking.bookingReference}</strong></div>
          <div className="mt-3 flex justify-between gap-4"><span className="text-slate-500">Stay</span><strong className="text-right">{booking.propertyName}</strong></div>
          <div className="mt-3 flex justify-between gap-4"><span className="text-slate-500">Amount</span><strong>{money(booking.totalAmount, booking.currency)}</strong></div>
        </div>
        {error && <div className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>}
        <div className="mt-7 flex items-start gap-3 rounded-2xl border border-slate-200 p-4 text-sm leading-6 text-slate-600"><ShieldCheck size={19} className="mt-0.5 shrink-0" />A successful test payment confirms the booking. A failed test payment cancels it and releases the held rooms.</div>
        <div className="mt-6 grid gap-3 sm:grid-cols-2">
          <button disabled={processing} onClick={() => processPayment(false)} className="rounded-xl border border-slate-300 px-4 py-3.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60">Simulate failure</button>
          <button disabled={processing} onClick={() => processPayment(true)} className="rounded-xl bg-slate-950 px-4 py-3.5 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-60">{processing ? "Verifying..." : "Pay successfully"}</button>
        </div>
      </div>
    </div>
  );
}
