import type { LucideIcon } from "lucide-react";

interface Props {
  label: string;
  value: string | number;
  icon: LucideIcon;
}

export default function StatCard({ label, value, icon: Icon }: Props) {
  return (
    <div className="bg-apple-surface rounded-2xl border border-black/5 p-5 shadow-card hover:shadow-card-hover transition-shadow duration-200">
      <div className="flex items-center justify-between">
        <span className="text-sm text-apple-secondary">{label}</span>
        <span className="text-apple-blue bg-apple-blue/10 rounded-full p-1.5">
          <Icon size={15} strokeWidth={2.25} />
        </span>
      </div>
      <div className="text-2xl font-bold text-apple-text mt-3 tracking-tight">{value}</div>
    </div>
  );
}
