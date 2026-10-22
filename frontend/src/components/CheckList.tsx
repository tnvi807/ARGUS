export function CheckList({
  title,
  options,
  selected,
  onChange,
  columns = 2,
}: {
  title: string
  options: { code: string; label?: string }[]
  selected: string[]
  onChange: (next: string[]) => void
  columns?: number
}) {
  const toggle = (code: string) => {
    onChange(selected.includes(code) ? selected.filter((c) => c !== code) : [...selected, code])
  }
  return (
    <div>
      <div className="text-xs uppercase tracking-wide text-faint mb-1">
        {title} <span className="font-mono text-dim">({selected.length})</span>
      </div>
      <div
        className="rounded-md border border-edge bg-base p-2 max-h-40 overflow-y-auto gap-x-3"
        style={{ display: 'grid', gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
      >
        {options.map((opt) => (
          <label key={opt.code} className="flex items-center gap-1.5 text-xs text-dim cursor-pointer hover:text-bright">
            <input
              type="checkbox"
              checked={selected.includes(opt.code)}
              onChange={() => toggle(opt.code)}
              className="accent-[#22d3ee]"
            />
            <span className="truncate" title={opt.label ? `${opt.code} — ${opt.label}` : opt.code}>
              {opt.label ?? opt.code}
            </span>
          </label>
        ))}
      </div>
    </div>
  )
}
