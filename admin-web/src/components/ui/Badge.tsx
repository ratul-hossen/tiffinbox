import type { ReactNode } from "react";

export type BadgeTone = "green" | "amber" | "red" | "blue" | "neutral";

const TONE_CLASSES: Record<BadgeTone, string> = {
  green: "bg-apple-green/10 text-apple-green",
  amber: "bg-apple-amber/10 text-apple-amber",
  red: "bg-apple-red/10 text-apple-red",
  blue: "bg-apple-blue/10 text-apple-blue",
  neutral: "bg-black/5 text-apple-secondary",
};

interface Props {
  tone: BadgeTone;
  children: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
}

/** Small pill badge used consistently for every status/verification state. */
export default function Badge({ tone, children, onClick, disabled }: Props) {
  const classes = `inline-flex items-center gap-1 text-xs font-medium rounded-full px-2.5 py-1 transition-colors duration-150 ${TONE_CLASSES[tone]} ${
    onClick ? "hover:opacity-80 cursor-pointer" : ""
  } ${disabled ? "opacity-50 cursor-not-allowed" : ""}`;

  if (onClick) {
    return (
      <button type="button" onClick={onClick} disabled={disabled} className={classes}>
        {children}
      </button>
    );
  }
  return <span className={classes}>{children}</span>;
}
