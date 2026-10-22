import { useCallback, useEffect, useRef, useState } from 'react'
import * as api from '../api/client'
import { apiErrorMessage } from '../api/client'
import type {
  AnalysisHistoryItem,
  AnalysisResult,
  Factor,
  GatewayPreview,
  Indicator,
  AffectedAgent,
} from '../api/types'
import { useToast } from '../components/Toast'
import { ErrorPanel } from '../components/ErrorPanel'
import { FactorList } from '../components/FactorList'
import { LoadingPanel } from '../components/LoadingPanel'
import { PageHeader } from '../components/PageHeader'
import { RiskGauge } from '../components/RiskGauge'

const RECO_STYLES: Record<string, string> = {
  ALLOW: 'bg-ok/15 text-ok border-ok/40',
  RESTRICT: 'bg-warn/15 text-warn border-warn/40',
  REQUIRE_APPROVAL: 'bg-accent/15 text-accent border-accent/40',
  BLOCK: 'bg-danger/15 text-danger border-danger/40',
}

const SEVERITY_STYLES: Record<string, string> = {
  HIGH: 'bg-danger/15 text-danger border-danger/40',
  MEDIUM: 'bg-warn/15 text-warn border-warn/40',
  LOW: 'bg-ok/15 text-ok border-ok/40',
}

function parseJsonArray<T>(json: string | null): T[] {
  if (!json) return []
  try {
    const parsed: unknown = JSON.parse(json)
    return Array.isArray(parsed) ? (parsed as T[]) : []
  } catch {
    return []
  }
}

function parseJsonObject<T>(json: string | null): T | null {
  if (!json) return null
  try {
    return JSON.parse(json) as T
  } catch {
    return null
  }
}

function historyToResult(item: AnalysisHistoryItem): AnalysisResult {
  return {
    id: item.id,
    ts: item.ts,
    source: (item.sourceType === 'PDF' ? 'PDF' : 'PROMPT') as AnalysisResult['source'],
    filename: item.filename,
    actor: item.actor,
    indicators: parseJsonArray<Indicator>(item.indicatorsJson),
    score: item.score,
    recommendation: (item.recommendation || 'ALLOW') as AnalysisResult['recommendation'],
    reason: item.reason ?? '',
    sensitiveDataRisk: item.sensitiveValues > 0,
    sensitiveValues: item.sensitiveValues,
    affectedAgents: parseJsonArray<AffectedAgent>(item.affectedAgentsJson),
    gateway: parseJsonObject<GatewayPreview>(item.gatewayJson),
    textExcerpt: item.textExcerpt ?? '',
    textLength: item.textLength,
    policyImpact: { categoriesMentioned: [], agentsMentioned: [], restrictedCategoriesTriggered: [], note: '' },
  }
}

