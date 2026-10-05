import { ArrowRight, MapPin, Search } from "lucide-react";
import { Link } from "react-router-dom";

export default function Home() {
  return (
    <div>

      {/* Hero */}
      <section className="relative overflow-hidden bg-slate-950">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(59,130,246,0.25),transparent_35%),radial-gradient(circle_at_80%_30%,rgba(14,165,233,0.15),transparent_35%)]" />

        <div className="relative mx-auto max-w-7xl px-5 py-24 sm:py-32 lg:px-8">

          <div className="max-w-3xl">

            <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-4 py-2 text-sm text-slate-300 backdrop-blur">
              <MapPin size={15} />
              Discover places worth remembering
            </div>

            <h1 className="text-5xl font-bold tracking-tight text-white sm:text-6xl lg:text-7xl">
              Your journey.
              <br />
              <span className="text-slate-400">
                Your way.
              </span>
            </h1>

            <p className="mt-7 max-w-2xl text-lg leading-8 text-slate-300">
              Discover destinations, find unique stays, connect with verified
              local guides, and build your perfect travel experience.
            </p>

            <div className="mt-9 flex flex-wrap gap-4">

              <Link
                to="/destinations"
                className="inline-flex items-center gap-2 rounded-xl bg-white px-6 py-3.5 font-semibold text-slate-950 transition hover:bg-slate-200"
              >
                Explore destinations
                <ArrowRight size={18} />
              </Link>

              <Link
                to="/stays"
                className="inline-flex items-center gap-2 rounded-xl border border-white/15 bg-white/5 px-6 py-3.5 font-semibold text-white backdrop-blur transition hover:bg-white/10"
              >
                Find a stay
              </Link>

            </div>
          </div>
        </div>
      </section>

      {/* Search */}
      <section className="relative -mt-8 px-5">
        <div className="mx-auto max-w-5xl rounded-2xl border border-slate-200 bg-white p-3 shadow-xl">

          <div className="flex flex-col gap-3 md:flex-row">

            <div className="flex flex-1 items-center gap-3 rounded-xl bg-slate-50 px-4 py-3">
              <MapPin className="text-slate-400" size={20} />

              <div>
                <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Destination
                </div>

                <div className="text-sm font-medium text-slate-700">
                  Where do you want to go?
                </div>
              </div>
            </div>

            <button className="flex items-center justify-center gap-2 rounded-xl bg-slate-900 px-7 py-3 font-semibold text-white transition hover:bg-slate-700">
              <Search size={18} />
              Search
            </button>

          </div>
        </div>
      </section>

      {/* Intro */}
      <section className="mx-auto max-w-7xl px-5 py-20 lg:px-8">

        <div className="max-w-2xl">
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500">
            Travel Buddy
          </p>

          <h2 className="mt-3 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Everything you need for your next adventure.
          </h2>

          <p className="mt-4 leading-7 text-slate-600">
            Explore destinations, save places to your bucket list, discover
            accommodations and connect with local experiences.
          </p>
        </div>

      </section>

    </div>
  );
}