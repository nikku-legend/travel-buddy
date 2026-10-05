import { ArrowUpRight, MapPin } from "lucide-react";
import { Link } from "react-router-dom";

import BucketListButton from "./destination/BucketListButton";

export default function DestinationCard({
  destination,
}) {
  return (
    <article className="group overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm transition-all duration-300 hover:-translate-y-1 hover:shadow-2xl">

      {/* =====================================================
          IMAGE
         ===================================================== */}
      <div className="relative aspect-[16/10] overflow-hidden bg-slate-100">

        {destination.imageUrl ? (
          <img
            src={destination.imageUrl}
            alt={destination.name}
            className="h-full w-full object-cover transition duration-700 group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-sm text-slate-400">
            No image available
          </div>
        )}

        {/* Image gradient */}
        <div className="absolute inset-x-0 bottom-0 h-28 bg-gradient-to-t from-black/70 via-black/20 to-transparent" />

        {/* =================================================
            LOCATION
           ================================================= */}
        <div className="absolute bottom-4 left-4 flex items-center gap-1.5 text-sm font-medium text-white">
          <MapPin size={15} />

          {destination.stateName || "Explore"}
        </div>

        {/* =================================================
            BUCKET LIST
           ================================================= */}
        <BucketListButton
          placeId={destination.placeId}
          className="absolute right-4 top-4 h-10 w-10 rounded-full border border-white/30 bg-black/20 text-white shadow-lg backdrop-blur-xl hover:bg-white hover:text-slate-900"
        />

      </div>

      {/* =====================================================
          CONTENT
         ===================================================== */}
      <div className="p-5">

        <div className="flex items-start justify-between gap-4">

          <h3 className="text-xl font-bold tracking-tight text-slate-900">
            {destination.name}
          </h3>

          <ArrowUpRight
            size={19}
            className="mt-1 shrink-0 text-slate-400 transition group-hover:text-slate-900"
          />

        </div>

        <p className="mt-3 line-clamp-3 text-sm leading-6 text-slate-600">
          {destination.description ||
            "Discover this beautiful destination and create unforgettable memories."}
        </p>

        {/* =================================================
            DESTINATION DETAILS
           ================================================= */}
        <Link
          to={`/destinations/${destination.placeId}`}
          className="mt-5 flex w-full items-center justify-center rounded-xl border border-slate-200 px-4 py-3 text-sm font-semibold text-slate-800 transition hover:border-slate-900 hover:bg-slate-900 hover:text-white"
        >
          Explore destination
        </Link>

      </div>

    </article>
  );
}