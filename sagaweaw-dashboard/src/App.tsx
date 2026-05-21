import { useState } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Header from './components/Header'
import SagaFeed from './pages/SagaFeed'
import SagaDetail from './pages/SagaDetail'
import DeadLetters from './pages/DeadLetters'
import TokenGate from './pages/TokenGate'
import { getToken, clearToken } from './auth'
import { setUnauthHandler } from './api/client'

export default function App() {
  const [authed, setAuthed] = useState(() => !!getToken())

  setUnauthHandler(() => {
    clearToken()
    setAuthed(false)
  })

  if (!authed) {
    return <TokenGate onAuth={() => setAuthed(true)} />
  }

  // BASE_URL is '/' in standalone dev and '/sagaweaw/' in embedded build
  const basename = import.meta.env.BASE_URL.replace(/\/$/, '') || '/'

  return (
    <BrowserRouter basename={basename}>
      <div className="min-h-screen bg-canvas">
        <Header />
        <main className="max-w-8xl mx-auto px-4 sm:px-6 pt-4 h-[calc(100vh-3.5rem)] overflow-hidden">
          <Routes>
            <Route path="/"             element={<SagaFeed />} />
            <Route path="/dead-letters" element={<DeadLetters />} />
            <Route path="/sagas/:id"    element={<SagaDetail />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  )
}
