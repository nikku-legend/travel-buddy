import { useCallback, useEffect, useState } from "react";
import {
  ArrowRight,
  Bookmark,
  Heart,
  Loader2,
  MapPin,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { Link, useNavigate } from "react-router-dom";

import bucketListService from "../services/bucketListService";

function BucketList() {
  const navigate = useNavigate();

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [removingId, setRemovingId] = useState(null);

  const loadBucketList = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const data =
        await bucketListService.getMyBucketList();

      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error(
        "Failed to load bucket list:",
        err
      );

      const status =
        err?.response?.status;

      if (status === 401 || status === 403) {
        setError(
          "Please log in to view your bucket list."
        );
      } else {
        setError(
          err?.response?.data?.message ||
            "Unable to load your bucket list."
        );
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadBucketList();
  }, [loadBucketList]);

  const handleRemove = async (bucketId) => {
    if (!bucketId) {
      return;
    }

    setRemovingId(bucketId);
    setError("");

    try {
      await bucketListService.removeFromBucketList(
        bucketId
      );

      setItems((currentItems) =>
        currentItems.filter(
          (item) =>
            item.bucketId !== bucketId
        )
      );
    } catch (err) {
      console.error(
        "Failed to remove bucket list item:",
        err
      );

      setError(
        err?.response?.data?.message ||
          "Unable to remove this place from your bucket list."
      );
    } finally {
      setRemovingId(null);
    }
  };

  const handleLogin = () => {
    navigate("/login", {
      state: {
        from: "/bucket-list",
      },
    });
  };

  return (
    <main className="min-h-screen bg-slate-50">

      {/* HERO */}
      <section className="relative overflow-hidden border-b border-slate-200 bg-white">

        <div className="absolute -left-24 -top-24 h-72 w-72 rounded-full bg-rose-100/60 blur-3xl" />

        <div className="absolute -bottom-32 -right-20 h-80 w-80 rounded-full bg-amber-100/50 blur-3xl" />

        <div className="relative mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8 lg:py-16">

          <div className="max-w-3xl">

            <div className="inline-flex items-center gap-2 rounded-full border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-bold uppercase tracking-[0.18em] text-rose-700">
              <Heart
                size={13}
                fill="currentColor"
              />
              Your Travel Collection
            </div>

            <h1 className="mt-5 text-4xl font-black tracking-tight text-slate-950 sm:text-5xl">
              My Bucket List
            </h1>

            <p className="mt-4 max-w-2xl text-sm leading-7 text-slate-600 sm:text-base">
              Keep the places you want to experience
              close. Save your favorite destinations
              today and turn them into future adventures.
            </p>

          </div>

          {!loading && !error && (
            <div className="mt-8 flex flex-wrap gap-3">

              <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3 shadow-sm">

                <p className="text-xs font-semibold text-slate-400">
                  Saved places
                </p>

                <p className="mt-1 text-2xl font-black text-slate-950">
                  {items.length}
                </p>

              </div>

              <Link
                to="/destinations"
                className="inline-flex items-center gap-2 rounded-2xl bg-slate-950 px-5 py-3 text-sm font-bold text-white shadow-sm transition hover:-translate-y-0.5 hover:bg-slate-800"
              >
                Explore destinations
                <ArrowRight size={16} />
              </Link>

            </div>
          )}

        </div>
      </section>

      {/* CONTENT */}
      <section className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 lg:py-10">

        {/* ERROR */}
        {error && (
          <div className="rounded-3xl border border-rose-200 bg-rose-50 p-5">

            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">

              <div>

                <p className="text-sm font-bold text-rose-900">
                  Something went wrong
                </p>

                <p className="mt-1 text-sm leading-6 text-rose-700">
                  {error}
                </p>

              </div>

              <div className="flex flex-wrap gap-2">

                {(error.includes("log in") ||
                  error.includes("login")) && (
                  <button
                    type="button"
                    onClick={handleLogin}
                    className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2.5 text-xs font-bold text-white transition hover:bg-rose-700"
                  >
                    Log in
                    <ArrowRight size={14} />
                  </button>
                )}

                <button
                  type="button"
                  onClick={loadBucketList}
                  className="inline-flex items-center gap-2 rounded-xl border border-rose-200 bg-white px-4 py-2.5 text-xs font-bold text-rose-700 transition hover:bg-rose-100"
                >
                  <RefreshCw size={14} />
                  Try again
                </button>

              </div>

            </div>

          </div>
        )}

        {/* LOADING */}
        {loading && (
          <div className="flex min-h-[360px] items-center justify-center">

            <div className="text-center">

              <Loader2
                size={32}
                className="mx-auto animate-spin text-slate-700"
              />

              <p className="mt-4 text-sm font-semibold text-slate-600">
                Loading your bucket list...
              </p>

            </div>

          </div>
        )}

        {/* EMPTY */}
        {!loading && !error && items.length === 0 && (
          <div className="rounded-[2rem] border border-dashed border-slate-300 bg-white px-6 py-16 text-center shadow-sm sm:px-10">

            <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-rose-50 text-rose-600">

              <Bookmark
                size={34}
                fill="currentColor"
              />

            </div>

            <h2 className="mt-6 text-2xl font-black text-slate-950">
              Your bucket list is waiting
            </h2>

            <p className="mx-auto mt-3 max-w-xl text-sm leading-7 text-slate-500">
              You haven't saved any tourist places yet.
              Explore Travel Buddy and save the places
              you want to visit.
            </p>

            <Link
              to="/destinations"
              className="mt-7 inline-flex items-center gap-2 rounded-2xl bg-slate-950 px-6 py-3 text-sm font-bold text-white transition hover:-translate-y-0.5 hover:bg-slate-800"
            >
              Discover places
              <ArrowRight size={16} />
            </Link>

          </div>
        )}

        {/* LIST */}
        {!loading && !error && items.length > 0 && (
          <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">

            {items.map((item) => (
              <BucketListCard
                key={item.bucketId}
                item={item}
                removing={
                  removingId === item.bucketId
                }
                onRemove={handleRemove}
              />
            ))}

          </div>
        )}

      </section>

    </main>
  );
}

