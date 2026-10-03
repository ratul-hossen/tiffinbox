import { Search } from "lucide-react";

interface Props {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}

/** The global top-bar search box. Filters the currently-open page's list client-side. */
export default function SearchInput({ value, onChange, placeholder }: Props) {
  return (
    <div className="relative w-full max-w-xs">
      <Search
        size={16}
        className="absolute left-3 top-1/2 -translate-y-1/2 text-apple-secondary pointer-events-none"
      />
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder ?? "Search…"}
        className="w-full rounded-xl border border-black/5 bg-black/[0.03] pl-9 pr-3 py-2 text-sm text-apple-text placeholder:text-apple-secondary focus:outline-none focus:ring-2 focus:ring-apple-blue/50 focus:bg-white focus:border-apple-blue/30 transition-all duration-150"
      />
    </div>
  );
}
