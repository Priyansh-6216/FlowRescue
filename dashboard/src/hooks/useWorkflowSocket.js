import { useEffect, useRef, useState } from 'react'

const WS_URL = (import.meta.env.VITE_WS_URL || 'http://localhost:8080') + '/ws'

/**
 * Hook for subscribing to STOMP WebSocket topics.
 * Fails gracefully when the backend is offline.
 */
export function useWorkflowSocket(onWorkflowList) {
  const clientRef = useRef(null)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    let client = null
    let cancelled = false

    // Dynamically import STOMP + SockJS to avoid SSR/ESM issues
    Promise.all([
      import('@stomp/stompjs'),
      import('sockjs-client'),
    ]).then(([stompModule, sockjsModule]) => {
      if (cancelled) return

      const { Client } = stompModule
      // sockjs-client may export as default or as module.default
      const SockJS = sockjsModule.default || sockjsModule

      client = new Client({
        webSocketFactory: () => new SockJS(WS_URL),
        reconnectDelay: 5000,
        onConnect: () => {
          if (cancelled) return
          setConnected(true)
          // Subscribe to the live workflow list broadcast (every 3s from server)
          client.subscribe('/topic/workflows/list', (msg) => {
            try {
              const data = JSON.parse(msg.body)
              if (onWorkflowList) onWorkflowList(data)
            } catch (e) {
              console.warn('Failed to parse workflow list message', e)
            }
          })
        },
        onDisconnect: () => {
          if (!cancelled) setConnected(false)
        },
        onStompError: (frame) => {
          console.warn('STOMP error:', frame.headers?.message)
          if (!cancelled) setConnected(false)
        },
      })

      clientRef.current = client
      try {
        client.activate()
      } catch (err) {
        console.warn('WebSocket connection failed (backend offline):', err.message)
      }
    }).catch((err) => {
      console.warn('Failed to load WebSocket dependencies:', err.message)
    })

    return () => {
      cancelled = true
      if (clientRef.current) {
        try { clientRef.current.deactivate() } catch (_) {}
        clientRef.current = null
      }
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return { connected }
}

/**
 * Subscribe to a single workflow's updates.
 */
export function useWorkflowDetail(workflowId, onUpdate) {
  const clientRef = useRef(null)
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!workflowId) return
    let cancelled = false

    Promise.all([
      import('@stomp/stompjs'),
      import('sockjs-client'),
    ]).then(([stompModule, sockjsModule]) => {
      if (cancelled) return

      const { Client } = stompModule
      const SockJS = sockjsModule.default || sockjsModule

      const client = new Client({
        webSocketFactory: () => new SockJS(WS_URL),
        reconnectDelay: 5000,
        onConnect: () => {
          if (cancelled) return
          setConnected(true)
          client.subscribe(`/topic/workflows/${workflowId}`, (msg) => {
            try {
              const data = JSON.parse(msg.body)
              if (onUpdate) onUpdate(data)
            } catch (e) {}
          })
        },
        onDisconnect: () => {
          if (!cancelled) setConnected(false)
        },
      })

      clientRef.current = client
      try { client.activate() } catch (_) {}
    }).catch(() => {})

    return () => {
      cancelled = true
      if (clientRef.current) {
        try { clientRef.current.deactivate() } catch (_) {}
        clientRef.current = null
      }
    }
  }, [workflowId])

  return { connected }
}
