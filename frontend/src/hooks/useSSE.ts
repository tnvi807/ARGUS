import { useEffect, useRef, useState } from 'react'
import { getToken, setToken } from '../api/client'

export type SSEHandler = (name: string, data: unknown) => void

function parseFrame(raw: string): { name: string; data: string } | null {
  let name = 'message'
  const dataLines: string[] = []
  for (const line of raw.split('\n')) {
    if (!line || line.startsWith(':')) {
      continue
    }
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    if (field === 'event') {
      name = value
    } else if (field === 'data') {
      dataLines.push(value)
    }
  }
  if (dataLines.length === 0 && name === 'message') {
    return null
  }
  return { name, data: dataLines.join('\n') }
}

export function useSSE(onEvent: SSEHandler, enabled = true): boolean {
  const handlerRef = useRef(onEvent)
  useEffect(() => {
    handlerRef.current = onEvent
  }, [onEvent])
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!enabled) {
      return
    }
    let disposed = false
    let controller: AbortController | null = null
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined

    const connect = async () => {
      controller = new AbortController()
      try {
        const token = getToken()
        const res = await fetch('/api/events/stream', {
          headers: {
            Accept: 'text/event-stream',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          signal: controller.signal,
        })
        if (res.status === 401) {
          setToken(null)
          window.dispatchEvent(new Event('argus:unauthorized'))
          return
        }
        if (!res.ok || !res.body) {
          throw new Error(`SSE stream failed: ${res.status}`)
        }
        setConnected(true)
        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        for (;;) {
          const { done, value } = await reader.read()
          if (done) {
            break
          }
          buffer += decoder.decode(value, { stream: true })
          let sep: number
          while ((sep = buffer.indexOf('\n\n')) !== -1) {
            const raw = buffer.slice(0, sep)
            buffer = buffer.slice(sep + 2)
            const frame = parseFrame(raw)
            if (frame) {
              let payload: unknown = null
              if (frame.data) {
                try {
                  payload = JSON.parse(frame.data)
                } catch {
                  payload = frame.data
                }
              }
              handlerRef.current(frame.name, payload)
            }
          }
        }
        throw new Error('SSE stream ended')
      } catch {
        // aborted or failed; reconnect below unless disposed
      } finally {
        setConnected(false)
      }
      if (!disposed) {
        reconnectTimer = setTimeout(connect, 5000)
      }
    }

    void connect()
    return () => {
      disposed = true
      controller?.abort()
      if (reconnectTimer) {
        clearTimeout(reconnectTimer)
      }
      setConnected(false)
    }
  }, [enabled])

  return connected
}
