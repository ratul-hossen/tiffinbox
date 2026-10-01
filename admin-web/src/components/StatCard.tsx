interface Props {
  label: string;
  value: string | number;
  emoji?: string;
}

export default function StatCard({ label, value, emoji }: Props) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <span className="text-sm text-slate-500">{label}</span>
        {emoji && <span className="text-xl">{emoji}</span>}
      </div>
      <div className="text-2xl font-semibold text-slate-900 mt-2">{value}</div>
    </div>
  );
}
