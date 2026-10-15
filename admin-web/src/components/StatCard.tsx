interface Props {
  label: string;
  value: string | number;
  caption?: string;
}

/** Matches the portfolio admin's stat-tile style: label, huge number, small caption - no icon. */
export default function StatCard({ label, value, caption }: Props) {
  return (
    <div className="bg-apple-surface rounded-2xl border border-apple-border p-5 shadow-card hover:shadow-card-hover transition-shadow duration-200">
      <div className="text-sm text-apple-secondary">{label}</div>
      <div className="text-3xl font-bold text-apple-text mt-2 tracking-tight">{value}</div>
      {caption && <div className="text-xs text-apple-secondary mt-1">{caption}</div>}
    </div>
  );
}