function BucketListCard({
  item,
  removing,
  onRemove,
}) {
  const imageUrl =
    item.imageUrl ||
    "https://images.unsplash.com/photo-1524492412937-b28074a5d7da?auto=format&fit=crop&w=1200&q=85";

  return (
    <article className="group overflow-hidden rounded-[1.75rem] border border-slate-200 bg-white shadow-sm transition duration-300 hover:-translate-y-1 hover:shadow-xl">

      {/* IMAGE */}
      <div className="relative aspect-[16/10] overflow-hidden bg-slate-100">

        <img
          src={imageUrl}
          alt={item.placeName || "Saved destination"}
          className="h-full w-full object-cover transition duration-700 group-hover:scale-105"
          loading="lazy"
          onError={(event) => {
            event.currentTarget.src =
              "https://images.unsplash.com/photo-1524492412937-b28074a5d7da?auto=format&fit=crop&w=1200&q=85";
          }}
        />

        <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/5 to-transparent" />

        <div className="absolute left-4 top-4 inline-flex items-center gap-1.5 rounded-full border border-white/20 bg-black/35 px-3 py-1.5 text-xs font-bold text-white backdrop-blur-md">
          <Heart
            size={13}
            fill="currentColor"
          />
          Saved
        </div>

        <button
          type="button"
          onClick={() =>
            onRemove(item.bucketId)
          }
          disabled={removing}
          aria-label={`Remove ${item.placeName || "place"} from bucket list`}
          className="absolute right-4 top-4 flex h-10 w-10 items-center justify-center rounded-full border border-white/20 bg-black/35 text-white backdrop-blur-md transition hover:bg-rose-600 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {removing ? (
            <Loader2
              size={17}
              className="animate-spin"
            />
          ) : (
            <Trash2 size={17} />
          )}
        </button>

      </div>

      {/* CONTENT */}
      <div className="p-5">

        <h2 className="line-clamp-2 text-xl font-black text-slate-950">
          {item.placeName || "Untitled destination"}
        </h2>

        <div className="mt-2 flex items-center gap-2 text-xs font-semibold text-slate-400">

          <MapPin size={14} />

          <span>
            Tourist destination
          </span>

        </div>

        {item.description && (
          <p className="mt-4 line-clamp-3 text-sm leading-6 text-slate-500">
            {item.description}
          </p>
        )}

        <div className="mt-5 flex items-center justify-between gap-3 border-t border-slate-100 pt-4">

          <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">
            Saved place
          </span>

          <Link
            to={`/destinations/${item.placeId}`}
            className="inline-flex items-center gap-1.5 rounded-xl bg-slate-950 px-3.5 py-2 text-xs font-bold text-white transition hover:bg-slate-800"
          >
            View
            <ArrowRight size={13} />
          </Link>

        </div>

      </div>

    </article>
  );
}

export default BucketList;