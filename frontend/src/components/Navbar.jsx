import { useEffect, useRef, useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import {
  ArrowUpRight,
  Bookmark,
  Building2,
  CalendarDays,
  Car,
  ChevronDown,
  Compass,
  LogOut,
  Menu,
  Shield,
  UserRound,
  X,
} from "lucide-react";

import { useAuth } from "../context/useAuth";
import NotificationBell from "./NotificationBell";
import {
  getDefaultRouteForRole,
  getPartnerPortals,
  userHasRole,
} from "./ProtectedRoute";

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

const navItems = [
  {
    label: "Home",
    to: "/",
  },
  {
    label: "Destinations",
    to: "/destinations",
  },
  {
    label: "Stays",
    to: "/stays",
  },
  {
    label: "Bucket List",
    to: "/bucket-list",
  },
  {
    label: "Guides",
    to: "/guides",
  },
  {
    label: "My Trips",
    to: "/trips",
  },
];

export default function Navbar() {
  const { user, loading, logout } = useAuth();

  const navigate = useNavigate();

  const [mobileOpen, setMobileOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);

  const accountRef = useRef(null);

  const initials = getInitials(user?.fullName);
  const role = formatRole(user?.role);

  /*
   * ============================================================
   * ROLE-AWARE ACCOUNT SUMMARY
   *
   * A user can hold several roles at once, for example a traveler who
   * is also an approved hotel and cab partner. Every approved portal is
   * therefore listed, rather than just one.
   * ============================================================
   */
  const isAdmin = userHasRole(user, "ROLE_SUPER_ADMIN");

  const partnerPortals = getPartnerPortals(user);

  const portalRoute = isAdmin
    ? getDefaultRouteForRole("ROLE_SUPER_ADMIN")
    : partnerPortals[0]?.to ?? "/become-a-partner";

  /*
   * Only nudge travelers who have not applied for anything yet. Once a
   * partner role is approved the "Become a Partner" prompt would be
   * noise, so it is replaced by the portal links.
   */
  const showBecomePartnerCta = Boolean(
    user && !isAdmin && partnerPortals.length === 0
  );

  const [portalOpen, setPortalOpen] = useState(false);

  const portalRef = useRef(null);

  /*
   * ============================================================
   * CLOSE ACCOUNT DROPDOWN WHEN CLICKING OUTSIDE
   * ============================================================
   */
  useEffect(() => {
    function handleOutsideClick(event) {
      if (
        accountRef.current &&
        !accountRef.current.contains(event.target)
      ) {
        setAccountOpen(false);
      }

      if (
        portalRef.current &&
        !portalRef.current.contains(event.target)
      ) {
        setPortalOpen(false);
      }
    }

    document.addEventListener("mousedown", handleOutsideClick);

    return () => {
      document.removeEventListener("mousedown", handleOutsideClick);
    };
  }, []);

  /*
   * ============================================================
   * CLOSE MENUS WHEN ESCAPE IS PRESSED
   * ============================================================
   */
  useEffect(() => {
    function handleEscape(event) {
      if (event.key !== "Escape") {
        return;
      }

      setAccountOpen(false);
      setMobileOpen(false);
      setPortalOpen(false);
    }

    document.addEventListener("keydown", handleEscape);

    return () => {
      document.removeEventListener("keydown", handleEscape);
    };
  }, []);

  /*
   * ============================================================
   * CLOSE MOBILE MENU AFTER NAVIGATION
   * ============================================================
   */
  function closeMobileMenu() {
    setMobileOpen(false);
  }

  function handleAccountNavigation(path) {
    setAccountOpen(false);
    setMobileOpen(false);
    setPortalOpen(false);
    navigate(path);
  }

  async function handleLogout() {
    setAccountOpen(false);
    setMobileOpen(false);

    await logout();

    navigate("/login");
  }

  return (
    <header className="sticky top-0 z-50 border-b border-slate-200/80 bg-white/95 shadow-sm backdrop-blur-xl">
      <div className="mx-auto flex h-[72px] max-w-7xl items-center justify-between gap-4 px-5 sm:px-6 lg:px-8">
        {/* ========================================================
            BRAND
            ======================================================== */}
        <Link
          to="/"
          onClick={closeMobileMenu}
          className="group flex shrink-0 items-center gap-2.5"
        >
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-950 text-white shadow-sm transition-transform duration-300 group-hover:scale-105">
            <Compass size={21} strokeWidth={1.8} />
          </span>

          <span className="hidden text-lg font-bold tracking-tight text-slate-950 min-[400px]:block">
            Travel<span className="text-slate-500">Buddy</span>
          </span>
        </Link>

        {/* ========================================================
            DESKTOP NAVIGATION
            ======================================================== */}
        <nav className="hidden items-center gap-1 lg:flex">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                [
                  "rounded-xl px-3.5 py-2.5 text-sm font-semibold transition-all duration-200",
                  isActive
                    ? "bg-slate-100 text-slate-950"
                    : "text-slate-500 hover:bg-slate-50 hover:text-slate-950",
                ].join(" ")
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* ========================================================
            DESKTOP ACCOUNT
            ======================================================== */}
        <div className="hidden items-center gap-3 sm:flex" ref={accountRef}>
          {/* ======================================================
              NOTIFICATIONS (FR-25)

              Rendered inside the signed-in cluster so it inherits
              the same "only when authenticated" rule as the
              account menu.
              ====================================================== */}
          <NotificationBell />

          {isAdmin && (
            <Link
              to={getDefaultRouteForRole("ROLE_SUPER_ADMIN")}
              className="inline-flex items-center gap-1.5 rounded-xl border border-purple-500/30 bg-purple-50 px-3.5 py-2 text-xs font-bold text-purple-900 shadow-sm transition hover:bg-purple-100"
            >
              <Shield size={14} className="text-purple-600" />
              <span>Admin Hub</span>
              <ArrowUpRight size={13} className="opacity-70" />
            </Link>
          )}

          {/*
            Every approved partner portal is listed. A user can hold
            more than one, so this is a dropdown rather than a single
            link.
          */}
          {partnerPortals.length === 1 && (
            <Link
              to={partnerPortals[0].to}
              className="inline-flex items-center gap-1.5 rounded-xl border border-cyan-500/30 bg-cyan-50 px-3.5 py-2 text-xs font-bold text-cyan-900 shadow-sm transition hover:bg-cyan-100"
            >
              {partnerPortals[0].role === "ROLE_HOTEL_PARTNER" ? (
                <>
                  <Building2 size={14} className="text-cyan-700" />
                  <span>Hotel Portal</span>
                </>
              ) : partnerPortals[0].role === "ROLE_GUIDE_PARTNER" ? (
                <>
                  <Compass size={14} className="text-emerald-700" />
                  <span>Guide Portal</span>
                </>
              ) : (
                <>
                  <Car size={14} className="text-amber-700" />
                  <span>Cab Portal</span>
                </>
              )}
              <ArrowUpRight size={13} className="opacity-70" />
            </Link>
          )}

          {partnerPortals.length > 1 && (
            <div className="relative" ref={portalRef}>
              <button
                type="button"
                onClick={() => setPortalOpen((open) => !open)}
                className="inline-flex items-center gap-1.5 rounded-xl border border-cyan-500/30 bg-cyan-50 px-3.5 py-2 text-xs font-bold text-cyan-900 shadow-sm transition hover:bg-cyan-100"
              >
                Partner Portals
                <ChevronDown size={13} className="opacity-70" />
              </button>

              {portalOpen && (
                <div className="absolute right-0 top-full z-50 mt-2 w-52 overflow-hidden rounded-2xl border border-slate-200 bg-white p-1.5 shadow-xl">
                  {partnerPortals.map((portal) => (
                    <button
                      key={portal.role}
                      type="button"
                      onClick={() =>
                        handleAccountNavigation(portal.to)
                      }
                      className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-left text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                    >
                      {portal.role === "ROLE_HOTEL_PARTNER" ? (
                        <Building2 size={16} className="text-cyan-700" />
                      ) : portal.role === "ROLE_GUIDE_PARTNER" ? (
                        <Compass size={16} className="text-emerald-700" />
                      ) : (
                        <Car size={16} className="text-amber-700" />
                      )}
                      {portal.role === "ROLE_HOTEL_PARTNER"
                        ? "Hotel Dashboard"
                        : portal.role === "ROLE_GUIDE_PARTNER"
                          ? "Guide Dashboard"
                          : "Cab Dashboard"}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          {showBecomePartnerCta && (
            <Link
              to="/become-a-partner"
              className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 px-3.5 py-2 text-xs font-bold text-slate-700 shadow-sm transition hover:border-cyan-400/40 hover:text-cyan-700"
            >
              Become a Partner
              <ArrowUpRight size={13} className="opacity-70" />
            </Link>
          )}

          {loading ? (
            <div className="h-10 w-32 animate-pulse rounded-xl bg-slate-100" />
          ) : user ? (
            <div className="relative">
              <button
                type="button"
                onClick={() => setAccountOpen((current) => !current)}
                aria-expanded={accountOpen}
                aria-haspopup="menu"
                className="flex items-center gap-2.5 rounded-2xl border border-slate-200 bg-white px-2 py-1.5 text-left transition-all duration-200 hover:border-slate-300 hover:bg-slate-50"
              >
                <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-slate-950 text-xs font-bold text-white">
                  {initials}
                </span>

                <span className="hidden max-w-[130px] xl:block">
                  <span className="block truncate text-xs font-bold text-slate-950">
                    {user.fullName}
                  </span>

                  <span className="mt-0.5 block text-[10px] font-medium text-slate-400">
                    {role}
                  </span>
                </span>

                <ChevronDown
                  size={16}
                  className={`mr-1 text-slate-400 transition-transform duration-200 ${
                    accountOpen ? "rotate-180" : ""
                  }`}
                />
              </button>

              {/* ==================================================
                  ACCOUNT DROPDOWN
                  ================================================== */}
              {accountOpen && (
                <div
                  role="menu"
                  className="absolute right-0 top-[calc(100%+10px)] w-[290px] overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl shadow-slate-950/10"
                >
                  {/* Account identity */}
                  <div className="border-b border-slate-100 bg-slate-50/80 p-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-slate-950 text-sm font-bold text-white">
                        {initials}
                      </div>

                      <div className="min-w-0">
                        <p className="truncate text-sm font-bold text-slate-950">
                          {user.fullName}
                        </p>

                        <p className="mt-0.5 truncate text-xs text-slate-500">
                          {user.email}
                        </p>

                        <span className="mt-2 inline-flex rounded-full bg-white px-2 py-1 text-[9px] font-bold uppercase tracking-wider text-slate-500 ring-1 ring-slate-200">
                          {role}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Menu items */}
                  <div className="p-2">
                    {isAdmin && (
                      <button
                        type="button"
                        role="menuitem"
                        onClick={() =>
                          handleAccountNavigation(
                            getDefaultRouteForRole("ROLE_SUPER_ADMIN")
                          )
                        }
                        className="flex w-full items-center gap-3 rounded-xl bg-slate-900 px-3 py-3 text-left text-white transition-colors hover:bg-slate-800 mb-1"
                      >
                        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/10 text-white">
                          <Shield size={17} />
                        </span>

                        <span>
                          <span className="block text-sm font-semibold">
                            Admin Console
                          </span>

                          <span className="block text-[11px] text-white/60">
                            Switch to operations hub
                          </span>
                        </span>
                      </button>
                    )}

                    {/* Every approved partner portal is listed, so a
                        multi-partner account can switch between them. */}
                    {partnerPortals.map((portal) => (
                      <button
                        key={portal.role}
                        type="button"
                        role="menuitem"
                        onClick={() =>
                          handleAccountNavigation(portal.to)
                        }
                        className="flex w-full items-center gap-3 rounded-xl bg-slate-900 px-3 py-3 text-left text-white transition-colors hover:bg-slate-800 mb-1"
                      >
                        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/10 text-white">
                          {portal.role === "ROLE_HOTEL_PARTNER" ? (
                            <Building2 size={17} />
                          ) : portal.role === "ROLE_GUIDE_PARTNER" ? (
                            <Compass size={17} />
                          ) : (
                            <Car size={17} />
                          )}
                        </span>

                        <span>
                          <span className="block text-sm font-semibold">
                            {portal.role === "ROLE_HOTEL_PARTNER"
                              ? "Hotel Dashboard"
                              : portal.role === "ROLE_GUIDE_PARTNER"
                                ? "Guide Dashboard"
                                : "Cab Dashboard"}
                          </span>

                          <span className="block text-[11px] text-white/60">
                            Switch to partner portal
                          </span>
                        </span>
                      </button>
                    ))}

                    {/* Travelers with no partner role yet are invited
                        to apply. Applying never changes the account
                        immediately; it starts a reviewed application. */}
                    {showBecomePartnerCta && (
                      <button
                        type="button"
                        role="menuitem"
                        onClick={() =>
                          handleAccountNavigation("/become-a-partner")
                        }
                        className="flex w-full items-center gap-3 rounded-xl border border-cyan-500/30 bg-cyan-50 px-3 py-3 text-left transition-colors hover:bg-cyan-100 mb-1"
                      >
                        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white text-cyan-700">
                          <Building2 size={17} />
                        </span>

                        <span>
                          <span className="block text-sm font-semibold text-cyan-900">
                            Become a Partner
                          </span>

                          <span className="block text-[11px] text-cyan-700/80">
                            Apply as a hotel, guide or cab partner
                          </span>
                        </span>
                      </button>
                    )}

                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => handleAccountNavigation("/profile")}
                      className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-slate-50"
                    >
                      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
                        <UserRound size={17} />
                      </span>

                      <span>
                        <span className="block text-sm font-semibold text-slate-900">
                          My Profile
                        </span>

                        <span className="block text-[11px] text-slate-400">
                          View your account
                        </span>
                      </span>
                    </button>

                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => handleAccountNavigation("/bookings")}
                      className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-slate-50"
                    >
                      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
                        <CalendarDays size={17} />
                      </span>

                      <span>
                        <span className="block text-sm font-semibold text-slate-900">
                          My Bookings
                        </span>

                        <span className="block text-[11px] text-slate-400">
                          View reservations
                        </span>
                      </span>
                    </button>

                    <button
                      type="button"
                      role="menuitem"
                      onClick={() =>
                        handleAccountNavigation("/bucket-list")
                      }
                      className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-slate-50"
                    >
                      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
                        <Bookmark size={17} />
                      </span>

                      <span>
                        <span className="block text-sm font-semibold text-slate-900">
                          Bucket List
                        </span>

                        <span className="block text-[11px] text-slate-400">
                          Saved destinations
                        </span>
                      </span>
                    </button>
                  </div>

                  {/* Logout */}
                  <div className="border-t border-slate-100 p-2">
                    <button
                      type="button"
                      role="menuitem"
                      onClick={handleLogout}
                      className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left transition-colors hover:bg-red-50"
                    >
                      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-red-50 text-red-600">
                        <LogOut size={17} />
                      </span>

                      <span>
                        <span className="block text-sm font-semibold text-red-600">
                          Sign out
                        </span>

                        <span className="block text-[11px] text-slate-400">
                          End this session
                        </span>
                      </span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <>
              <Link
                to="/login"
                className="rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 hover:text-slate-950"
              >
                Sign in
              </Link>

              <Link
                to="/register"
                className="rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800"
              >
                Create account
              </Link>

              {/* Public entry point to the partner journey. */}
              <Link
                to="/become-a-partner"
                className="rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 hover:text-slate-950"
              >
                Become a Partner
              </Link>
            </>
          )}
        </div>

        {/* ========================================================
            MOBILE CONTROLS
            ======================================================== */}
        <div className="flex items-center gap-2 sm:hidden">
          {user && !loading && (
            <button
              type="button"
              onClick={() =>
                handleAccountNavigation(
                  isAdmin || partnerPortals.length > 0
                    ? portalRoute
                    : "/profile"
                )
              }
              aria-label="Open profile"
              className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-950 text-xs font-bold text-white"
            >
              {initials}
            </button>
          )}

          <button
            type="button"
            onClick={() => setMobileOpen((current) => !current)}
            aria-label={mobileOpen ? "Close menu" : "Open menu"}
            aria-expanded={mobileOpen}
            className="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 text-slate-700 transition hover:bg-slate-50"
          >
            {mobileOpen ? <X size={21} /> : <Menu size={21} />}
          </button>
        </div>
      </div>

      {/* ============================================================
          MOBILE MENU
          ============================================================ */}
      {mobileOpen && (
        <div className="border-t border-slate-100 bg-white shadow-lg sm:hidden">
          <div className="mx-auto max-w-7xl px-5 py-4">
            <nav className="space-y-1">
              {navItems.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  onClick={closeMobileMenu}
                  className={({ isActive }) =>
                    [
                      "block rounded-xl px-4 py-3 text-sm font-semibold transition",
                      isActive
                        ? "bg-slate-100 text-slate-950"
                        : "text-slate-600 hover:bg-slate-50 hover:text-slate-950",
                    ].join(" ")
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>

            <div className="mt-4 border-t border-slate-100 pt-4">
              {user ? (
                <>
                  <div className="mb-3 flex items-center gap-3 rounded-2xl bg-slate-50 p-3">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-slate-950 text-sm font-bold text-white">
                      {initials}
                    </div>

                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold text-slate-950">
                        {user.fullName}
                      </p>

                      <p className="truncate text-xs text-slate-500">
                        {user.email}
                      </p>

                      <p className="mt-1 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                        {role}
                      </p>
                    </div>
                  </div>

                  {isAdmin && (
                    <button
                      type="button"
                      onClick={() => handleAccountNavigation(portalRoute)}
                      className="flex w-full items-center gap-3 rounded-xl bg-slate-900 px-3 py-3 text-left text-sm font-semibold text-white mb-2"
                    >
                      <ArrowUpRight size={18} />
                      Go to Admin Hub
                    </button>
                  )}

                  {partnerPortals.map((portal) => (
                    <button
                      key={portal.role}
                      type="button"
                      onClick={() =>
                        handleAccountNavigation(portal.to)
                      }
                      className="flex w-full items-center gap-3 rounded-xl bg-slate-900 px-3 py-3 text-left text-sm font-semibold text-white mb-2"
                    >
                      <ArrowUpRight size={18} />
                      {portal.role === "ROLE_HOTEL_PARTNER"
                        ? "Hotel Dashboard"
                        : portal.role === "ROLE_GUIDE_PARTNER"
                          ? "Guide Dashboard"
                          : "Cab Dashboard"}
                    </button>
                  ))}

                  {showBecomePartnerCta && (
                    <button
                      type="button"
                      onClick={() =>
                        handleAccountNavigation("/become-a-partner")
                      }
                      className="flex w-full items-center gap-3 rounded-xl border border-cyan-500/30 bg-cyan-50 px-3 py-3 text-left text-sm font-semibold text-cyan-900 mb-2"
                    >
                      <Building2 size={18} />
                      Become a Partner
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() =>
                      handleAccountNavigation("/profile")
                    }
                    className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-sm font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <UserRound size={18} />
                    My Profile
                  </button>

                  <button
                    type="button"
                    onClick={() =>
                      handleAccountNavigation("/bookings")
                    }
                    className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-sm font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <CalendarDays size={18} />
                    My Bookings
                  </button>

                  <button
                    type="button"
                    onClick={() =>
                      handleAccountNavigation("/bucket-list")
                    }
                    className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-sm font-semibold text-slate-700 hover:bg-slate-50"
                  >
                    <Bookmark size={18} />
                    Bucket List
                  </button>

                  <button
                    type="button"
                    onClick={handleLogout}
                    className="mt-2 flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-sm font-semibold text-red-600 hover:bg-red-50"
                  >
                    <LogOut size={18} />
                    Sign out
                  </button>
                </>
              ) : (
                <div className="grid gap-2">
                  <Link
                    to="/login"
                    onClick={closeMobileMenu}
                    className="rounded-xl border border-slate-200 px-4 py-3 text-center text-sm font-semibold text-slate-700"
                  >
                    Sign in
                  </Link>

                  <Link
                    to="/register"
                    onClick={closeMobileMenu}
                    className="rounded-xl bg-slate-950 px-4 py-3 text-center text-sm font-semibold text-white"
                  >
                    Create account
                  </Link>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
