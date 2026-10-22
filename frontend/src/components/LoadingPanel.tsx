export function LoadingPanel({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-dim">
      <span className="inline-block h-4 w-4 rounded-full border-2 border-accent border-t-transparent animate-spin" />
      <span>{label}</span>
    </div>
  )
}
