export function ErrorPanel({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="rounded-xl border border-danger/40 bg-danger/5 p-6 my-4">
      <div className="text-danger font-semibold mb-1">Request failed</div>
      <p className="text-dim text-sm mb-3 break-words">{message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="rounded-md border border-edge bg-panel px-3 py-1.5 text-sm text-bright hover:border-danger/50"
        >
          Retry
        </button>
      )}
    </div>
  )
}
