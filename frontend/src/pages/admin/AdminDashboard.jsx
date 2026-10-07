import { useCallback, useEffect, useState } from "react";
import { Shield } from "lucide-react";
import { useAuth } from "../../context/useAuth";
import adminService from "../../services/adminService";
import PartnerReviewQueue from "../../components/admin/PartnerReviewQueue";
import OverviewTab from "../../components/admin/OverviewTab";
import UsersTab from "../../components/admin/UsersTab";
import PropertiesQueue from "../../components/admin/PropertiesQueue";
import DestinationsTab from "../../components/admin/DestinationsTab";
import FinanceTab from "../../components/admin/FinanceTab";
import AuditTab from "../../components/admin/AuditTab";

export default function AdminDashboard() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState("overview");

  /*
   * Overview statistics are fetched once and owned here, so the
   * tiles and the queues can never disagree. Every other tab
   * loads its own data when it mounts.
   */
  const [stats, setStats] = useState(null);
  const [statsLoading, setStatsLoading] = useState(false);
  const [statsError, setStatsError] = useState("");

  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    setStatsError("");
    try {
      setStats(await adminService.getStats());
    } catch (err) {
      setStatsError(
        err?.response?.data?.message || "Could not load statistics."
      );
    } finally {
      setStatsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (
      activeTab === "overview" &&
      !stats &&
      !statsError &&
      !statsLoading
    ) {
      loadStats();
    }
  }, [activeTab, stats, statsError, statsLoading, loadStats]);

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
        {/* ========================================================
            TAB CONTENT
            ======================================================== */}
        <div className="mt-8">
          {activeTab === "overview" && (
            <OverviewTab
              stats={stats}
              loading={statsLoading}
              error={statsError}
              onRetry={loadStats}
            />
          )}

          {activeTab === "users" && <UsersTab />}
          {activeTab === "kyc" && <PartnerReviewQueue />}
          {activeTab === "properties" && <PropertiesQueue />}
          {activeTab === "destinations" && <DestinationsTab />}
          {activeTab === "finance" && <FinanceTab />}
          {activeTab === "audit" && <AuditTab />}
        </div>

        <p className="mt-8 text-center text-xs text-slate-600">
          Super Admin session: {user?.email}. All administrative
          operations are recorded to the audit log.
        </p>
      </div>
    </div>
  );
}
