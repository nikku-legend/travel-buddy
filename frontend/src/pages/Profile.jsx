import { useMemo } from "react";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  BadgeCheck,
  Bookmark,
  CalendarDays,
  ChevronRight,
  CreditCard,
  LogOut,
  Mail,
  MapPin,
  Phone,
  ShieldCheck,
  UserRound,
} from "lucide-react";

import { useAuth } from "../context/useAuth";

function getInitials(name = "") {
  return (
    name
      .trim()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join("") || "U"
  );
}

function formatRole(role) {
  if (!role) {
    return "Traveler";
  }

  return role
    .replace(/^ROLE_/i, "")
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function ProfileInfoRow({
  icon: Icon,
  label,
  value,
  muted = false,
}) {
  return (
    <div className="flex items-start gap-4 rounded-2xl border border-slate-100 bg-slate-50/70 p-4">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-slate-700 shadow-sm ring-1 ring-slate-200/70">
        <Icon size={18} strokeWidth={1.8} />
      </div>

      <div className="min-w-0 flex-1">
        <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">
          {label}
        </p>

        <p
          className={`mt-1 break-words text-sm font-semibold ${
            muted ? "text-slate-400" : "text-slate-900"
          }`}
        >
          {value || "Not provided"}
        </p>
      </div>
    </div>
  );
}

function AccountAction({
  icon: Icon,
  title,
  description,
  to,
}) {
  return (
    <Link
      to={to}
      className="group flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-4 transition-all duration-300 hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-lg"
    >
      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-slate-950 text-white transition-transform duration-300 group-hover:scale-105">
        <Icon size={19} strokeWidth={1.8} />
      </div>

      <div className="min-w-0 flex-1">
        <h3 className="text-sm font-bold text-slate-950">
          {title}
        </h3>

        <p className="mt-1 text-xs leading-5 text-slate-500">
          {description}
        </p>
      </div>

      <ChevronRight
        size={18}
        className="shrink-0 text-slate-300 transition-transform duration-300 group-hover:translate-x-1 group-hover:text-slate-700"
      />
    </Link>
  );
}

export default function Profile() {
  const { user, loading, logout } = useAuth();

  const initials = useMemo(
    () => getInitials(user?.fullName),
    [user?.fullName]
  );

  const formattedRole = useMemo(
    () => formatRole(user?.role),
    [user?.role]
  );

  async function handleLogout() {
    await logout();
  }

  if (loading) {
    return (
      <div className="min-h-[calc(100vh-80px)] bg-slate-50 px-5 py-12 sm:py-16">
        <div className="mx-auto max-w-6xl">
          <div className="animate-pulse">
            <div className="h-8 w-48 rounded-lg bg-slate-200" />
            <div className="mt-3 h-4 w-80 max-w-full rounded bg-slate-200" />

            <div className="mt-10 grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
              <div className="h-[460px] rounded-3xl bg-white shadow-sm ring-1 ring-slate-200" />
              <div className="h-[460px] rounded-3xl bg-white shadow-sm ring-1 ring-slate-200" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="flex min-h-[calc(100vh-80px)] items-center justify-center bg-slate-50 px-5 py-16">
        <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-slate-100 text-slate-500">
            <UserRound size={28} />
          </div>

          <h1 className="mt-6 text-2xl font-bold text-slate-950">
            Sign in to view your profile
          </h1>

          <p className="mt-3 text-sm leading-6 text-slate-500">
            Your Travel Buddy account information will appear here after
            authentication.
          </p>

          <Link
            to="/login"
            className="mt-7 inline-flex items-center justify-center gap-2 rounded-xl bg-slate-950 px-5 py-3 text-sm font-semibold text-white transition hover:bg-slate-800"
          >
            Sign in
            <ArrowRight size={17} />
          </Link>
        </div>
      </div>
    );
  }

  return (
    <main className="min-h-[calc(100vh-80px)] bg-slate-50">
      {/* ============================================================
          HERO
          ============================================================ */}
      <section className="relative overflow-hidden border-b border-slate-200 bg-white">
        <div className="absolute -right-24 -top-32 h-72 w-72 rounded-full bg-slate-100 blur-3xl" />
        <div className="absolute -left-24 bottom-[-160px] h-80 w-80 rounded-full bg-slate-100 blur-3xl" />

        <div className="relative mx-auto max-w-6xl px-5 py-12 sm:px-6 sm:py-16 lg:px-8">
          <div className="max-w-2xl">
            <p className="text-xs font-bold uppercase tracking-[0.24em] text-slate-400">
              Your Travel Buddy account
            </p>

            <h1 className="mt-3 text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">
              Welcome back, {user.fullName?.split(" ")[0] || "Traveler"}.
            </h1>

            <p className="mt-4 max-w-xl text-sm leading-7 text-slate-500 sm:text-base">
              Manage your account information, bookings, saved destinations,
              and travel activity from one place.
            </p>
          </div>
        </div>
      </section>

      {/* ============================================================
          MAIN CONTENT
          ============================================================ */}
      <section className="mx-auto max-w-6xl px-5 py-8 sm:px-6 sm:py-10 lg:px-8 lg:py-12">
        <div className="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
          {/* ========================================================
              PROFILE CARD
              ======================================================== */}
          <section className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
            {/* Profile header */}
            <div className="relative overflow-hidden bg-slate-950 px-6 py-8 text-white sm:px-8 sm:py-10">
              <div className="absolute -right-16 -top-20 h-48 w-48 rounded-full bg-white/10 blur-2xl" />
              <div className="absolute -bottom-24 -left-10 h-44 w-44 rounded-full bg-white/5 blur-2xl" />

              <div className="relative flex flex-col gap-6 sm:flex-row sm:items-center">
                {/* Avatar */}
                <div className="flex h-24 w-24 shrink-0 items-center justify-center rounded-[28px] bg-white text-3xl font-bold text-slate-950 shadow-xl ring-4 ring-white/10">
                  {initials}
                </div>

                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-2xl font-bold tracking-tight">
                      {user.fullName}
                    </h2>

                    <span className="inline-flex items-center gap-1 rounded-full bg-white/10 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-white/80 ring-1 ring-white/10">
                      <BadgeCheck size={13} />
                      Verified account
                    </span>
                  </div>

                  <p className="mt-2 break-all text-sm text-white/65">
                    {user.email}
                  </p>

                  <div className="mt-4 inline-flex items-center rounded-full border border-white/10 bg-white/10 px-3 py-1.5 text-xs font-semibold text-white/80">
                    {formattedRole}
                  </div>
                </div>
              </div>
            </div>

            {/* Profile information */}
            <div className="p-5 sm:p-8">
              <div className="mb-5">
                <p className="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">
                  Account information
                </p>

                <h3 className="mt-2 text-xl font-bold text-slate-950">
                  Personal details
                </h3>
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <ProfileInfoRow
                  icon={UserRound}
                  label="Full name"
                  value={user.fullName}
                />

                <ProfileInfoRow
                  icon={Mail}
                  label="Email address"
                  value={user.email}
                />

                <ProfileInfoRow
                  icon={Phone}
                  label="Phone number"
                  value={user.phoneNumber}
                  muted={!user.phoneNumber}
                />

                <ProfileInfoRow
                  icon={ShieldCheck}
                  label="Account role"
                  value={formattedRole}
                />

                <ProfileInfoRow
                  icon={CreditCard}
                  label="Account ID"
                  value={`TB-${String(user.userId).padStart(6, "0")}`}
                />

                <ProfileInfoRow
                  icon={MapPin}
                  label="Travel profile"
                  value="Traveler account"
                />
              </div>
            </div>
          </section>

          {/* ========================================================
              ACCOUNT ACTIONS
              ======================================================== */}
          <aside className="space-y-6">
            <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
              <p className="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">
                Quick access
              </p>

              <h2 className="mt-2 text-xl font-bold text-slate-950">
                Your Travel Buddy
              </h2>

              <p className="mt-2 text-sm leading-6 text-slate-500">
                Jump directly into the parts of your travel account you use
                most.
              </p>

              <div className="mt-6 space-y-3">
                <AccountAction
                  icon={CalendarDays}
                  title="My bookings"
                  description="View your reservations and booking details."
                  to="/bookings"
                />

                <AccountAction
                  icon={Bookmark}
                  title="Bucket list"
                  description="Explore the destinations you've saved."
                  to="/bucket-list"
                />

                <AccountAction
                  icon={MapPin}
                  title="Explore stays"
                  description="Find your next hotel, villa, or stay."
                  to="/stays"
                />
              </div>
            </section>

            {/* Security card */}
            <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
              <div className="flex items-start gap-4">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700">
                  <ShieldCheck size={20} />
                </div>

                <div>
                  <h3 className="text-sm font-bold text-slate-950">
                    Account security
                  </h3>

                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    Your account uses authenticated access through Travel
                    Buddy's secure API.
                  </p>
                </div>
              </div>
            </section>

            {/* Logout */}
            <button
              type="button"
              onClick={handleLogout}
              className="group flex w-full items-center justify-between rounded-2xl border border-red-200 bg-white px-5 py-4 text-left transition-all duration-300 hover:border-red-300 hover:bg-red-50"
            >
              <span className="flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-red-50 text-red-600 transition-colors group-hover:bg-red-100">
                  <LogOut size={18} />
                </span>

                <span>
                  <span className="block text-sm font-bold text-slate-950">
                    Sign out
                  </span>

                  <span className="mt-0.5 block text-xs text-slate-500">
                    Sign out of this Travel Buddy account
                  </span>
                </span>
              </span>

              <ChevronRight
                size={18}
                className="text-slate-300 transition-transform group-hover:translate-x-1"
              />
            </button>
          </aside>
        </div>
      </section>
    </main>
  );
}