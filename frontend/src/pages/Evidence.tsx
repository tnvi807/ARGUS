import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import * as api from '../api/client'
import { apiErrorMessage } from '../api/client'
import type { UploadedFile } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { EmptyState } from '../components/EmptyState'
import { ErrorPanel } from '../components/ErrorPanel'
import { LoadingPanel } from '../components/LoadingPanel'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../components/Toast'

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

export function Evidence() {
  const toast = useToast()
  const { hasPermission } = useAuth()
  const canUpload = hasPermission('evidence.upload')
  const [files, setFiles] = useState<UploadedFile[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<UploadedFile | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    let cancelled = false
    api
      .evidenceList()
      .then((data) => {
        if (!cancelled) {
          setFiles(data)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(apiErrorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  const upload = async (file: File) => {
    setUploading(true)
    try {
      const created = await api.evidenceUpload(file)
      toast.success(`Uploaded ${created.originalName}`)
      setReloadKey((k) => k + 1)
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  const download = async (file: UploadedFile) => {
    try {
      await api.downloadEvidence(file)
    } catch (err) {
      toast.error(apiErrorMessage(err))
    }
  }

  const remove = async (file: UploadedFile) => {
    try {
      await api.evidenceDelete(file.id!)
      toast.success(`Deleted ${file.originalName}`)
      setReloadKey((k) => k + 1)
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setConfirmDelete(null)
    }
  }

  const onFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0]
    if (f) void upload(f)
  }

  if (loading && files.length === 0 && !error) return <LoadingPanel label="Loading evidence…" />
  if (error) return <ErrorPanel message={error} onRetry={() => setReloadKey((k) => k + 1)} />

  return (
    <div>
      <PageHeader
        title="Evidence Vault"
        description="Validated, hashed investigative files attached to security cases"
        actions={
          canUpload ? (
            <>
              <input
                ref={fileRef}
                type="file"
                accept=".json,.csv,.txt,.log,.md,.png,.jpg,.jpeg,.pdf"
                onChange={onFileChange}
                className="hidden"
              />
              <button
                type="button"
                onClick={() => fileRef.current?.click()}
                disabled={uploading}
                className="rounded-md border border-accent/50 bg-accent/15 px-3 py-1.5 text-sm text-accent font-semibold disabled:opacity-50"
              >
                {uploading ? 'Uploading…' : 'Upload evidence'}
              </button>
            </>
          ) : null
        }
      />

      {!canUpload && (
        <p className="text-xs text-faint mb-3">
          You can view and download evidence. Uploading requires the evidence.upload permission.
        </p>
      )}

      {files.length === 0 ? (
        <EmptyState title="No evidence files" description="Upload json, csv, txt, log, md, images or pdf files." />
      ) : (
        <div className="rounded-xl border border-edge bg-panel overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[10px] uppercase tracking-wide text-faint border-b border-edge">
                <th className="px-4 py-2 font-normal">Time</th>
                <th className="px-2 py-2 font-normal">Filename</th>
                <th className="px-2 py-2 font-normal">Kind</th>
                <th className="px-2 py-2 font-normal">Size</th>
                <th className="px-2 py-2 font-normal">SHA-256</th>
                <th className="px-2 py-2 font-normal">Uploaded by</th>
                <th className="px-4 py-2 font-normal text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {files.map((f) => (
                <tr key={f.id} className="border-b border-edge last:border-b-0 hover:bg-panel-2/60">
                  <td className="px-4 py-2 font-mono text-xs text-faint whitespace-nowrap">
                    {new Date(f.ts).toLocaleString()}
                  </td>
                  <td className="px-2 py-2 text-bright font-mono text-xs break-all max-w-[240px]">
                    {f.originalName}
                  </td>
                  <td className="px-2 py-2">
                    <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] uppercase text-dim">
                      {f.kind ?? '—'}
                    </span>
                  </td>
                  <td className="px-2 py-2 font-mono text-xs text-dim">{formatBytes(f.sizeBytes)}</td>
                  <td className="px-2 py-2 font-mono text-[10px] text-faint max-w-[140px] truncate" title={f.sha256 ?? ''}>
                    {f.sha256 ? `${f.sha256.slice(0, 12)}…` : '—'}
                  </td>
                  <td className="px-2 py-2 text-xs text-dim">{f.uploadedBy ?? '—'}</td>
                  <td className="px-4 py-2 text-right whitespace-nowrap">
                    <button
                      type="button"
                      onClick={() => void download(f)}
                      className="rounded border border-edge px-2 py-1 text-[11px] text-dim hover:text-accent hover:border-accent/40 mr-2"
                    >
                      Download
                    </button>
                    {canUpload && (
                      <button
                        type="button"
                        onClick={() => setConfirmDelete(f)}
                        className="rounded border border-danger/40 px-2 py-1 text-[11px] text-danger hover:bg-danger/10"
                      >
                        Delete
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        open={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        title="Delete evidence"
      >
        <p className="text-sm text-dim mb-4">
          Permanently delete <span className="font-mono text-bright">{confirmDelete?.originalName}</span> from
          the vault? The audit trail entry for the upload is preserved.
        </p>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={() => setConfirmDelete(null)}
            className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => confirmDelete && void remove(confirmDelete)}
            className="rounded-md border border-danger/50 bg-danger/15 px-3 py-1.5 text-sm text-danger font-semibold"
          >
            Delete
          </button>
        </div>
      </Modal>
    </div>
  )
}
