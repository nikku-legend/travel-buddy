import { useEffect, useState } from "react";

import {
  ArrowLeft,
  ArrowRight,
  Hotel,
  MapPin,
  Navigation,
  Ticket,
  UsersRound,
} from "lucide-react";

import {
  Link,
  useNavigate,
  useParams,
} from "react-router-dom";

import {
  MapContainer,
  Marker,
  Popup,
  TileLayer,
} from "react-leaflet";

import "leaflet/dist/leaflet.css";

import destinationService from "../services/destinationService";
import Loading from "../components/Loading";
import BucketListButton from "../components/destination/BucketListButton";

export default function DestinationDetails() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [destination, setDestination] =
    useState(null);

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState("");

  useEffect(() => {
    let isMounted = true;

    async function loadDestination() {
      try {
        setLoading(true);
        setError("");

        const found =
          await destinationService.getDestinationById(id);

        if (isMounted) {
          setDestination(found);
        }
      } catch (err) {
        console.error(err);

        if (isMounted) {
          setError(
            "Unable to load destination."
          );
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    loadDestination();

    return () => {
      isMounted = false;
    };
  }, [id]);

  if (loading) {
    return <Loading />;
  }

  if (error || !destination) {
    return (
      <div className="mx-auto max-w-3xl px-5 py-20 text-center">

        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-slate-100">
          <MapPin
            className="text-slate-400"
            size={28}
          />
        </div>

        <h1 className="mt-6 text-3xl font-bold text-slate-900">
          Destination unavailable
        </h1>

        <p className="mt-3 text-slate-500">
          {error ||
            "We couldn't find this destination."}
        </p>

        <button
          type="button"
          onClick={() =>
            navigate("/destinations")
          }
          className="mt-7 inline-flex items-center gap-2 rounded-xl bg-slate-950 px-5 py-3 text-sm font-semibold text-white"
        >
          <ArrowLeft size={17} />

          Back to destinations
        </button>

      </div>
    );
  }

  const latitude =
    Number(destination.latitude);

  const longitude =
    Number(destination.longitude);

  const hasCoordinates =
    Number.isFinite(latitude) &&
    Number.isFinite(longitude);

  return (
    <div className="min-h-screen bg-slate-50">

      {/* =====================================================
          BREADCRUMB
         ===================================================== */}
      <div className="mx-auto max-w-7xl px-5 pt-7 lg:px-8">

        <Link
          to="/destinations"
          className="inline-flex items-center gap-2 text-sm font-medium text-slate-500 transition hover:text-slate-950"
        >
          <ArrowLeft size={16} />

          Back to destinations
        </Link>

      </div>

      {/* =====================================================
          HERO
         ===================================================== */}
      <section className="mx-auto max-w-7xl px-5 pb-10 pt-6 lg:px-8">

        <div className="relative overflow-hidden rounded-[2rem] bg-slate-950">

          <div className="aspect-[16/8] min-h-[400px]">

            {destination.imageUrl ? (
              <img
                src={destination.imageUrl}
                alt={destination.name}
                className="h-full w-full object-cover opacity-80"
              />
            ) : (
              <div className="flex h-full items-center justify-center bg-slate-800 text-slate-400">
                No image available
              </div>
            )}

          </div>

          <div className="absolute inset-0 bg-gradient-to-t from-black via-black/40 to-transparent" />

          <div className="absolute inset-x-0 bottom-0 p-6 sm:p-10 lg:p-12">

            <div className="flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">

              {/* Destination title */}
              <div className="max-w-3xl">

                <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1.5 text-xs font-medium text-white backdrop-blur-xl">
                  <MapPin size={14} />

                  Travel Buddy Destination
                </div>

                <h1 className="text-4xl font-bold tracking-tight text-white sm:text-5xl lg:text-6xl">
                  {destination.name}
                </h1>

              </div>

              {/* =================================================
                  BUCKET LIST
                 ================================================= */}
              <BucketListButton
                placeId={destination.placeId}
                className="shrink-0 gap-2 rounded-xl border border-white/20 bg-white/10 px-5 py-3.5 text-sm font-semibold text-white backdrop-blur-xl hover:bg-white hover:text-slate-950"
              />

            </div>

          </div>

        </div>

      </section>

      {/* =====================================================
          MAIN CONTENT
         ===================================================== */}
      <main className="mx-auto max-w-7xl px-5 pb-20 lg:px-8">

        <div className="grid gap-8 lg:grid-cols-[1fr_380px]">

          {/* =================================================
              LEFT COLUMN
             ================================================= */}
          <div className="space-y-8">

            {/* About */}
            <section className="rounded-3xl border border-slate-200 bg-white p-6 sm:p-8">

              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                About this place
              </p>

              <h2 className="mt-2 text-2xl font-bold text-slate-950">
                Discover {destination.name}
              </h2>

              <p className="mt-5 text-base leading-8 text-slate-600">
                {destination.description ||
                  "Explore this destination and discover what makes it worth adding to your journey."}
              </p>

            </section>

            {/* Location */}
            <section className="overflow-hidden rounded-3xl border border-slate-200 bg-white">

              <div className="p-6 sm:p-8">

                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                  Location
                </p>

                <h2 className="mt-2 text-2xl font-bold text-slate-950">
                  Find your way
                </h2>

                <p className="mt-3 text-sm leading-6 text-slate-500">
                  Explore the destination location on the map.
                </p>

              </div>

              {hasCoordinates ? (
                <div className="h-[380px] w-full">

                  <MapContainer
                    center={[
                      latitude,
                      longitude,
                    ]}
                    zoom={13}
                    scrollWheelZoom={false}
                    className="h-full w-full"
                  >

                    <TileLayer
                      attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                      url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                    />

                    <Marker
                      position={[
                        latitude,
                        longitude,
                      ]}
                    >
                      <Popup>
                        <strong>
                          {destination.name}
                        </strong>
                      </Popup>
                    </Marker>

                  </MapContainer>

                </div>
              ) : (
                <div className="flex h-[280px] items-center justify-center border-t border-slate-100 text-sm text-slate-400">
                  Location coordinates are not available.
                </div>
              )}

            </section>

          </div>

          {/* =================================================
              RIGHT COLUMN
             ================================================= */}
          <aside className="space-y-6">

            {/* Quick information */}
            <section className="rounded-3xl border border-slate-200 bg-white p-6">

              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-400">
                Destination information
              </p>

              <div className="mt-6 space-y-5">

                {/* Entry fee */}
                <div className="flex items-start gap-4">

                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
                    <Ticket size={18} />
                  </div>

                  <div>
                    <p className="text-xs font-medium text-slate-400">
                      Entry fee
                    </p>

                    <p className="mt-1 text-sm font-semibold text-slate-900">
                      {destination.entryFee !== null &&
                      destination.entryFee !== undefined
                        ? `${destination.currency || "INR"} ${destination.entryFee}`
                        : "Information unavailable"}
                    </p>
                  </div>

                </div>

                {/* Location */}
                <div className="flex items-start gap-4">

                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
                    <Navigation size={18} />
                  </div>

                  <div>
                    <p className="text-xs font-medium text-slate-400">
                      Coordinates
                    </p>

                    <p className="mt-1 text-sm font-semibold text-slate-900">
                      {hasCoordinates
                        ? `${latitude.toFixed(
                            4
                          )}, ${longitude.toFixed(
                            4
                          )}`
                        : "Unavailable"}
                    </p>
                  </div>

                </div>

              </div>

            </section>

            {/* Stay CTA */}
            <section className="rounded-3xl bg-slate-950 p-6 text-white">

              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/10">
                <Hotel size={20} />
              </div>

              <h3 className="mt-5 text-xl font-bold">
                Need a place to stay?
              </h3>

              <p className="mt-2 text-sm leading-6 text-slate-300">
                Find available stays and accommodation
                options for your journey.
              </p>

              <Link
                to="/stays"
                className="mt-6 flex items-center justify-center gap-2 rounded-xl bg-white px-4 py-3 text-sm font-semibold text-slate-950 transition hover:bg-slate-100"
              >
                Explore stays

                <ArrowRight size={16} />
              </Link>

            </section>

            {/* Trip planning */}
            <section className="rounded-3xl border border-slate-200 bg-white p-6">

              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
                <UsersRound size={20} />
              </div>

              <h3 className="mt-5 text-lg font-bold text-slate-950">
                Build your journey
              </h3>

              <p className="mt-2 text-sm leading-6 text-slate-500">
                Save this attraction now and keep it
                ready for your future Travel Buddy trip.
              </p>

              <Link
                to="/bucket-list"
                className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-slate-900 hover:underline"
              >
                View bucket list

                <ArrowRight size={15} />
              </Link>

            </section>

          </aside>

        </div>

      </main>

    </div>
  );
}