import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowRight,
  BedDouble,
  Compass,
  MapPin,
  Search,
  Sparkles,
  Star,
} from "lucide-react";
import { Link, useNavigate } from "react-router-dom";

import BucketListButton from "../components/destination/BucketListButton";
import homeService from "../services/homeService";

function Home() {
  const navigate = useNavigate();
  const [discovery, setDiscovery] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");

  const loadDiscovery = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setDiscovery(await homeService.home());
    } catch (loadError) {
      setError(
        loadError?.response?.data?.message ||
          "We could not load travel inspiration. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDiscovery();
  }, [loadDiscovery]);

  const matches = useMemo(() => {
    const value = query.trim().toLowerCase();
    if (!value || !discovery) return [];
    const attractions = (discovery.popularAttractions ?? [])
      .filter((place) =>
        `${place.name} ${place.cityName ?? ""} ${place.stateName ?? ""} ${place.category ?? ""}`
          .toLowerCase()
          .includes(value)
      )
      .slice(0, 4)
      .map((place) => ({
        label: place.name,
        context: [place.cityName, place.stateName].filter(Boolean).join(", "),
        to: `/destinations/${place.placeId}`,
      }));
    const cities = (discovery.cities ?? [])
      .filter((city) =>
        `${city.name} ${city.stateName ?? ""}`.toLowerCase().includes(value)
      )
      .slice(0, 3)
      .map((city) => ({
        label: city.name,
        context: city.stateName,
        to: `/destinations?city=${encodeURIComponent(city.slug)}`,
      }));
    return [...attractions, ...cities].slice(0, 5);
  }, [discovery, query]);

  function submitSearch(event) {
    event.preventDefault();
    if (matches[0]) {
      navigate(matches[0].to);
    } else {
      navigate("/destinations");
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-7xl px-5 py-16" aria-busy="true">
        <div className="h-[28rem] animate-pulse rounded-[2rem] bg-slate-200" />
        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }, (_, index) => (
            <div key={index} className="h-64 animate-pulse rounded-3xl bg-slate-200" />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <section className="mx-auto max-w-3xl px-5 py-24 text-center">
        <Compass className="mx-auto h-10 w-10 text-amber-600" />
        <h1 className="mt-5 text-2xl font-bold text-slate-950">
          Discovery is taking a short break
        </h1>
        <p className="mt-2 text-slate-600">{error}</p>
        <button
          type="button"
          onClick={loadDiscovery}
          className="mt-6 rounded-xl bg-slate-950 px-5 py-3 font-semibold text-white"
        >
          Try again
        </button>
      </section>
    );
  }

  const hero = discovery?.hero;
  const attractions = discovery?.popularAttractions ?? [];
  const stays = discovery?.featuredStays ?? [];
  const experiences = discovery?.experiences ?? [];
  const zones = discovery?.zones ?? [];
  const cities = discovery?.cities ?? [];
  const inspiration = discovery?.inspiration?.collections ?? [];
  const planner = discovery?.plannerCta;
  const heroImage = discovery?.featuredAttractions?.[0]?.imageUrl;

  return (
    <div className="pb-16">
      <section className="relative isolate overflow-visible bg-slate-950">
        {heroImage && (
          <img
            src={heroImage}
            alt=""
            className="absolute inset-0 -z-20 h-full w-full object-cover opacity-35"
          />
        )}
        <div className="absolute inset-0 -z-10 bg-gradient-to-r from-slate-950 via-slate-950/90 to-slate-900/35" />
        <div className="mx-auto max-w-7xl px-5 pb-28 pt-20 sm:pb-36 sm:pt-28 lg:px-8">
          <div className="max-w-3xl">
            <p className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-4 py-2 text-sm text-amber-200">
              <Compass size={16} />
              Discover somewhere that feels like yours
            </p>
            <h1 className="mt-7 text-5xl font-bold tracking-tight text-white sm:text-7xl">
              {hero?.headline ?? "Find your next journey"}
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-8 text-slate-200">
              {hero?.subline ??
                "Explore destinations, stays, local guides and experiences in one place."}
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link
                to={planner?.route ?? "/trips/new"}
                className="inline-flex items-center gap-2 rounded-xl bg-amber-300 px-5 py-3 font-bold text-slate-950 transition hover:bg-amber-200"
              >
                {planner?.label ?? "Start Custom Trip"}
                <ArrowRight size={18} />
              </Link>
              <Link
                to="/destinations"
                className="rounded-xl border border-white/25 bg-white/10 px-5 py-3 font-semibold text-white transition hover:bg-white/15"
              >
                Explore destinations
              </Link>
            </div>
          </div>
        </div>
      </section>

      <section className="relative z-10 -mt-10 px-5">
        <form
          onSubmit={submitSearch}
          className="mx-auto flex max-w-5xl flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-3 shadow-xl sm:flex-row"
        >
          <label className="flex min-w-0 flex-1 items-center gap-3 rounded-xl bg-slate-50 px-4 py-3">
            <MapPin className="shrink-0 text-slate-400" size={20} />
            <span className="min-w-0 flex-1">
              <span className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
                Explore
              </span>
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={hero?.searchPlaceholder ?? "Search destinations, places or stays"}
                className="mt-1 w-full bg-transparent text-sm font-medium text-slate-800 outline-none placeholder:text-slate-500"
                aria-label="Search destinations and attractions"
              />
            </span>
          </label>
          <button
            type="submit"
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-slate-950 px-7 py-3 font-semibold text-white transition hover:bg-slate-800"
          >
            <Search size={18} />
            Search
          </button>
        </form>
        {matches.length > 0 && (
          <ul className="mx-auto mt-2 max-w-5xl divide-y divide-slate-100 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-lg">
            {matches.map((match) => (
              <li key={match.to}>
                <Link
                  to={match.to}
                  className="flex items-center justify-between gap-4 px-5 py-3 hover:bg-slate-50"
                >
                  <span className="font-semibold text-slate-900">{match.label}</span>
                  <span className="text-sm text-slate-500">{match.context}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <main className="mx-auto max-w-7xl space-y-20 px-5 pt-20 lg:px-8">
        <section>
          <SectionHeading
            eyebrow="Start with a place"
            title="Featured destinations"
            action="/destinations"
            actionLabel="All destinations"
          />
          {cities.length ? (
            <div className="mt-7 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
              {cities.slice(0, 4).map((city, index) => {
                const image = attractions[index]?.imageUrl;
                return (
                  <Link
                    key={city.cityId}
                    to="/destinations"
                    className="group relative flex min-h-72 items-end overflow-hidden rounded-3xl bg-slate-800 p-5 text-white"
                  >
                    {image && (
                      <img
                        src={image}
                        alt=""
                        className="absolute inset-0 h-full w-full object-cover transition duration-700 group-hover:scale-105"
                      />
                    )}
                    <div className="absolute inset-0 bg-gradient-to-t from-slate-950/90 via-slate-950/15 to-transparent" />
                    <div className="relative">
                      <p className="flex items-center gap-1.5 text-sm text-slate-200">
                        <MapPin size={14} /> {city.stateName}
                      </p>
                      <h3 className="mt-2 text-2xl font-bold">{city.name}</h3>
                      <p className="mt-1 text-sm text-slate-200">
                        {city.attractionCount} places to discover
                      </p>
                    </div>
                  </Link>
                );
              })}
            </div>
          ) : <EmptyMessage text="Destination recommendations will appear here as the catalogue grows." />}
        </section>

        <section>
          <SectionHeading
            eyebrow="Worth the detour"
            title="Popular attractions"
            action="/destinations"
            actionLabel="Explore places"
          />
          {attractions.length ? (
            <div className="mt-7 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
              {attractions.slice(0, 8).map((place) => (
                <article key={place.placeId} className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
                  <Link to={`/destinations/${place.placeId}`} className="relative block aspect-[4/3] bg-slate-100">
                    {place.imageUrl ? (
                      <img src={place.imageUrl} alt={place.name} className="h-full w-full object-cover transition duration-500 hover:scale-105" />
                    ) : (
                      <div className="flex h-full items-center justify-center text-sm text-slate-400">Photo coming soon</div>
                    )}
                    <span className="absolute bottom-3 left-3 rounded-full bg-slate-950/75 px-3 py-1 text-xs font-semibold text-white">
                      {place.category || "Attraction"}
                    </span>
                  </Link>
                  <div className="p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <h3 className="font-bold text-slate-950">{place.name}</h3>
                        <p className="mt-1 text-sm text-slate-500">
                          {[place.cityName, place.stateName].filter(Boolean).join(", ") || "Explore India"}
                        </p>
                      </div>
                      <BucketListButton
                        placeId={place.placeId}
                        className="h-9 w-9 shrink-0 rounded-full border border-slate-200 text-slate-600 hover:bg-amber-50 hover:text-amber-700"
                      />
                    </div>
                    <p className="mt-3 line-clamp-2 text-sm leading-6 text-slate-600">
                      {place.description || "Add this place to your journey and explore what makes it special."}
                    </p>
                    <div className="mt-4 flex items-center justify-between gap-2">
                      <span className="text-xs text-slate-500">
                        {place.visitDuration || "Plan your visit"}
                      </span>
                      <Link to={`/destinations/${place.placeId}`} className="text-sm font-semibold text-amber-700 hover:text-amber-900">
                        Explore <ArrowRight className="ml-1 inline" size={14} />
                      </Link>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          ) : <EmptyMessage text="Popular places will appear here as destinations are added." />}
        </section>

        <section className="rounded-[2rem] bg-slate-950 px-6 py-8 text-white sm:px-9">
          <SectionHeading eyebrow="Explore a little wider" title="Travel by region" inverse />
          {zones.length ? (
            <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {zones.map((zone) => (
                <Link
                  key={zone.zone}
                  to="/destinations"
                  className="flex items-center justify-between rounded-2xl border border-white/10 bg-white/5 p-5 transition hover:border-amber-300/60 hover:bg-white/10"
                >
                  <span>
                    <span className="block font-bold">{zone.label}</span>
                    <span className="mt-1 block text-sm text-slate-400">
                      {zone.cityCount} cities · {zone.attractionCount} attractions
                    </span>
                  </span>
                  <ArrowRight className="text-amber-300" size={18} />
                </Link>
              ))}
            </div>
          ) : <EmptyMessage text="Regional collections are coming soon." inverse />}
        </section>

        <section>
          <SectionHeading
            eyebrow="Stay somewhere memorable"
            title="Places to stay"
            action="/stays"
            actionLabel="Browse all stays"
          />
          {stays.length ? (
            <div className="mt-7 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
              {stays.slice(0, 4).map((stay) => (
                <Link
                  key={stay.propertyId}
                  to={`/stays/${stay.propertyId}`}
                  className="overflow-hidden rounded-3xl border border-slate-200 bg-white transition hover:-translate-y-1 hover:shadow-xl"
                >
                  <div className="relative aspect-[4/3] bg-slate-100">
                    {stay.imageUrl ? (
                      <img src={stay.imageUrl} alt={stay.name} className="h-full w-full object-cover" />
                    ) : (
                      <div className="flex h-full items-center justify-center"><BedDouble className="text-slate-300" /></div>
                    )}
                  </div>
                  <div className="p-5">
                    <p className="flex items-center gap-1 text-sm text-slate-500">
                      <MapPin size={14} /> {stay.cityName || "Travel Buddy stay"}
                    </p>
                    <h3 className="mt-2 text-lg font-bold text-slate-950">{stay.name}</h3>
                    {stay.rating != null ? (
                      <p className="mt-2 flex items-center gap-1 text-sm text-slate-600">
                        <Star size={15} className="fill-amber-400 text-amber-500" />
                        {Number(stay.rating).toFixed(1)}
                        <span className="text-slate-400">({stay.reviewCount} reviews)</span>
                      </p>
                    ) : (
                      <p className="mt-2 text-sm text-slate-500">New verified stay</p>
                    )}
                  </div>
                </Link>
              ))}
            </div>
          ) : <EmptyMessage text="Verified stays will appear here as partners publish their listings." />}
        </section>

        <section>
          <SectionHeading
            eyebrow="Know the place with a local"
            title="Local experiences"
            action="/guides"
            actionLabel="Meet local guides"
          />
          {experiences.length ? (
            <div className="mt-7 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
              {experiences.slice(0, 4).map((experience) => (
                <Link key={experience.guideId} to="/guides" className="rounded-3xl border border-slate-200 bg-white p-5 transition hover:shadow-lg">
                  <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-amber-50 text-amber-700">
                    <Sparkles size={21} />
                  </div>
                  <h3 className="mt-4 text-lg font-bold text-slate-950">{experience.name}</h3>
                  <p className="mt-1 text-sm text-slate-500">{experience.stateName}</p>
                  <p className="mt-3 line-clamp-3 text-sm leading-6 text-slate-600">{experience.bio || "Explore the area with a verified local guide."}</p>
                  <p className="mt-4 text-sm font-semibold text-slate-800">
                    {experience.dailyRate != null
                      ? `${experience.currency ?? "INR"} ${Number(experience.dailyRate).toLocaleString("en-IN")} / day`
                      : "Ask about availability"}
                  </p>
                </Link>
              ))}
            </div>
          ) : <EmptyMessage text="Local guide experiences will appear as verified partners join." />}
        </section>

        {inspiration.length > 0 && (
          <section>
            <SectionHeading eyebrow="Ideas for your next story" title={discovery?.inspiration?.title ?? "Travel inspiration"} />
            <div className="mt-7 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {inspiration.slice(0, 6).map((collection) => (
                <Link key={`${collection.title}-${collection.cityName}`} to="/destinations" className="group relative flex min-h-52 items-end overflow-hidden rounded-3xl bg-slate-200 p-5">
                  {collection.imageUrl && <img src={collection.imageUrl} alt="" className="absolute inset-0 h-full w-full object-cover transition duration-500 group-hover:scale-105" />}
                  <div className="absolute inset-0 bg-gradient-to-t from-slate-950/85 to-transparent" />
                  <div className="relative text-white">
                    <h3 className="text-xl font-bold">{collection.title}</h3>
                    <p className="mt-1 text-sm text-slate-200">{collection.subtitle}</p>
                  </div>
                </Link>
              ))}
            </div>
          </section>
        )}

        <section className="flex flex-col items-start justify-between gap-6 rounded-[2rem] bg-amber-100 px-7 py-8 sm:flex-row sm:items-center sm:px-10">
          <div>
            <p className="text-sm font-bold uppercase tracking-widest text-amber-800">Your trip, your choices</p>
            <h2 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">Ready to bring it all together?</h2>
            <p className="mt-2 max-w-2xl text-slate-700">
              {planner?.description ?? "Choose your dates and destinations. We will help you shape a journey."}
            </p>
          </div>
          <Link to={planner?.route ?? "/trips/new"} className="inline-flex shrink-0 items-center gap-2 rounded-xl bg-slate-950 px-5 py-3 font-bold text-white hover:bg-slate-800">
            {planner?.label ?? "Start Custom Trip"} <ArrowRight size={17} />
          </Link>
        </section>
      </main>
    </div>
  );
}

function SectionHeading({ eyebrow, title, action, actionLabel, inverse = false }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className={`text-xs font-bold uppercase tracking-[0.2em] ${inverse ? "text-amber-300" : "text-amber-700"}`}>{eyebrow}</p>
        <h2 className={`mt-2 text-3xl font-bold tracking-tight sm:text-4xl ${inverse ? "text-white" : "text-slate-950"}`}>{title}</h2>
      </div>
      {action && (
        <Link to={action} className={`text-sm font-semibold ${inverse ? "text-amber-300" : "text-slate-700"} hover:underline`}>
          {actionLabel} <ArrowRight className="ml-1 inline" size={15} />
        </Link>
      )}
    </div>
  );
}

function EmptyMessage({ text, inverse = false }) {
  return <p className={`mt-6 rounded-2xl border border-dashed p-6 text-sm ${inverse ? "border-white/20 text-slate-300" : "border-slate-300 text-slate-500"}`}>{text}</p>;
}

export default Home;
