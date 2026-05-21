import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getToken, clearToken } from '../auth'

export function useStomp(topic: string, onMessage: () => void): { connected: boolean } {
  const [connected, setConnected] = useState(false)
  const callbackRef = useRef(onMessage)
  callbackRef.current = onMessage

  useEffect(() => {
    const token = getToken()
    const client = new Client({
      webSocketFactory: () => new SockJS('/sagaweaw-ws'),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(topic, () => callbackRef.current())
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => {
        setConnected(false)
        const msg = frame.headers?.message ?? ''
        if (msg.includes('Invalid') || msg.includes('locked')) {
          client.deactivate()
          clearToken()
          window.location.reload()
        }
      },
    })
    client.activate()
    return () => { client.deactivate() }
  }, [topic])

  return { connected }
}
