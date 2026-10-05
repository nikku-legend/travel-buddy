import { useEffect, useState } from "react";
import {
  Calendar,
  CheckCircle,
  CheckCircle2,
  Compass,
  Globe2,
  Languages,
  MapPin,
  Search,
  ShieldCheck,
  Sparkles,
  Star,
  Users,
  X,
} from "lucide-react";
import guideService from "../services/guideService";
import destinationService from "../services/destinationService";

export default function Guides() {
  const [guides, setGuides] = useState([]);
  const [states, setStates] = useState([]);
  const [selectedState, setSelectedState] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Availability modal state
  const [selectedGuide, setSelectedGuide] = useState(null);
  const [checkDate, setCheckDate] = useState("");
  const [availabilityResult, setAvailabilityResult] = useState(null);
  const [checkingAvailability, setCheckingAvailability] = useState(false);

  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true);
        setError("");

        const [guidesData, statesData] = await Promise.all([
          guideService.getGuides(),
          destinationService.getStates(1).catch(() => []),
        ]);

        setGuides(guidesData || []);
        setStates(statesData || []);
      } catch (err) {
        console.error("Failed to load guides:", err);
        setError("Unable to load guides right now. Please try again later.");
      } finally {
        setLoading(false);
      }
    }

    loadData();
  }, []);

  async function handleStateFilter(stateId) {
    setSelectedState(stateId);
    try {
      setLoading(true);
      const data = await guideService.getGuides(stateId || null);
      setGuides(data || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }

  async function handleCheckAvailability(e) {
    e.preventDefault();
    if (!selectedGuide || !checkDate) return;

    try {
      setCheckingAvailability(true);
      setAvailabilityResult(null);
      const result = await guideService.checkAvailability(
        selectedGuide.guideId,
        checkDate
      );
      setAvailabilityResult(result);
    } catch (err) {
      console.error(err);
      setAvailabilityResult({ available: false, error: true });
    } finally {
      setCheckingAvailability(false);
    }
  }

  const filteredGuides = guides.filter((g) => {
    const matchesSearch =
      searchQuery === "" ||
      g.fullName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      g.bio?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      g.languages?.some((lang) =>
        lang.toLowerCase().includes(searchQuery.toLowerCase())
      ) ||
      g.stateName?.toLowerCase().includes(searchQuery.toLowerCase());

    return matchesSearch;
  });

  return (
    <div className="min-h-screen bg-slate-950 text-white">
      {/* Hero Section */}
      <section className="relative overflow-hidden border-b border-white/10 bg-slate-900/60 py-16 sm:py-24">
        <div className="pointer-events-none absolute inset-0">
          <div className="absolute -left-40 -top-40 h-96 w-96 rounded-full bg-emerald-500/10 blur-3xl" />
          <div className="absolute right-0 top-1/2 h-96 w-96 rounded-full bg-cyan-500/10 blur-3xl" />
        </div>

        <div className="relative mx-auto max-w-7xl px-5 sm:px-6 lg:px-8">
          <div className="max-w-3xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-emerald-400/30 bg-emerald-400/10 px-4 py-1.5 text-xs font-semibold text-emerald-300 backdrop-blur-xl">
              <Compass className="h-3.5 w-3.5" />
              Verified Local Experts
            </div>
            <h1 className="mt-4 text-4xl font-extrabold tracking-tight sm:text-5xl lg:text-6xl">
              Experience the world with{" "}
              <span className="bg-gradient-to-r from-emerald-300 via-teal-300 to-cyan-300 bg-clip-text text-transparent">
                local certified guides
              </span>
            </h1>
            <p className="mt-4 text-base leading-7 text-slate-300 sm:text-lg">
              Unlock authentic stories, hidden pathways, and rich heritage. Connect directly with licensed guides across India.
            </p>
          </div>

          {/* Search & State Filter Bar */}
          <div className="mt-10 grid gap-4 rounded-3xl border border-white/10 bg-white/[0.04] p-4 backdrop-blur-2xl sm:grid-cols-[1fr_260px]">
            <div className="relative flex items-center">
              <Search className="absolute left-4 h-5 w-5 text-slate-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search by guide name, spoken language, or specialty..."
                className="w-full rounded-2xl border border-white/10 bg-black/30 py-3.5 pl-12 pr-4 text-sm text-white placeholder-slate-400 outline-none transition focus:border-emerald-400"
              />
            </div>

            <div className="relative">
              <select
                value={selectedState}
                onChange={(e) => handleStateFilter(e.target.value)}
                className="w-full appearance-none rounded-2xl border border-white/10 bg-black/30 px-4 py-3.5 text-sm text-white outline-none transition focus:border-emerald-400 cursor-pointer"
              >
                <option value="" className="bg-slate-900 text-white">
                  All Regions / States
                </option>
                {states.map((st) => (
                  <option
                    key={st.stateId}
                    value={st.stateId}
                    className="bg-slate-900 text-white"
                  >
                    {st.name}
                  </option>
                ))}
              </select>
              <MapPin className="pointer-events-none absolute right-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            </div>
          </div>
        </div>
      </section>

      {/* Guide Cards Grid */}
      <section className="mx-auto max-w-7xl px-5 py-12 sm:px-6 lg:px-8">
        {loading ? (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div
                key={i}
                className="h-80 animate-pulse rounded-3xl border border-white/10 bg-white/[0.02]"
              />
            ))}
          </div>
        ) : error ? (
          <div className="rounded-3xl border border-rose-500/20 bg-rose-500/10 p-8 text-center text-rose-300">
            {error}
          </div>
        ) : filteredGuides.length === 0 ? (
          <div className="rounded-3xl border border-white/10 bg-white/[0.02] p-16 text-center">
            <Compass className="mx-auto h-12 w-12 text-slate-500" />
            <h3 className="mt-4 text-lg font-semibold text-white">
              No tour guides found
            </h3>
            <p className="mt-2 text-sm text-slate-400">
              Try adjusting your search criteria or selecting a different state.
            </p>
            {selectedState && (
              <button
                type="button"
                onClick={() => handleStateFilter("")}
                className="mt-6 rounded-xl bg-emerald-400 px-4 py-2 text-xs font-semibold text-slate-950 hover:bg-emerald-300"
              >
                Clear Filters
              </button>
            )}
          </div>
        ) : (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {filteredGuides.map((guide) => (
              <div
                key={guide.guideId}
                className="group flex flex-col justify-between rounded-3xl border border-white/10 bg-white/[0.03] p-6 shadow-xl backdrop-blur-xl transition hover:border-emerald-400/40 hover:bg-white/[0.05]"
              >
                <div>
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400 to-teal-500 text-base font-bold text-slate-950 shadow-md">
                        {guide.fullName
                          ?.split(" ")
                          .map((n) => n[0])
                          .join("")
                          .slice(0, 2)
                          .toUpperCase()}
                      </div>
                      <div>
                        <h3 className="font-bold text-white text-base">
                          {guide.fullName}
                        </h3>
                        <div className="flex items-center gap-1.5 text-xs text-emerald-400 mt-0.5">
                          <ShieldCheck className="h-3.5 w-3.5" />
                          <span>{guide.isVerified ? "Verified Partner" : "Certified Guide"}</span>
                        </div>
                      </div>
                    </div>

                    {/*
                     * No rating is shown when there is none.
                     *
                     * This used to read guide.rating || "5.0",
                     * which turned the absence of reviews into a
                     * perfect score on the client, undoing the
                     * backend's decision not to invent one.
                     */}
                    {guide.rating != null && (
                      <div className="flex items-center gap-1 rounded-xl bg-amber-400/10 px-2.5 py-1 text-xs font-bold text-amber-300 border border-amber-400/20">
                        <Star className="h-3 w-3 fill-amber-300" />
                        <span>{guide.rating}</span>
                        {guide.reviewCount > 0 && (
                          <span className="font-normal text-amber-200/60">
                            ({guide.reviewCount})
                          </span>
                        )}
                      </div>
                    )}
                  </div>

                  {guide.stateName && (
                    <div className="mt-4 flex items-center gap-1.5 text-xs text-slate-400">
                      <MapPin className="h-3.5 w-3.5 text-slate-500" />
                      <span>{guide.stateName}</span>
                      <span className="text-slate-600">•</span>
                      <span>{guide.yearsOfExperience || 1} yrs experience</span>
                    </div>
                  )}

                  <p className="mt-3 text-sm text-slate-300 line-clamp-3 leading-relaxed">
                    {guide.bio ||
                      "Experienced certified local guide passionate about providing culturally immersive and memorable adventures."}
                  </p>

                  {/* Languages */}
                  {guide.languages && guide.languages.length > 0 && (
                    <div className="mt-4 flex flex-wrap gap-1.5">
                      {guide.languages.map((lang, idx) => (
                        <span
                          key={idx}
                          className="rounded-lg border border-white/10 bg-white/[0.04] px-2.5 py-0.5 text-[11px] font-medium text-slate-300"
                        >
                          {lang}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                <div className="mt-6 border-t border-white/10 pt-4 flex items-center justify-between">
                  <div>
                    <span className="text-xs text-slate-400">Daily Rate</span>
                    <div className="text-lg font-bold text-white">
                      ₹{Number(guide.dailyRate || 0).toLocaleString()}
                      <span className="text-xs font-normal text-slate-400"> / day</span>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      setSelectedGuide(guide);
                      setCheckDate("");
                      setAvailabilityResult(null);
                    }}
                    className="flex items-center gap-1.5 rounded-xl bg-emerald-400 px-4 py-2.5 text-xs font-semibold text-slate-950 transition hover:bg-emerald-300 cursor-pointer"
                  >
                    <Calendar className="h-3.5 w-3.5" />
                    Check Dates
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Date Availability Modal */}
      {selectedGuide && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl border border-white/10 bg-slate-900 p-6 shadow-2xl">
            <div className="flex items-center justify-between border-b border-white/10 pb-4">
              <div>
                <h3 className="font-bold text-white text-lg">
                  Check Tour Availability
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  with {selectedGuide.fullName}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setSelectedGuide(null)}
                className="rounded-xl p-1.5 text-slate-400 hover:bg-white/10 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleCheckAvailability} className="mt-5 space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
                  Select Tour Date
                </label>
                <input
                  type="date"
                  required
                  min={new Date().toISOString().split("T")[0]}
                  value={checkDate}
                  onChange={(e) => {
                    setCheckDate(e.target.value);
                    setAvailabilityResult(null);
                  }}
                  className="w-full rounded-xl border border-white/10 bg-black/40 px-4 py-3 text-sm text-white outline-none focus:border-emerald-400"
                />
              </div>

              <button
                type="submit"
                disabled={checkingAvailability || !checkDate}
                className="w-full rounded-xl bg-emerald-400 py-3 text-sm font-semibold text-slate-950 transition hover:bg-emerald-300 disabled:opacity-50 cursor-pointer"
              >
                {checkingAvailability ? "Checking schedule..." : "Check Availability"}
              </button>
            </form>

            {availabilityResult && (
              <div
                className={`mt-4 rounded-2xl border p-4 text-center ${
                  availabilityResult.available
                    ? "border-emerald-400/30 bg-emerald-500/10 text-emerald-300"
                    : "border-rose-400/30 bg-rose-500/10 text-rose-300"
                }`}
              >
                {availabilityResult.available ? (
                  <div>
                    <CheckCircle2 className="mx-auto h-7 w-7 text-emerald-400 mb-1" />
                    <div className="font-semibold text-sm">
                      {selectedGuide.fullName} is Available!
                    </div>
                    <div className="text-xs text-slate-300 mt-1">
                      Rate: ₹{Number(selectedGuide.dailyRate).toLocaleString()} for {checkDate}
                    </div>
                  </div>
                ) : (
                  <div>
                    <div className="font-semibold text-sm">
                      Unavailable on this date
                    </div>
                    <div className="text-xs text-slate-400 mt-1">
                      The guide already has a scheduled tour or is off-duty. Please pick another date.
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
