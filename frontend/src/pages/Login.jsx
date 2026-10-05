import { useState } from "react";
import {
  ArrowRight,
  Eye,
  EyeOff,
  LockKeyhole,
  Mail,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import {
  Link,
  useLocation,
  useNavigate,
} from "react-router-dom";

import { useAuth } from "../context/useAuth";
import { getDefaultRouteForRole } from "../components/ProtectedRoute";

function getInitials(name) {
  if (!name) {
    return "TB";
  }

  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0))
    .join("")
    .toUpperCase();
}

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();

  const { login } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [showPassword, setShowPassword] = useState(false);

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();

    if (loading) {
      return;
    }

    setError("");
    setLoading(true);

    try {
      const authResult = await login({
        email: email.trim(),
        password,
      });

      /*
       * ----------------------------------------------------------
       * RETURN USER TO ORIGINAL DESTINATION OR ROLE PORTAL
       * ----------------------------------------------------------
       */

      const from = location.state?.from;

      if (from?.pathname) {
        navigate(
          {
            pathname: from.pathname,
            search: from.search || "",
            hash: from.hash || "",
          },
          {
            replace: true,
          }
        );

        return;
      }

      /*
       * Role-based automatic portal redirection
       */
      const targetRoute = getDefaultRouteForRole(authResult?.user?.role);
      navigate(targetRoute, {
        replace: true,
      });
    } catch (err) {
      console.error("Login failed:", err);

      const responseMessage =
        err?.response?.data?.message ||
        err?.response?.data?.error;

      setError(
        responseMessage ||
          "Unable to sign in. Please check your email and password."
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="relative min-h-[calc(100vh-72px)] overflow-hidden bg-slate-950 text-white">
      {/* =========================================================
          BACKGROUND
         ========================================================= */}

      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -left-40 top-10 h-96 w-96 rounded-full bg-cyan-500/10 blur-3xl" />

        <div className="absolute right-[-8rem] top-32 h-[30rem] w-[30rem] rounded-full bg-blue-500/10 blur-3xl" />

        <div className="absolute bottom-[-8rem] left-1/3 h-96 w-96 rounded-full bg-indigo-500/10 blur-3xl" />
      </div>

      {/* =========================================================
          CONTENT
         ========================================================= */}

      <section className="relative mx-auto flex min-h-[calc(100vh-72px)] w-full max-w-7xl items-center px-4 py-10 sm:px-6 lg:px-8">
        <div className="grid w-full gap-10 lg:grid-cols-[1.05fr_0.95fr] lg:items-center">
          {/* =====================================================
              LEFT SIDE
             ===================================================== */}

          <div className="hidden lg:block">
            <div className="max-w-xl">
              <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] px-4 py-2 text-xs font-medium uppercase tracking-[0.18em] text-white/60 backdrop-blur-xl">
                <UserRound className="h-4 w-4" />
                Travel Buddy
              </div>

              <h1 className="text-5xl font-semibold leading-[1.05] tracking-tight xl:text-6xl">
                Continue your
                <span className="block bg-gradient-to-r from-cyan-300 via-blue-300 to-indigo-300 bg-clip-text text-transparent">
                  journey.
                </span>
              </h1>

              <p className="mt-6 max-w-lg text-base leading-8 text-white/55">
                Sign in to manage your profile, reservations, stays and
                upcoming adventures from one secure travel dashboard.
              </p>

              <div className="mt-10 grid gap-3 sm:grid-cols-3">
                <Feature
                  icon={<ShieldCheck className="h-5 w-5" />}
                  title="Secure"
                  text="Protected account access"
                />

                <Feature
                  icon={<LockKeyhole className="h-5 w-5" />}
                  title="Private"
                  text="Your travel data stays yours"
                />

                <Feature
                  icon={<ArrowRight className="h-5 w-5" />}
                  title="Seamless"
                  text="Pick up where you left off"
                />
              </div>
            </div>
          </div>

          {/* =====================================================
              LOGIN CARD
             ===================================================== */}

          <div className="mx-auto w-full max-w-md">
            <div className="rounded-[2rem] border border-white/10 bg-white/[0.05] p-5 shadow-2xl shadow-black/30 backdrop-blur-2xl sm:p-7">
              {/* Mobile brand */}
              <div className="mb-7 lg:hidden">
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-white text-sm font-bold text-slate-950">
                  TB
                </div>

                <p className="text-xs font-medium uppercase tracking-[0.18em] text-white/40">
                  Travel Buddy
                </p>
              </div>

              {/* Heading */}
              <div>
                <p className="text-xs font-medium uppercase tracking-[0.18em] text-cyan-300/80">
                  Welcome back
                </p>

                <h2 className="mt-2 text-3xl font-semibold tracking-tight">
                  Good to see you.
                </h2>

                <p className="mt-2 text-sm leading-6 text-white/50">
                  Sign in to continue your Travel Buddy experience.
                </p>
              </div>

              {/* Error */}
              {error && (
                <div className="mt-6 rounded-2xl border border-red-400/20 bg-red-400/[0.06] p-4">
                  <p className="text-sm leading-6 text-red-200">
                    {error}
                  </p>
                </div>
              )}

              {/* Form */}
              <form
                onSubmit={handleSubmit}
                className="mt-7 space-y-5"
              >
                {/* Email */}
                <div>
                  <label
                    htmlFor="login-email"
                    className="mb-2 block text-sm font-medium text-white/80"
                  >
                    Email address
                  </label>

                  <div className="relative">
                    <Mail className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-white/35" />

                    <input
                      id="login-email"
                      type="email"
                      required
                      autoComplete="email"
                      value={email}
                      onChange={(event) =>
                        setEmail(event.target.value)
                      }
                      placeholder="you@example.com"
                      className="w-full rounded-xl border border-white/10 bg-black/20 py-3.5 pl-11 pr-4 text-sm text-white outline-none transition placeholder:text-white/25 focus:border-cyan-300/40 focus:bg-black/30 focus:ring-4 focus:ring-cyan-300/5"
                    />
                  </div>
                </div>

                {/* Password */}
                <div>
                  <div className="mb-2 flex items-center justify-between">
                    <label
                      htmlFor="login-password"
                      className="block text-sm font-medium text-white/80"
                    >
                      Password
                    </label>
                  </div>

                  <div className="relative">
                    <LockKeyhole className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-white/35" />

                    <input
                      id="login-password"
                      type={showPassword ? "text" : "password"}
                      required
                      autoComplete="current-password"
                      value={password}
                      onChange={(event) =>
                        setPassword(event.target.value)
                      }
                      placeholder="Enter your password"
                      className="w-full rounded-xl border border-white/10 bg-black/20 py-3.5 pl-11 pr-12 text-sm text-white outline-none transition placeholder:text-white/25 focus:border-cyan-300/40 focus:bg-black/30 focus:ring-4 focus:ring-cyan-300/5"
                    />

                    <button
                      type="button"
                      onClick={() =>
                        setShowPassword((current) => !current)
                      }
                      className="absolute right-3 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-lg text-white/40 transition hover:bg-white/10 hover:text-white"
                      aria-label={
                        showPassword
                          ? "Hide password"
                          : "Show password"
                      }
                    >
                      {showPassword ? (
                        <EyeOff className="h-4 w-4" />
                      ) : (
                        <Eye className="h-4 w-4" />
                      )}
                    </button>
                  </div>
                </div>

                {/* Submit */}
                <button
                  type="submit"
                  disabled={loading}
                  className="group flex w-full items-center justify-center gap-2 rounded-xl bg-white px-5 py-3.5 text-sm font-semibold text-slate-950 transition hover:bg-white/90 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {loading ? (
                    <>
                      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-950/20 border-t-slate-950" />
                      Signing in...
                    </>
                  ) : (
                    <>
                      Sign in
                      <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
                    </>
                  )}
                </button>
              </form>

              {/* Register */}
              <div className="mt-7 border-t border-white/10 pt-6 text-center">
                <p className="text-sm text-white/45">
                  Don&apos;t have a Travel Buddy account?
                </p>

                <Link
                  to="/register"
                  className="mt-2 inline-flex items-center gap-1 text-sm font-semibold text-white transition hover:text-cyan-300"
                >
                  Create your account
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </div>
            </div>

            {/* Secure note */}
            <div className="mt-5 flex items-center justify-center gap-2 text-xs text-white/30">
              <ShieldCheck className="h-4 w-4" />
              Secure authentication powered by Travel Buddy
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}

function Feature({ icon, title, text }) {
  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.03] p-4 backdrop-blur-xl">
      <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/[0.06] text-cyan-300">
        {icon}
      </div>

      <p className="mt-3 text-sm font-medium text-white">
        {title}
      </p>

      <p className="mt-1 text-xs leading-5 text-white/40">
        {text}
      </p>
    </div>
  );
}
