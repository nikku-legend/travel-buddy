import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ArrowRight, Building2, Car, Compass, Loader2 } from "lucide-react";

import { useAuth } from "../../context/useAuth";
import partnerService from "../../services/partnerService";

const ICONS = {
  building: Building2,
  compass: Compass,
  car: Car,
};

const STATUS_STYLES = {
  APPROVED: "border-emerald-500/30 bg-emerald-500/10 text-emerald-300",
  PENDING_REVIEW: "border-amber-500/30 bg-amber-500/10 text-amber-300",
  UNDER_REVIEW: "border-amber-500/30 bg-amber-500/10 text-amber-300",
  PENDING_CORRECTION: "border-amber-500/30 bg-amber-500/10 text-amber-300",
  REJECTED: "border-rose-500/30 bg-rose-500/10 text-rose-300",
  WITHDRAWN: "border-white/15 bg-white/5 text-white/50",
};

/*
 * ============================================================
 * BECOME A PARTNER
 *
 * Renders the three partner types straight from the backend catalogue,
 * so the required fields and documents are never hard-coded here and
 * can be changed by an admin.
 *
 * Public: a guest can see what each partner type needs before signing
 * up. Choosing a type as a guest sends them through login/register and
 * then straight back into the application.
 * ============================================================
 */

export default function BecomePartner() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();

  const [types, setTypes] = useState([]);
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const catalogue = await partnerService.getPartnerTypes();

        if (!active) return;

        setTypes(catalogue);

        /*
         * Application status is per-account, so only fetch it once the
         * visitor is known to be signed in.
         */
        if (user) {
          const mine =
            await partnerService.getMyApplications();

          if (active) setApplications(mine);
        }
      } catch (err) {
        if (active) {
          setError(
            err.response?.data?.message ||
              "Could not load partner types."
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    load();

    return () => {
      active = false;
    };
  }, [user]);

  function applicationFor(partnerType) {
    return applications.find(
      (application) => application.partnerType === partnerType
    );
  }

  function handleChoose(partnerType) {
    if (!user) {
      /*
       * Preserve the intent so login/register can return the visitor
       * here instead of dumping them on the homepage.
       */
      navigate("/register", {
        state: {
          becomePartner: true,
          partnerType,
          from: location.pathname,
        },
      });

      return;
    }

    navigate(`/partner/apply/${partnerType}`);
  }

  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-72px)] items-center justify-center bg-slate-950 text-white">
        <Loader2 className="h-8 w-8 animate-spin text-cyan-400" />
      </div>
    );
  }

  return (
    <div className="min-h-[calc(100vh-72px)] bg-slate-950 px-5 py-14 text-white">
      <div className="mx-auto max-w-5xl">
        <p className="text-xs font-bold uppercase tracking-[0.22em] text-cyan-400">
          Travel Buddy Partners
        </p>

        <h1 className="mt-3 text-4xl font-black tracking-tight sm:text-5xl">
          Grow with Travel Buddy
        </h1>

        <p className="mt-4 max-w-2xl text-base leading-7 text-slate-300">
          One account, every portal. Keep your traveler profile and
          bookings, and add a partner role once Travel Buddy verifies
          your business.
        </p>

        {error && (
          <div className="mt-6 rounded-2xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-300">
            {error}
          </div>
        )}

        <div className="mt-10 grid gap-5 md:grid-cols-3">
          {types.map((option) => {
            const IconComponent =
              ICONS[option.icon] ?? Building2;

            const application = applicationFor(option.type);
            const statusClass =
              STATUS_STYLES[application?.status] ?? "";

            return (
              <button
                key={option.type}
                type="button"
                onClick={() => handleChoose(option.type)}
                className="group flex h-full flex-col rounded-3xl border border-white/10 bg-white/[0.04] p-6 text-left transition hover:-translate-y-1 hover:border-cyan-400/40 hover:bg-white/[0.07]"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="rounded-2xl bg-cyan-400/10 p-3">
                    <IconComponent className="h-6 w-6 text-cyan-400" />
                  </div>

                  {application && (
                    <span
                      className={`rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider ${statusClass}`}
                    >
                      {application.statusLabel}
                    </span>
                  )}
                </div>

                <h2 className="mt-5 text-lg font-bold">
                  {option.label}
                </h2>

                <p className="mt-2 flex-1 text-sm leading-6 text-slate-400">
                  {option.description}
                </p>

                {application?.rejectionReason && (
                  <p className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs leading-5 text-rose-200">
                    {application.rejectionReason}
                  </p>
                )}

                {option.requiredDocuments?.length > 0 && (
                  <p className="mt-4 text-xs text-slate-500">
                    {option.requiredDocuments.length} document
                    {option.requiredDocuments.length === 1
                      ? ""
                      : "s"}{" "}
                    required
                  </p>
                )}

                <span className="mt-5 inline-flex items-center gap-1.5 text-sm font-semibold text-cyan-400">
                  {application?.status === "APPROVED"
                    ? "Open portal"
                    : application
                      ? "View application"
                      : "Start application"}
                  <ArrowRight className="h-4 w-4 transition group-hover:translate-x-1" />
                </span>
              </button>
            );
          })}
        </div>

        <div className="mt-12 rounded-3xl border border-white/10 bg-white/[0.03] p-6">
          <h3 className="text-sm font-bold uppercase tracking-wider text-slate-300">
            How it works
          </h3>

          <ol className="mt-4 grid gap-4 text-sm text-slate-400 sm:grid-cols-4">
            {[
              "Choose your partner type",
              "Fill your business details",
              "Upload your KYC documents",
              "Admin reviews and activates you",
            ].map((step, index) => (
              <li key={step} className="flex gap-3">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-cyan-400/10 text-xs font-bold text-cyan-400">
                  {index + 1}
                </span>
                <span className="pt-1 leading-6">{step}</span>
              </li>
            ))}
          </ol>
        </div>
      </div>
    </div>
  );
}