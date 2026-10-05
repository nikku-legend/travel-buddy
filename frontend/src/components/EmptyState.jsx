import { Compass } from "lucide-react";

export default function EmptyState({
  title = "Nothing here yet",
  message = "Start exploring to discover amazing places.",
}) {
  return (
    <div className="rounded-3xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">

      <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100">
        <Compass className="text-slate-500" size={25} />
      </div>

      <h3 className="mt-5 text-lg font-bold text-slate-900">
        {title}
      </h3>

      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
        {message}
      </p>

    </div>
  );
}