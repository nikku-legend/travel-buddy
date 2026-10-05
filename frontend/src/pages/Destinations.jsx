import { useEffect, useMemo, useState } from "react";
import {
  ChevronDown,
  Filter,
  Map,
  Search,
  SlidersHorizontal,
  X,
} from "lucide-react";

import destinationService from "../services/destinationService";
import DestinationCard from "../components/DestinationCard";
import Loading from "../components/Loading";
import EmptyState from "../components/EmptyState";

const REGIONS = [
  { value: "", label: "All regions" },
  { value: "NORTH", label: "North" },
  { value: "SOUTH", label: "South" },
  { value: "EAST", label: "East" },
  { value: "WEST", label: "West" },
  { value: "CENTRAL", label: "Central" },
  { value: "NORTH_EAST", label: "North East" },
];

export default function Destinations() {
  const [countries, setCountries] = useState([]);
  const [states, setStates] = useState([]);
  const [destinations, setDestinations] = useState([]);

  const [selectedCountry, setSelectedCountry] = useState("");
  const [selectedRegion, setSelectedRegion] = useState("");
  const [selectedState, setSelectedState] = useState("");

  const [search, setSearch] = useState("");

  const [loadingCountries, setLoadingCountries] = useState(true);
  const [loadingStates, setLoadingStates] = useState(false);
  const [loadingDestinations, setLoadingDestinations] =
    useState(false);

  const [error, setError] = useState("");

  useEffect(() => {
    let isMounted = true;

    async function loadCountries() {
      try {
        setError("");
        const data = await destinationService.getCountries();
        if (isMounted) {
          setCountries(data);
        }
      } catch (err) {
        console.error(err);
        if (isMounted) {
          setError("Unable to load countries.");
        }
      } finally {
        if (isMounted) {
          setLoadingCountries(false);
        }
      }
    }

    loadCountries();

    return () => {
      isMounted = false;
    };
  }, []);

  async function loadStates(countryId, region) {
    if (!countryId) {
      setStates([]);
      return;
    }

    try {
      setLoadingStates(true);
      setError("");

      const data = await destinationService.getStates(
        countryId,
        region || null
      );

      setStates(data);
    } catch (err) {
      console.error(err);
      setError("Unable to load states.");
    } finally {
      setLoadingStates(false);
    }
  }

  async function loadDestinations(stateId) {
    if (!stateId) {
      setDestinations([]);
      return;
    }

    try {
      setLoadingDestinations(true);
      setError("");

      const data =
        await destinationService.getDestinations([stateId]);

      setDestinations(data);
    } catch (err) {
      console.error(err);
      setError("Unable to load destinations.");
    } finally {
      setLoadingDestinations(false);
    }
  }

  function handleCountryChange(event) {
    const countryId = event.target.value;

    setSelectedCountry(countryId);
    setSelectedRegion("");
    setSelectedState("");
    setStates([]);
    setDestinations([]);
    setSearch("");

    loadStates(countryId, "");
  }

  function handleRegionChange(event) {
    const region = event.target.value;

    setSelectedRegion(region);
    setSelectedState("");
    setDestinations([]);
    setSearch("");

    loadStates(selectedCountry, region);
  }

  function handleStateChange(event) {
    const stateId = event.target.value;

    setSelectedState(stateId);
    setSearch("");

    loadDestinations(stateId);
  }

  function clearFilters() {
    setSelectedCountry("");
    setSelectedRegion("");
    setSelectedState("");
    setStates([]);
    setDestinations([]);
    setSearch("");
  }

  const filteredDestinations = useMemo(() => {
    const query = search.trim().toLowerCase();

    if (!query) {
      return destinations;
    }

    return destinations.filter((destination) =>
      `${destination.name} ${destination.description || ""}`
        .toLowerCase()
        .includes(query)
    );
  }, [destinations, search]);

  return (
    <div className="min-h-screen bg-slate-50">

      {/* Header */}
      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto max-w-7xl px-5 py-12 lg:px-8">

          <div className="flex flex-col gap-6 md:flex-row md:items-end md:justify-between">

            <div className="max-w-2xl">

              <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold uppercase tracking-wider text-slate-600">
                <Map size={14} />
                Discover
              </div>

              <h1 className="text-4xl font-bold tracking-tight text-slate-950 sm:text-5xl">
                Find your next
                <span className="text-slate-400">
                  {" "}adventure.
                </span>
              </h1>

              <p className="mt-4 max-w-xl text-base leading-7 text-slate-600">
                Explore destinations across countries, regions and
                states. Save the places you want to experience.
              </p>

            </div>

            <div className="hidden rounded-2xl bg-slate-100 px-5 py-4 sm:block">
              <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Destinations found
              </div>

              <div className="mt-1 text-2xl font-bold text-slate-900">
                {filteredDestinations.length}
              </div>
            </div>

          </div>

        </div>
      </section>

      {/* Filters */}
      <section className="sticky top-[72px] z-30 border-b border-slate-200 bg-white/95 backdrop-blur-xl">
        <div className="mx-auto max-w-7xl px-5 py-4 lg:px-8">

          <div className="flex items-center gap-2 pb-3 lg:hidden">
            <SlidersHorizontal size={17} />
            <span className="text-sm font-semibold">
              Explore filters
            </span>
          </div>

          <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">

            {/* Country */}
            <div className="relative">
              <select
                value={selectedCountry}
                onChange={handleCountryChange}
                disabled={loadingCountries}
                className="w-full appearance-none rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 pr-10 text-sm font-medium text-slate-700 outline-none transition focus:border-slate-900 focus:bg-white disabled:opacity-60"
              >
                <option value="">
                  {loadingCountries
                    ? "Loading countries..."
                    : "Select country"}
                </option>

                {countries.map((country) => (
                  <option
                    key={country.countryId}
                    value={country.countryId}
                  >
                    {country.name}
                  </option>
                ))}
              </select>

              <ChevronDown
                size={17}
                className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-slate-400"
              />
            </div>

            {/* Region */}
            <div className="relative">
              <select
                value={selectedRegion}
                onChange={handleRegionChange}
                disabled={!selectedCountry}
                className="w-full appearance-none rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 pr-10 text-sm font-medium text-slate-700 outline-none transition focus:border-slate-900 focus:bg-white disabled:cursor-not-allowed disabled:opacity-50"
              >
                {REGIONS.map((region) => (
                  <option
                    key={region.value}
                    value={region.value}
                  >
                    {region.label}
                  </option>
                ))}
              </select>

              <ChevronDown
                size={17}
                className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-slate-400"
              />
            </div>

            {/* State */}
            <div className="relative">
              <select
                value={selectedState}
                onChange={handleStateChange}
                disabled={!selectedCountry || loadingStates}
                className="w-full appearance-none rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 pr-10 text-sm font-medium text-slate-700 outline-none transition focus:border-slate-900 focus:bg-white disabled:cursor-not-allowed disabled:opacity-50"
              >
                <option value="">
                  {loadingStates
                    ? "Loading states..."
                    : "Select state"}
                </option>

                {states.map((state) => (
                  <option
                    key={state.stateId}
                    value={state.stateId}
                  >
                    {state.name}
                  </option>
                ))}
              </select>

              <ChevronDown
                size={17}
                className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-slate-400"
              />
            </div>

            {/* Search */}
            <div className="relative">
              <Search
                size={18}
                className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"
              />

              <input
                value={search}
                onChange={(event) =>
                  setSearch(event.target.value)
                }
                disabled={!selectedState}
                placeholder="Search attractions..."
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-11 pr-10 text-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:bg-white disabled:cursor-not-allowed disabled:opacity-50"
              />

              {search && (
                <button
                  onClick={() => setSearch("")}
                  className="absolute right-3 top-1/2 -translate-y-1/2 rounded-lg p-1 text-slate-400 hover:bg-slate-200 hover:text-slate-900"
                >
                  <X size={16} />
                </button>
              )}
            </div>

          </div>

          {(selectedCountry ||
            selectedRegion ||
            selectedState ||
            search) && (
            <div className="mt-3 flex items-center justify-between">

              <div className="flex items-center gap-2 text-xs text-slate-500">
                <Filter size={14} />
                Filters active
              </div>

              <button
                onClick={clearFilters}
                className="text-xs font-semibold text-slate-700 hover:text-slate-950"
              >
                Clear all
              </button>

            </div>
          )}

        </div>
      </section>

      {/* Main */}
      <main className="mx-auto max-w-7xl px-5 py-10 lg:px-8">

        {error && (
          <div className="mb-8 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {error}
          </div>
        )}

        {loadingDestinations ? (
          <Loading />
        ) : !selectedState ? (
          <EmptyState
            title="Choose a destination"
            message="Select a country, region and state above to discover attractions."
          />
        ) : filteredDestinations.length === 0 ? (
          <EmptyState
            title="No destinations found"
            message={
              search
                ? `No attractions match "${search}". Try another search.`
                : "There are currently no destinations available for this state."
            }
          />
        ) : (
          <>
            <div className="mb-7 flex items-end justify-between">

              <div>
                <p className="text-sm font-medium text-slate-500">
                  Explore
                </p>

                <h2 className="mt-1 text-2xl font-bold text-slate-950">
                  Places worth visiting
                </h2>
              </div>

              <div className="text-sm text-slate-500">
                {filteredDestinations.length} result
                {filteredDestinations.length !== 1
                  ? "s"
                  : ""}
              </div>

            </div>

            <div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">

              {filteredDestinations.map((destination) => (
                <DestinationCard
                  key={destination.placeId}
                  destination={destination}
                />
              ))}

            </div>
          </>
        )}

      </main>

    </div>
  );
}
