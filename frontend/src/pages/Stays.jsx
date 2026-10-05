import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  Building2,
  ChevronDown,
  MapPin,
  Search,
  SlidersHorizontal,
  X,
} from "lucide-react";

import propertyService from "../services/propertyService";
import Loading from "../components/Loading";
import EmptyState from "../components/EmptyState";

const PROPERTY_TYPES = [
  {
    value: "",
    label: "All property types",
  },
  {
    value: "HOTEL",
    label: "Hotels",
  },
  {
    value: "RESORT",
    label: "Resorts",
  },
  {
    value: "VILLA",
    label: "Villas",
  },
  {
    value: "HOMESTAY",
    label: "Homestays",
  },
];

export default function Stays() {

  const [properties, setProperties] = useState([]);

  const [stateId, setStateId] = useState("");
  const [propertyType, setPropertyType] = useState("");

  const [search, setSearch] = useState("");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    loadProperties();
  }, []);

  async function loadProperties(
    selectedStateId = "",
    selectedPropertyType = ""
  ) {

    try {

      setLoading(true);
      setError("");

      const data =
        await propertyService.getProperties(
          selectedStateId || null,
          selectedPropertyType || null
        );

      setProperties(data);

    } catch (err) {

      console.error(err);

      setError(
        "Unable to load stays right now."
      );

    } finally {

      setLoading(false);
    }
  }

  function handleTypeChange(event) {

    const value = event.target.value;

    setPropertyType(value);

    loadProperties(
      stateId,
      value
    );
  }

  function clearFilters() {

    setStateId("");
    setPropertyType("");
    setSearch("");

    loadProperties("", "");
  }

  const filteredProperties = useMemo(() => {

    const query =
      search.trim().toLowerCase();

    if (!query) {
      return properties;
    }

    return properties.filter(
      (property) =>
        `${property.name} ${property.address} ${property.stateName || ""} ${property.propertyType || ""}`
          .toLowerCase()
          .includes(query)
    );

  }, [properties, search]);

  return (
    <div className="min-h-screen bg-slate-50">

      {/* Header */}
      <section className="border-b border-slate-200 bg-white">

        <div className="mx-auto max-w-7xl px-5 py-12 lg:px-8">

          <div className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">

            <div className="max-w-2xl">

              <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-xs font-semibold uppercase tracking-wider text-slate-600">

                <Building2 size={14} />

                Stays

              </div>

              <h1 className="text-4xl font-bold tracking-tight text-slate-950 sm:text-5xl">

                Find a place
                <span className="text-slate-400">
                  {" "}to stay.
                </span>

              </h1>

              <p className="mt-4 max-w-xl text-base leading-7 text-slate-600">

                Discover verified hotels, resorts, villas and
                homestays for your journey.

              </p>

            </div>

            <div className="hidden rounded-2xl bg-slate-100 px-5 py-4 sm:block">

              <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Available stays
              </div>

              <div className="mt-1 text-2xl font-bold text-slate-900">
                {filteredProperties.length}
              </div>

            </div>

          </div>

        </div>

      </section>

      {/* Filters */}
      <section className="sticky top-[72px] z-30 border-b border-slate-200 bg-white/95 backdrop-blur-xl">

        <div className="mx-auto max-w-7xl px-5 py-4 lg:px-8">

          <div className="mb-3 flex items-center gap-2 lg:hidden">

            <SlidersHorizontal size={17} />

            <span className="text-sm font-semibold">
              Stay filters
            </span>

          </div>

          <div className="grid gap-3 md:grid-cols-2">

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
                placeholder="Search hotels, resorts, villas..."
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-11 pr-10 text-sm outline-none transition placeholder:text-slate-400 focus:border-slate-900 focus:bg-white"
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

            {/* Property type */}
            <div className="relative">

              <select
                value={propertyType}
                onChange={handleTypeChange}
                className="w-full appearance-none rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 pr-10 text-sm font-medium text-slate-700 outline-none transition focus:border-slate-900 focus:bg-white"
              >

                {PROPERTY_TYPES.map((type) => (
                  <option
                    key={type.value}
                    value={type.value}
                  >
                    {type.label}
                  </option>
                ))}

              </select>

              <ChevronDown
                size={17}
                className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-slate-400"
              />

            </div>

          </div>

          {(search || propertyType) && (

            <div className="mt-3 flex items-center justify-between">

              <div className="text-xs text-slate-500">
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

      {/* Results */}
      <main className="mx-auto max-w-7xl px-5 py-10 lg:px-8">

        {error && (
          <div className="mb-8 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {error}
          </div>
        )}

        {loading ? (
          <Loading />
        ) : filteredProperties.length === 0 ? (

          <EmptyState
            title="No stays found"
            message="There are currently no verified stays matching your search."
          />

        ) : (

          <>

            <div className="mb-7">

              <p className="text-sm font-medium text-slate-500">
                Places to stay
              </p>

              <h2 className="mt-1 text-2xl font-bold text-slate-950">
                Verified accommodations
              </h2>

            </div>

            <div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">

              {filteredProperties.map(
                (property) => (
                  <PropertyCard
                    key={property.propertyId}
                    property={property}
                  />
                )
              )}

            </div>

          </>

        )}

      </main>

    </div>
  );
}

function PropertyCard({ property }) {

  return (
    <article className="group overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm transition duration-300 hover:-translate-y-1 hover:shadow-xl">

      {/* Image placeholder */}
      <div className="relative flex aspect-[16/10] items-center justify-center overflow-hidden bg-gradient-to-br from-slate-800 via-slate-700 to-slate-950">

        <Building2
          size={48}
          strokeWidth={1.2}
          className="text-white/20"
        />

        <div className="absolute left-4 top-4 rounded-full border border-white/10 bg-black/20 px-3 py-1.5 text-xs font-semibold text-white backdrop-blur-xl">
          {property.propertyType}
        </div>

        {property.verified && (
          <div className="absolute right-4 top-4 rounded-full bg-white px-3 py-1.5 text-xs font-semibold text-slate-900">
            Verified
          </div>
        )}

      </div>

      {/* Content */}
      <div className="p-5">

        <h3 className="text-xl font-bold tracking-tight text-slate-950">
          {property.name}
        </h3>

        <div className="mt-2 flex items-start gap-2 text-sm text-slate-500">

          <MapPin
            size={16}
            className="mt-0.5 shrink-0"
          />

          <span>
            {property.address}
            {property.stateName
              ? `, ${property.stateName}`
              : ""}
          </span>

        </div>

        {property.description && (
          <p className="mt-3 line-clamp-2 text-sm leading-6 text-slate-600">
            {property.description}
          </p>
        )}

        <Link
          to={`/stays/${property.propertyId}`}
          className="mt-5 flex w-full items-center justify-center rounded-xl bg-slate-950 px-4 py-3 text-sm font-semibold text-white transition hover:bg-slate-700"
        >
          View property
        </Link>

      </div>

    </article>
  );
}
