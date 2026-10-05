import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { ShieldCheck } from "lucide-react";
import { useAuth } from "../context/useAuth";
import { getDefaultRouteForRole } from "../components/ProtectedRoute";

/*
 * ============================================================
 * REGISTRATION
 *
 * Registration always creates a TRAVELER account.
 *
 * Partner accounts are deliberately not selectable here. A partner role
 * is granted only after an admin approves a partner application with
 * verified KYC documents, so offering the choice at signup would let
 * anyone self-appoint as a verified supplier.
 *
 * Visitors who arrived via "Become a Partner" are routed straight into
 * the application flow once they have an account.
 * ============================================================
 */

export default function Register() {
  const navigate = useNavigate();
  const location = useLocation();
  const { register } = useAuth();

  const [form, setForm] = useState({
    fullName: "",
    email: "",
    password: "",
    phoneNumber: "",
  });

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  /*
   * Remember why the visitor came here. Sent through router state by the
   * "Become a Partner" page when it redirects a guest to sign up.
   */
  const partnerIntent = location.state?.becomePartner === true;
  const partnerType = location.state?.partnerType ?? null;

  function handleChange(event) {
    setForm({
      ...form,
      [event.target.name]: event.target.value,
    });
  }

  async function handleSubmit(event) {
    event.preventDefault();

    try {
      setLoading(true);
      setError("");

      const authResult = await register(form);

      /*
       * Continue the partner journey instead of dropping the new user on
       * the homepage.
       */
      if (partnerIntent) {
        navigate(
          partnerType
            ? `/partner/apply/${partnerType}`
            : "/become-a-partner",
          { replace: true }
        );

        return;
      }

      navigate(
        getDefaultRouteForRole(authResult?.user?.role),
        { replace: true }
      );
    } catch (err) {
      console.error(err);

      setError(
        err.response?.data?.message ||
          err?.message ||
          "Registration failed."
      );
    } finally {
      setLoading(false);
    }
  }

  const inputClass =
    "w-full rounded-xl border border-white/10 bg-black/20 px-4 py-3 text-sm text-white outline-none transition placeholder:text-white/25 focus:border-cyan-400";

  const labelClass =
    "mb-1.5 block text-xs font-semibold uppercase tracking-wider text-slate-400";

  return (
    <div className="flex min-h-[calc(100vh-72px)] items-center justify-center px-5 py-12 bg-slate-950 text-white">
      <div className="w-full max-w-xl rounded-3xl border border-white/10 bg-white/[0.04] p-8 shadow-2xl backdrop-blur-2xl">
        <h1 className="text-3xl font-bold tracking-tight">
          Join Travel Buddy
        </h1>

        <p className="mt-2 text-sm text-slate-400">
          {partnerIntent
            ? "Create your traveler account, then finish your partner application."
            : "Create a free traveler account to start planning your trips."}
        </p>

        {error && (
          <div className="mt-5 rounded-2xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-7 space-y-5">
          <div>
            <label className={labelClass}>Full Name</label>
            <input
              name="fullName"
              required
              value={form.fullName}
              onChange={handleChange}
              placeholder="e.g. John Doe"
              className={inputClass}
            />
          </div>

          <div>
            <label className={labelClass}>Email Address</label>
            <input
              name="email"
              type="email"
              required
              value={form.email}
              onChange={handleChange}
              placeholder="you@example.com"
              className={inputClass}
            />
          </div>

          <div>
            <label className={labelClass}>Phone Number</label>
            <input
              name="phoneNumber"
              value={form.phoneNumber}
              onChange={handleChange}
              placeholder="+91 98765 43210"
              className={inputClass}
            />
          </div>

          <div>
            <label className={labelClass}>Password</label>
            <input
              name="password"
              type="password"
              required
              minLength={8}
              value={form.password}
              onChange={handleChange}
              placeholder="Minimum 8 characters"
              className={inputClass}
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-xl bg-cyan-400 px-4 py-3.5 font-semibold text-slate-950 transition hover:bg-cyan-300 disabled:opacity-60 cursor-pointer"
          >
            {loading
              ? "Creating account..."
              : "Create Traveler Account"}
          </button>
        </form>

        {partnerIntent && (
          <p className="mt-5 rounded-2xl border border-cyan-400/20 bg-cyan-400/5 p-3 text-xs leading-5 text-cyan-200/80">
            You are registering as a traveler. After signing in you will
            continue to the partner application. Your partner role is
            activated only once a Travel Buddy admin approves it.
          </p>
        )}

        <p className="mt-7 text-center text-sm text-slate-400">
          Already have an account?{" "}
          <Link
            to="/login"
            state={location.state}
            className="font-semibold text-cyan-300 hover:underline"
          >
            Sign in
          </Link>
        </p>

        <div className="mt-6 flex items-center justify-center gap-2 text-xs text-white/30">
          <ShieldCheck className="h-4 w-4" />
          Role-protected secure onboarding
        </div>
      </div>
    </div>
  );
}