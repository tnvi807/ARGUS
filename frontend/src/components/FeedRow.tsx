import { useState } from 'react'
import type { ReactNode } from 'react'
import type { FeedEvent } from '../api/types'
import { DecisionBadge } from './DecisionBadge'
import { FactorList } from './FactorList'
import { RiskPill } from './RiskPill'

function Chip({ children }: { children: ReactNode }) {
  return (
    <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-dim">
      {children}
    </span>
  )
}

export function FeedRow({
  event,
  highlight,
  defaultExpanded = false,
}: {
  event: FeedEvent
  highlight?: boolean
  defaultExpanded?: boolean
}) {
  const [expanded, setExpanded] = useState(defaultExpanded)
  const showExpand = (event.factors?.length ?? 0) > 0 || event.executionDetail

  return (
    <div className={`border-b border-edge last:border-b-0 ${highlight ? 'feed-flash' : ''}`}>
      <button
        type="button"
        onClick={() => showExpand && setExpanded((v) => !v)}
        className={`w-full text-left px-3 py-2 flex flex-wrap items-center gap-x-3 gap-y-1 hover:bg-panel-2/60 ${showExpand ? 'cursor-pointer' : 'cursor-default'}`}
      >
        <span className="font-mono text-[11px] text-faint shrink-0">
          {new Date(event.ts).toLocaleTimeString()}
        </span>
        <span className="flex items-center gap-1.5 min-w-0 shrink-0">
          <span>{event.agentIcon || '·'}</span>
          <span className="text-bright text-sm truncate max-w-[140px]">{event.agentName || event.agentId}</span>
        </span>
        <span className="flex items-center gap-1 min-w-0">
          <Chip>{event.kind}</Chip>
          {event.tool && <Chip>{event.tool}</Chip>}
          {event.category && <Chip>{event.category}</Chip>}
        </span>
        {(event.target || event.message) && (
          <span
            className="font-mono text-xs text-dim truncate max-w-[280px] min-w-0"
            title={event.target || event.message || ''}
          >
            {event.target || event.message}
          </span>
        )}
        <span className="flex-1" />
        <RiskPill score={event.riskScore} />
        <DecisionBadge decision={event.decision} />
        <span
          className="text-xs text-faint truncate max-w-[260px] hidden lg:block"
          title={event.reason}
        >
          {event.reason}
        </span>
      </button>
      {expanded && (
        <div className="px-3 pb-3 pl-16 space-y-3 bg-panel-2/30">
          <div>
            <div className="text-[11px] uppercase tracking-wide text-faint mb-1.5">Risk factors</div>
            <FactorList factors={event.factors || []} />
          </div>
          {event.executionDetail && (
            <div>
              <div className="text-[11px] uppercase tracking-wide text-faint mb-1">Execution</div>
              <p className="text-xs text-dim break-words">{event.executionDetail}</p>
            </div>
          )}
          {event.reason && (
            <div>
              <div className="text-[11px] uppercase tracking-wide text-faint mb-1">Reason</div>
              <p className="text-xs text-dim break-words">{event.reason}</p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
