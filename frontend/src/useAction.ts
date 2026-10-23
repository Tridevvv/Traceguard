import { useCallback, useState } from 'react'

/** Wraps an async call with busy/error state so forms don't repeat the same try/catch. */
export function useAction() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const run = useCallback(async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true)
    setError(null)
    try {
      return await fn()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      return undefined
    } finally {
      setBusy(false)
    }
  }, [])

  return { run, busy, error, clearError: () => setError(null) }
}
