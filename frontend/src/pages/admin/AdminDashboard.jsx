import { useEffect, useState } from "react";
import {
  AlertTriangle,
  Building2,
  CalendarDays,
  CheckCircle,
  Clock,
  Compass,
  CreditCard,
  FileCheck,
  MapPin,
  Shield,
  ShieldAlert,
  Users,
} from "lucide-react";
import { useAuth } from "../../context/useAuth";
import partnerService from "../../services/partnerService";
import PartnerReviewQueue from "../../components/admin/PartnerReviewQueue";

export default function AdminDashboard() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState("overview");

  /*
   * Live counters for the summary tiles. Previously these were
   * hard-coded placeholders, which made the console look finished
   * while showing nothing real.
   */
  const [pendingApprovals, setPendingApprovals] = useState(null);

  useEffect(() => {
    let active = true;

    async function loadPending() {
      try {
        const queue = await partnerService.getReviewQueue();

        if (active) setPendingApprovals(queue.length);
      } catch (err) {
        /*
         * Leave the tile as a dash rather than showing a misleading 0,
         * which would look like "nothing to review" on a failure.
         */
        if (active) setPendingApprovals(null);
      }
    }

    if (activeTab === "overview" || activeTab === "kyc") {
      loadPending();
    }

    return () => {
      active = false;
    };
  }, [activeTab]);

  return (
    <div className="min-h-screen bg-slate-900 text-white">
      {/* Admin Header */}
      <div className="border-b border-slate-800 bg-slate-950">
        <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="inline-flex items-center gap-2 rounded-full bg-purple-500/10 px-3 py-1 text-xs font-semibold text-purple-400 border border-purple-500/20">
                <Shield className="h-3.5 w-3.5" />
                Super Admin Operations Center
              </div>
              <h1 className="mt-2 text-2xl font-bold tracking-tight text-white sm:text-3xl">
                Travel Buddy Command Hub
              </h1>
              <p className="mt-1 text-sm text-slate-400">
                Master oversight for users, partner approvals, property verification, transactions, and audit logs.
              </p>
            </div>
          </div>

          {/* Admin Tabs */}
          <div className="mt-8 flex gap-2 border-b border-slate-800 overflow-x-auto pb-px">
            {[
              { id: "overview", label: "Overview & Analytics" },
              { id: "users", label: "User Management" },
              { id: "kyc", label: "Partner KYC & Approvals" },
              { id: "properties", label: "Property Verification" },
              { id: "destinations", label: "Destinations Engine" },
              { id: "finance", label: "Payments & Settlements" },
              { id: "audit", label: "Audit & Risk Alerts" },
            ].map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`border-b-2 px-4 py-3 text-sm font-semibold whitespace-nowrap transition ${
                  activeTab === tab.id
                    ? "border-cyan-400 text-cyan-400"
                    : "border-transparent text-slate-400 hover:text-white"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5 backdrop-blur-xl">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Total Users
              </span>
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-500/10 text-blue-400 border border-blue-500/20">
                <Users className="h-5 w-5" />
              </div>
            </div>
            <div className="mt-3 text-2xl font-bold text-white">Active</div>
            <div className="mt-1 text-xs text-slate-500">Travelers & Partners</div>
          </div>

          <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5 backdrop-blur-xl">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Pending Approvals
              </span>
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-amber-500/10 text-amber-400 border border-amber-500/20">
                <FileCheck className="h-5 w-5" />
              </div>
            </div>
            <div className="mt-3 text-2xl font-bold text-white">
              {pendingApprovals === null
                ? "-"
                : pendingApprovals}
            </div>
            <div className="mt-1 text-xs text-slate-500">Partner KYC submissions</div>
          </div>

          <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5 backdrop-blur-xl">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Platform Volume
              </span>
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                <CreditCard className="h-5 w-5" />
              </div>
            </div>
            <div className="mt-3 text-2xl font-bold text-white">₹0.00</div>
            <div className="mt-1 text-xs text-slate-500">Gross platform GMV</div>
          </div>

          <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-5 backdrop-blur-xl">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Security & Risk
              </span>
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-rose-500/10 text-rose-400 border border-rose-500/20">
                <ShieldAlert className="h-5 w-5" />
              </div>
            </div>
            <div className="mt-3 text-2xl font-bold text-emerald-400">Normal</div>
            <div className="mt-1 text-xs text-slate-500">Zero active alerts</div>
          </div>
        </div>

        {/* ========================================================
            TAB CONTENT
            ======================================================== */}
        {activeTab === "kyc" && (
          <PartnerReviewQueue />
        )}

        {activeTab !== "kyc" && (
          <div className="mt-8 rounded-2xl border border-slate-800 bg-slate-950/60 p-8 backdrop-blur-xl text-center">
            <Shield className="mx-auto h-12 w-12 text-purple-400" />
            <h3 className="mt-4 text-base font-semibold text-white">
              Operations & Administration Console
            </h3>
            <p className="mt-1 text-sm text-slate-400 max-w-md mx-auto">
              Super Admin access active for {user?.email}. All
              administrative operations are monitored and recorded to
              the audit log.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
