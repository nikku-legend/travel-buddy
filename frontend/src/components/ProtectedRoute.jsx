import { Navigate, Outlet, useLocation } from "react-router-dom";
import { ShieldAlert } from "lucide-react";
import { useAuth } from "../context/useAuth";

/**
 * The partner portal each role lands on.
 *
 * A single user can hold several partner roles at once, so the
 * destination is chosen from the whole role list rather than one field.
 */
export const PARTNER_PORTAL_BY_ROLE = {
  ROLE_HOTEL_PARTNER: "/partner/hotel",
  ROLE_GUIDE_PARTNER: "/partner/guide",
  ROLE_CAB_PARTNER: "/partner/cab",
};

/**
 * Reads the full role list, tolerating older backend responses that only
 * send a single `role` string.
 */
export function getUserRoles(user) {
  if (!user) {
    return [];
  }

  if (Array.isArray(user.roles) && user.roles.length > 0) {
    return user.roles;
  }

  return user.role ? [user.role] : [];
}

export function userHasRole(user, role) {
  return getUserRoles(user).includes(role);
}

/**
 * Every partner portal the account may open, so the navbar can list all
 * of them rather than only the first.
 */
export function getPartnerPortals(user) {
  return getUserRoles(user)
    .filter((role) => PARTNER_PORTAL_BY_ROLE[role])
    .map((role) => ({
      role,
      to: PARTNER_PORTAL_BY_ROLE[role],
      label: PARTNER_PORTAL_BY_ROLE[role].split("/").pop(),
    }));
}

export function getDefaultRouteForRole(role) {
  switch (role) {
    case "ROLE_SUPER_ADMIN":
      return "/admin";
    case "ROLE_HOTEL_PARTNER":
      return "/partner/hotel";
    case "ROLE_GUIDE_PARTNER":
      return "/partner/guide";
    case "ROLE_CAB_PARTNER":
      return "/partner/cab";
    case "ROLE_USER":
    default:
      return "/";
  }
}

export default function ProtectedRoute({ allowedRoles = null }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  /*
   * ------------------------------------------------------------
   * AUTHENTICATION RESTORATION
   * ------------------------------------------------------------
   */
  if (loading) {
    return (
      <div className="min-h-[calc(100vh-72px)] bg-slate-950 px-5 py-12 text-white">
        <div className="mx-auto flex min-h-[60vh] max-w-2xl items-center justify-center">
          <div className="w-full rounded-3xl border border-white/10 bg-white/[0.04] p-8 text-center shadow-2xl backdrop-blur-xl sm:p-10">
            <div className="mx-auto h-12 w-12 animate-spin rounded-full border-2 border-white/10 border-t-white" />

            <h2 className="mt-6 text-xl font-semibold">
              Restoring your session
            </h2>

            <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-white/50">
              Please wait while Travel Buddy verifies your account.
            </p>
          </div>
        </div>
      </div>
    );
  }

  /*
   * ------------------------------------------------------------
   * NOT AUTHENTICATED
   * ------------------------------------------------------------
   */
  if (!user) {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          from: {
            pathname: location.pathname,
            search: location.search,
            hash: location.hash,
          },
        }}
      />
    );
  }

  /*
   * ------------------------------------------------------------
   * ROLE AUTHORIZATION CHECK
   * ------------------------------------------------------------
   */
  if (allowedRoles && allowedRoles.length > 0) {
    const hasRole = allowedRoles.some((role) =>
      userHasRole(user, role)
    );

    if (!hasRole) {
      return (
        <div className="min-h-[calc(100vh-72px)] bg-slate-950 px-5 py-16 text-white">
          <div className="mx-auto max-w-md rounded-3xl border border-rose-500/20 bg-rose-500/10 p-8 text-center backdrop-blur-xl">
            <ShieldAlert className="mx-auto h-14 w-14 text-rose-400" />
            <h1 className="mt-4 text-2xl font-bold">Access Restricted</h1>
            <p className="mt-2 text-sm text-slate-300">
              You do not have the required permissions to view this portal.
            </p>
            <div className="mt-6">
              <a
                href={getDefaultRouteForRole(user.role)}
                className="inline-block rounded-xl bg-white px-5 py-2.5 text-sm font-semibold text-slate-950 hover:bg-slate-200"
              >
                Go to your dashboard
              </a>
            </div>
          </div>
        </div>
      );
    }
  }

  /*
   * ------------------------------------------------------------
   * AUTHENTICATED & AUTHORIZED
   * ------------------------------------------------------------
   */
  return <Outlet />;
}
