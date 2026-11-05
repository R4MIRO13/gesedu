import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RETRASO_VALIDACION_MS, useValorConRetraso } from './useValorConRetraso'

describe('useValorConRetraso', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('devuelve el valor inicial de inmediato y sin pendiente', () => {
    const { result } = renderHook(() => useValorConRetraso('a'))

    expect(result.current).toEqual({ valor: 'a', pendiente: false })
  })

  it('solo publica el último valor cuando pasa el retraso sin nuevos cambios', () => {
    const { result, rerender } = renderHook(({ v }) => useValorConRetraso(v), { initialProps: { v: 'a' } })

    rerender({ v: 'b' })
    act(() => vi.advanceTimersByTime(RETRASO_VALIDACION_MS - 100))
    rerender({ v: 'c' })
    act(() => vi.advanceTimersByTime(RETRASO_VALIDACION_MS - 100))
    expect(result.current).toEqual({ valor: 'a', pendiente: true })

    act(() => vi.advanceTimersByTime(100))
    expect(result.current).toEqual({ valor: 'c', pendiente: false })
  })

  it('compara los objetos por contenido: un objeto nuevo con los mismos datos no reinicia la espera', () => {
    const { result, rerender } = renderHook(({ v }) => useValorConRetraso(v), { initialProps: { v: { n: 1 } } })

    rerender({ v: { n: 1 } })

    expect(result.current.pendiente).toBe(false)
  })
})