function ResultPanel({ result }: { result: AnalysisResult }) {
  const [excerptOpen, setExcerptOpen] = useState(false)
  const pi = result.policyImpact

  return (
    <div className="rounded-xl border border-edge bg-panel p-4 space-y-4">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <div>
          <div className="text-[10px] uppercase tracking-widest text-faint mb-1">Score</div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-2xl text-bright">{result.score}</span>
            <RiskGauge value={result.score} width={120} height={8} />
          </div>
        </div>
        <div>
          <div className="text-[10px] uppercase tracking-widest text-faint mb-1">Recommendation</div>
          <span className={`inline-flex rounded border px-2 py-0.5 text-sm font-semibold ${RECO_STYLES[result.recommendation] ?? 'border-edge bg-panel-2 text-dim'}`}>
            {result.recommendation.replace('_', ' ')}
          </span>
        </div>
        <div className="text-xs text-faint">
          {result.source}
          {result.filename ? ` · ${result.filename}` : ''} · {result.textLength} chars
          {result.ts ? ` · ${new Date(result.ts).toLocaleString()}` : ''}
        </div>
      </div>

      {result.reason && <p className="text-sm text-dim">{result.reason}</p>}

      {result.sensitiveDataRisk && (
        <div className="rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          Sensitive data risk: {result.sensitiveValues} sensitive value{result.sensitiveValues === 1 ? '' : 's'} detected in the submitted content.
        </div>
      )}

      {result.indicators.length > 0 && (
        <div>
          <div className="text-xs uppercase tracking-widest text-faint mb-2">Indicators</div>
          <div className="rounded-lg border border-edge overflow-hidden">
            <table className="w-full text-xs">
              <thead>
                <tr className="text-left text-[10px] uppercase tracking-wide text-faint border-b border-edge bg-panel-2/50">
                  <th className="px-3 py-1.5 font-normal">Severity</th>
                  <th className="px-2 py-1.5 font-normal">Type</th>
                  <th className="px-2 py-1.5 font-normal">Indicator</th>
                  <th className="px-2 py-1.5 font-normal">Evidence</th>
                  <th className="px-3 py-1.5 font-normal text-right">Weight</th>
                </tr>
              </thead>
              <tbody>
                {result.indicators.map((ind, i) => (
                  <tr key={i} className="border-b border-edge last:border-b-0">
                    <td className="px-3 py-1.5">
                      <span className={`rounded border px-1.5 py-0.5 text-[10px] font-semibold ${SEVERITY_STYLES[ind.severity] ?? 'border-edge text-dim'}`}>
                        {ind.severity}
                      </span>
                    </td>
                    <td className="px-2 py-1.5 text-dim">{ind.type}</td>
                    <td className="px-2 py-1.5 font-mono text-bright break-all">{ind.indicator}</td>
                    <td className="px-2 py-1.5 text-dim max-w-[260px] truncate" title={ind.evidence}>
                      {ind.evidence}
                    </td>
                    <td className="px-3 py-1.5 font-mono text-right text-warn-2">+{ind.weight}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {result.affectedAgents.length > 0 && (
        <div>
          <div className="text-xs uppercase tracking-widest text-faint mb-2">Affected agents</div>
          <div className="flex flex-wrap gap-2">
            {result.affectedAgents.map((a) => (
              <span
                key={a.agentId}
                className="rounded border border-edge bg-panel-2 px-2 py-1 text-xs text-dim"
                title={a.relation}
              >
                <span className="mr-1">{a.icon || '·'}</span>
                <span className="text-bright">{a.name}</span> <span className="font-mono text-faint">{a.relation}</span>
              </span>
            ))}
          </div>
        </div>
      )}

      {result.gateway && (
        <div className="rounded-lg border border-edge bg-panel-2/40 p-3 space-y-2">
          <div className="text-xs uppercase tracking-widest text-faint">Gateway preview</div>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            <span className="text-bright">{result.gateway.agentName}</span>
            <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] uppercase text-dim">
              {result.gateway.kind}
            </span>
            {result.gateway.tool && (
              <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] font-mono text-dim">
                {result.gateway.tool}
              </span>
            )}
            {result.gateway.category && (
              <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] uppercase text-dim">
                {result.gateway.category}
              </span>
            )}
            <span className="flex-1" />
            <span className="font-mono text-bright">{result.gateway.score}</span>
            <span
              className={`rounded border px-1.5 py-0.5 text-[11px] font-semibold ${
                result.gateway.decision === 'ALLOWED'
                  ? 'bg-ok/15 text-ok border-ok/40'
                  : result.gateway.decision === 'BLOCKED'
                    ? 'bg-danger/15 text-danger border-danger/40'
                    : result.gateway.decision === 'RESTRICTED'
                      ? 'bg-warn/15 text-warn border-warn/40'
                      : 'bg-accent/15 text-accent border-accent/40'
              }`}
            >
              {result.gateway.decision}
            </span>
          </div>
          {result.gateway.target && (
            <div className="font-mono text-xs text-dim break-all">{result.gateway.target}</div>
          )}
          {result.gateway.note && <div className="text-xs text-faint">{result.gateway.note}</div>}
          {result.gateway.factors?.length > 0 && <FactorList factors={result.gateway.factors as Factor[]} />}
        </div>
      )}

      {result.textExcerpt && (
        <div>
          <button
            type="button"
            onClick={() => setExcerptOpen((v) => !v)}
            className="text-xs text-accent hover:underline"
          >
            {excerptOpen ? 'Hide text excerpt' : 'Show text excerpt'}
          </button>
          {excerptOpen && (
            <pre className="mt-2 rounded-lg border border-edge bg-base p-3 text-xs text-dim whitespace-pre-wrap break-words max-h-60 overflow-y-auto font-mono">
              {result.textExcerpt}
            </pre>
          )}
        </div>
      )}

      {pi && (pi.categoriesMentioned.length > 0 || pi.agentsMentioned.length > 0 || pi.note) && (
        <div className="rounded-lg border border-edge bg-panel-2/40 p-3 text-xs space-y-1">
          <div className="text-[10px] uppercase tracking-widest text-faint mb-1">Policy impact</div>
          {pi.categoriesMentioned.length > 0 && (
            <div>
              <span className="text-faint">Categories: </span>
              <span className="font-mono text-dim">{pi.categoriesMentioned.join(', ')}</span>
            </div>
          )}
          {pi.agentsMentioned.length > 0 && (
            <div>
              <span className="text-faint">Agents: </span>
              <span className="font-mono text-dim">{pi.agentsMentioned.join(', ')}</span>
            </div>
          )}
          {pi.restrictedCategoriesTriggered.length > 0 && (
            <div>
              <span className="text-faint">Restricted triggered: </span>
              <span className="font-mono text-danger">{pi.restrictedCategoriesTriggered.join(', ')}</span>
            </div>
          )}
          {pi.note && <div className="text-dim">{pi.note}</div>}
        </div>
      )}
    </div>
  )
}

export function Analysis() {
  const toast = useToast()
  const [prompt, setPrompt] = useState('')
  const [promptBusy, setPromptBusy] = useState(false)
  const [pdfFile, setPdfFile] = useState<File | null>(null)
  const [pdfBusy, setPdfBusy] = useState(false)
  const [demoBusy, setDemoBusy] = useState(false)
  const [result, setResult] = useState<AnalysisResult | null>(null)
  const [history, setHistory] = useState<AnalysisHistoryItem[]>([])
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [historyLoading, setHistoryLoading] = useState(true)
  const fileRef = useRef<HTMLInputElement | null>(null)

  const loadHistory = useCallback(async () => {
    try {
      const data = await api.analyseHistory(30)
      setHistory(data)
      setHistoryError(null)
    } catch (err) {
      setHistoryError(apiErrorMessage(err))
    } finally {
      setHistoryLoading(false)
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    api
      .analyseHistory(30)
      .then((data) => {
        if (cancelled) return
        setHistory(data)
        setHistoryError(null)
      })
      .catch((err) => {
        if (!cancelled) setHistoryError(apiErrorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setHistoryLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const runPrompt = async () => {
    setPromptBusy(true)
    try {
      setResult(await api.analysePrompt(prompt))
      void loadHistory()
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setPromptBusy(false)
    }
  }

  const runPdf = async (file: File): Promise<boolean> => {
    setPdfBusy(true)
    try {
      setResult(await api.analysePdf(file))
      void loadHistory()
      return true
    } catch (err) {
      toast.error(apiErrorMessage(err))
      return false
    } finally {
      setPdfBusy(false)
    }
  }

  const loadDemoPdf = async () => {
    setDemoBusy(true)
    try {
      const file = await api.fetchDemoPdf()
      setPdfFile(file)
      const ok = await runPdf(file)
      if (ok) {
        toast.success('Malicious demo PDF analysed')
      }
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setDemoBusy(false)
    }
  }

  const loadDetail = async (item: AnalysisHistoryItem) => {
    if (item.id == null) return
    try {
      const full = await api.analyseGet(item.id)
      setResult(historyToResult(full))
    } catch (err) {
      toast.error(apiErrorMessage(err))
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Content Analysis" description="Inspect untrusted prompts and PDFs before they reach an agent" />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="rounded-xl border border-edge bg-panel p-4 space-y-3">
          <div className="text-xs uppercase tracking-widest text-faint">Prompt / text</div>
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            maxLength={20000}
            rows={8}
            placeholder="Paste a prompt, e-mail or document text (max 20 000 chars)…"
            className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright placeholder:text-faint focus:outline-none focus:border-accent/60 font-mono"
          />
          <div className="flex items-center justify-between">
            <span className="font-mono text-[11px] text-faint">{prompt.length}/20000</span>
            <button
              type="button"
              onClick={() => void runPrompt()}
              disabled={promptBusy || prompt.trim().length < 3}
              className="rounded-md border border-accent/50 bg-accent/15 px-4 py-1.5 text-sm text-accent font-semibold disabled:opacity-50"
            >
              {promptBusy ? 'Analysing…' : 'Analyse prompt'}
            </button>
          </div>
        </div>

        <div className="rounded-xl border border-edge bg-panel p-4 space-y-3">
          <div className="text-xs uppercase tracking-widest text-faint">PDF upload</div>
          <input
            ref={fileRef}
            type="file"
            accept="application/pdf"
            onChange={(e) => setPdfFile(e.target.files?.[0] ?? null)}
            className="block w-full text-sm text-dim file:mr-3 file:rounded-md file:border file:border-edge file:bg-panel-2 file:px-3 file:py-1.5 file:text-xs file:text-bright"
          />
          <div className="flex items-center justify-between gap-2">
            <button
              type="button"
              onClick={() => void loadDemoPdf()}
              disabled={demoBusy}
              className="text-xs text-accent hover:underline disabled:opacity-50"
            >
              {demoBusy ? 'Downloading demo PDF…' : 'Use malicious demo PDF'}
            </button>
            <button
              type="button"
              onClick={() => pdfFile && void runPdf(pdfFile)}
              disabled={pdfBusy || !pdfFile}
              className="rounded-md border border-accent/50 bg-accent/15 px-4 py-1.5 text-sm text-accent font-semibold disabled:opacity-50"
            >
              {pdfBusy ? 'Analysing…' : 'Analyse PDF'}
            </button>
          </div>
          {pdfFile && <div className="font-mono text-xs text-dim break-all">{pdfFile.name}</div>}
        </div>
      </div>

      {result && <ResultPanel result={result} />}

      <div>
        <div className="text-xs uppercase tracking-widest text-faint mb-3">Analysis history</div>
        {historyLoading && <LoadingPanel label="Loading history…" />}
        {historyError && <ErrorPanel message={historyError} onRetry={() => void loadHistory()} />}
        {!historyLoading && !historyError && history.length === 0 && (
          <p className="text-faint text-sm">No analyses recorded yet.</p>
        )}
        {history.length > 0 && (
          <div className="rounded-xl border border-edge bg-panel overflow-hidden">
            {history.map((h) => (
              <button
                key={h.id}
                type="button"
                onClick={() => void loadDetail(h)}
                className="w-full text-left px-4 py-2.5 border-b border-edge last:border-b-0 hover:bg-panel-2/60 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm"
              >
                <span className="font-mono text-bright">{h.score}</span>
                <span
                  className={`rounded border px-1.5 py-0.5 text-[10px] font-semibold ${RECO_STYLES[h.recommendation] ?? 'border-edge text-dim'}`}
                >
                  {h.recommendation.replace('_', ' ')}
                </span>
                <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] uppercase text-dim">
                  {h.sourceType}
                </span>
                {h.filename && <span className="font-mono text-xs text-dim truncate max-w-[200px]">{h.filename}</span>}
                <span className="text-xs text-faint">{h.indicatorCount} indicators</span>
                <span className="flex-1" />
                <span className="font-mono text-[11px] text-faint">{new Date(h.ts).toLocaleString()}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
