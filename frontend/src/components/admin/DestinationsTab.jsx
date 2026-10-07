import { useCallback, useEffect, useState } from "react";
import {
  Compass,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Star,
} from "lucide-react";

import adminService from "../../services/adminService";
import destinationService from "../../services/destinationService";

/*
 * DESTINATIONS ENGINE  (FR-32)
 *
 * The public catalogue only serves active places; this is the
 * one view that shows retired ones too, so a destination can be
 * taken back off the front page without deleting it.
 */

const EMPTY_FORM = {
  stateId: "",
  name: "",
  category: "",
  description: "",
  entryFee: "",
  currency: "INR",
  imageUrl: "",
  featured: false,
  active: true,
};

export default function DestinationsTab() {
  const [destinations, setDestinations] = useState([]);
  const [states, setStates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [places, stateList] = await Promise.all([
        adminService.getDestinations(),
        destinationService.getStates(1).catch(() => []),
      ]);
      setDestinations(Array.isArray(places) ? places : []);
      setStates(Array.isArray(stateList) ? stateList : []);
    } catch (loadError) {
      setError(
        loadError?.response?.data?.message ||
          "Could not load destinations."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function setField(key, value) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function startEdit(place) {
    setEditingId(place.placeId);
    setForm({
      stateId: place.stateId ?? "",
      name: place.name || "",
      category: place.category || "",
      description: place.description || "",
      entryFee: place.entryFee ?? "",
      currency: place.currency || "INR",
      imageUrl: place.imageUrl || "",
      featured: Boolean(place.featured),
      active: Boolean(place.active),
    });
    setNotice("");
    setError("");
  }

  function resetForm() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setNotice("");
    setError("");
  }

  async function submit(event) {
    event.preventDefault();

    if (!form.name.trim() || !form.stateId) {
      setError("A name and a state are required.");
      return;
    }

    const payload = {
      stateId: Number(form.stateId),
      name: form.name.trim(),
      category: form.category.trim() || null,
      description: form.description.trim() || null,
      entryFee: form.entryFee === "" ? null : Number(form.entryFee),
      currency: form.currency.trim() || null,
      imageUrl: form.imageUrl.trim() || null,
      featured: form.featured,
      active: form.active,
    };

    setBusy(true);
    setError("");
    setNotice("");
    try {
      if (editingId) {
        await adminService.updateDestination(editingId, payload);
        setNotice(`${payload.name} updated.`);
      } else {
        await adminService.createDestination(payload);
        setNotice(`${payload.name} created.`);
      }
      resetForm();
      await load();
    } catch (actionError) {
      setError(
        actionError?.response?.data?.message ||
          "Could not save this destination."
      );
    } finally {
      setBusy(false);
    }
  }

  async function toggle(place, key) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await adminService.updateDestination(place.placeId, {
        [key]: !place[key],
      });
      await load();
    } catch (actionError) {
      setError(
        actionError?.response?.data?.message ||
          "Could not update this destination."
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-slate-500">
          {destinations.length} destination
          {destinations.length === 1 ? "" : "s"} in the catalogue,
          retired listings included.
        </p>
        <button
          type="button"
          onClick={load}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-700 px-3 py-2 text-xs font-semibold text-slate-300 hover:bg-slate-800 disabled:opacity-50"
        >
          {loading ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : (
            <RefreshCw className="h-3.5 w-3.5" />
          )}
          Refresh
        </button>
      </div>

      {error && (
        <p className="mt-4 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-xs text-rose-300">
          {error}
        </p>
      )}
      {notice && (
        <p className="mt-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-300">
          {notice}
        </p>
      )}

      <div className="mt-4 grid gap-5 lg:grid-cols-[340px_minmax(0,1fr)]">
        <form
          onSubmit={submit}
          className="h-fit rounded-2xl border border-slate-800 bg-slate-950/60 p-5 backdrop-blur-xl"
        >
          <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-slate-400">
            {editingId ? (
              <Pencil className="h-4 w-4 text-amber-400" />
            ) : (
              <Plus className="h-4 w-4 text-emerald-400" />
            )}
            {editingId ? "Edit destination" : "New destination"}
          </h2>

          <label className="mt-4 block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
            Name *
            <input
              value={form.name}
              onChange={(event) => setField("name", event.target.value)}
              maxLength={120}
              className="mt-1.5 w-full rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 text-sm normal-case tracking-normal text-white outline-none focus:border-cyan-400"
              placeholder="Alleppey Backwaters"
            />
          </label>

          <label className="mt-3 block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
            State *
            <select
              value={form.stateId}
              onChange={(event) => setField("stateId", event.target.value)}
              className="mt-1.5 w-full rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 text-sm normal-case tracking-normal text-white outline-none focus:border-cyan-400"
            >
              <option value="">Choose a state…</option>
              {states.map((state) => (
                <option key={state.stateId} value={state.stateId}>
                  {state.name}
                </option>
              ))}
            </select>
          </label>

          <label className="mt-3 block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
            Category
            <input
              value={form.category}
              onChange={(event) =>
                setField("category", event.target.value)
              }
              maxLength={60}
              className="mt-1.5 w-full rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 text-sm normal-case tracking-normal text-white outline-none focus:border-cyan-400"
              placeholder="Nature, Heritage…"
            />
          </label>

          <div className="mt-3 grid grid-cols-2 gap-3">
            <label className="block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
              Entry fee
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.entryFee}
                onChange={(event) =>
                  setField("entryFee", event.target.value)
                }
                className="mt-1.5 w-full rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 text-sm normal-case tracking-normal text-white outline-none focus:border-cyan-400"
              />
            </label>
            <label className="block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
              Currency
              <input
                value={form.currency}
                onChange={(event) =>
                  setField("currency", event.target.value.toUpperCase())
                }
                maxLength={3}
                className="mt-1.5 w-full rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 text-sm normal-case tracking-normal text-white outline-none focus:border-cyan-400"
              />
            </label>
          </div>
          <label className="mt-3 block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
            Image URL
            <input
              value={form.imageUrl}
              onChange={(event) => setField("imageUrl", event.target.value)}
              maxLength={255}
              className="mt-1.5 w-full rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 text-sm normal-case tracking-normal text-white outline-none focus:border-cyan-400"
              placeholder="https://…"
            />
          </label>

          <label className="mt-3 block text-[11px] font-semibold uppercase tracking-wider text-slate-500">
            Description
            <textarea
              value={form.description}
              onChange={(event) =>
                setField("description", event.target.value)
              }
              rows={3}
              maxLength={1000}
              className="mt-1.5 w-full rounded-xl border border-slate-800 bg-slate-900 px-3 py-2 text-xs normal-case tracking-normal text-white outline-none focus:border-cyan-400"
            />
          </label>

          <div className="mt-4 flex flex-wrap gap-4">
            <label className="flex items-center gap-2 text-xs text-slate-300">
              <input
                type="checkbox"
                checked={form.featured}
                onChange={(event) =>
                  setField("featured", event.target.checked)
                }
                className="h-4 w-4 accent-amber-500"
              />
              Featured
            </label>
            <label className="flex items-center gap-2 text-xs text-slate-300">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(event) =>
                  setField("active", event.target.checked)
                }
                className="h-4 w-4 accent-emerald-500"
              />
              Active (public)
            </label>
          </div>

          <div className="mt-5 flex gap-3">
            <button
              type="submit"
              disabled={busy}
              className="inline-flex flex-1 items-center justify-center gap-2 rounded-xl bg-cyan-500 px-4 py-2.5 text-sm font-semibold text-slate-950 hover:bg-cyan-400 disabled:opacity-50"
            >
              {busy ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Compass className="h-4 w-4" />
              )}
              {editingId ? "Save changes" : "Create"}
            </button>
            {editingId && (
              <button
                type="button"
                onClick={resetForm}
                className="inline-flex items-center gap-2 rounded-xl border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-300 hover:bg-slate-800"
              >
                <RotateCcw className="h-4 w-4" />
                New
              </button>
            )}
          </div>
        </form>
        <div className="overflow-x-auto rounded-2xl border border-slate-800 bg-slate-950/60 backdrop-blur-xl">
          {loading ? (
            <div className="flex min-h-40 items-center justify-center">
              <Loader2 className="h-6 w-6 animate-spin text-cyan-400" />
            </div>
          ) : destinations.length === 0 ? (
            <p className="px-5 py-10 text-center text-sm text-slate-500">
              No destinations yet. Create the first one.
            </p>
          ) : (
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wider text-slate-500">
                  <th className="px-5 py-3 font-semibold">Destination</th>
                  <th className="px-3 py-3 font-semibold">Category</th>
                  <th className="px-3 py-3 font-semibold">Entry fee</th>
                  <th className="px-3 py-3 font-semibold">Flags</th>
                  <th className="px-3 py-3 text-right font-semibold">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {destinations.map((place) => (
                  <tr
                    key={place.placeId}
                    className={`border-b border-slate-800/60 hover:bg-slate-900/40 ${
                      place.active ? "" : "opacity-60"
                    }`}
                  >
                    <td className="px-5 py-3">
                      <div className="font-semibold text-white">
                        {place.name}
                      </div>
                      <div className="text-[11px] text-slate-500">
                        {[place.cityName, place.stateName]
                          .filter(Boolean)
                          .join(", ") || "Unassigned state"}
                      </div>
                    </td>
                    <td className="px-3 py-3 text-slate-400">
                      {place.category || "—"}
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-slate-300">
                      {place.entryFee == null
                        ? "—"
                        : `${place.currency || "INR"} ${Number(
                            place.entryFee
                          ).toFixed(2)}`}
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex flex-wrap gap-1.5">
                        {place.featured && (
                          <span className="inline-flex items-center gap-1 rounded-full border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-amber-300">
                            <Star className="h-3 w-3" />
                            Featured
                          </span>
                        )}
                        <span
                          className={`inline-flex rounded-full border px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider ${
                            place.active
                              ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                              : "border-rose-500/30 bg-rose-500/10 text-rose-300"
                          }`}
                        >
                          {place.active ? "Active" : "Retired"}
                        </span>
                      </div>
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-right">
                      <div className="inline-flex gap-2">
                        <button
                          type="button"
                          onClick={() => toggle(place, "featured")}
                          disabled={busy}
                          title={
                            place.featured
                              ? "Remove from featured"
                              : "Mark as featured"
                          }
                          className="rounded-lg border border-slate-700 p-1.5 text-amber-400 hover:bg-amber-500/10 disabled:opacity-50"
                        >
                          <Star
                            className={`h-3.5 w-3.5 ${
                              place.featured ? "fill-current" : ""
                            }`}
                          />
                        </button>
                        <button
                          type="button"
                          onClick={() => toggle(place, "active")}
                          disabled={busy}
                          className="rounded-lg border border-slate-700 px-2.5 py-1.5 text-[11px] font-semibold text-slate-300 hover:bg-slate-800 disabled:opacity-50"
                        >
                          {place.active ? "Retire" : "Restore"}
                        </button>
                        <button
                          type="button"
                          onClick={() => startEdit(place)}
                          disabled={busy}
                          className="inline-flex items-center gap-1.5 rounded-lg border border-cyan-500/30 px-2.5 py-1.5 text-[11px] font-semibold text-cyan-300 hover:bg-cyan-500/10 disabled:opacity-50"
                        >
                          <Pencil className="h-3.5 w-3.5" />
                          Edit
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </section>
  );
}