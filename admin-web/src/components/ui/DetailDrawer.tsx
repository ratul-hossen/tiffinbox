import type { ReactNode } from "react";
import { X } from "lucide-react";

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: ReactNode;
}

/**
 * Slide-over panel used for the Chef / Order / User "detail" views. A single
 * shared shell so every entity's detail view looks and behaves the same way.
 */
export default function DetailDrawer({ open, onClose, title, subtitle, children }: Props) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end fade-in">
      <div
        className="absolute inset-0 bg-black/30 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden="true"
      />
      <div className="drawer-panel relative w-full max-w-lg bg-white h-full shadow-2xl overflow-y-auto">
        <div className="sticky top-0 bg-white/95 backdrop-blur-xl border-b border-black/5 px-6 py-4 flex items-start justify-between z-10">
          <div className="min-w-0">
            <h3 className="text-lg font-semibold text-apple-text tracking-tight truncate">
              {title}
            </h3>
            {subtitle && (
              <p className="text-sm text-apple-secondary mt-0.5 truncate">{subtitle}</p>
            )}
          </div>
          <button
            onClick={onClose}
            className="shrink-0 rounded-full p-2 text-apple-secondary hover:bg-black/5 hover:text-apple-text transition-colors duration-150"
            aria-label="Close"
          >
            <X size={18} />
          </button>
        </div>
        <div className="p-6">{children}</div>
      </div>
    </div>
  );
}
