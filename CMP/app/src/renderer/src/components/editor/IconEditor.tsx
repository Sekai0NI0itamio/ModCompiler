import { useState, useRef, useEffect, useCallback } from 'react'
import { Pencil, Eraser, Paintbrush, Pipette, Undo2, Redo2, Trash2, Save, Download } from 'lucide-react'
import { useStore } from '../../store/useStore'
import type { Manifest } from '../../../../shared/types'

const CANVAS_SIZES = [16, 32, 64, 128] as const
const DISPLAY_SIZE = 280 // px on screen — fits in half-width column

type Tool = 'pencil' | 'eraser' | 'fill' | 'picker'

const PALETTE = [
  '#000000', '#333333', '#555555', '#888888', '#bbbbbb', '#ffffff',
  '#ff0000', '#ff5500', '#ff9900', '#ffcc00', '#ffff00', '#ccff00',
  '#00ff00', '#00cc00', '#009900', '#006600', '#003300', '#00ffcc',
  '#00ccff', '#0099ff', '#0066ff', '#0033ff', '#0000ff', '#3300ff',
  '#6600ff', '#9900ff', '#cc00ff', '#ff00ff', '#ff0099', '#ff0066',
  '#8B4513', '#A0522D', '#D2691E', '#CD853F', '#DEB887', '#F5DEB3',
  '#2d1b00', '#4a2f00', '#6b4400', '#8B6914', '#C4A000', '#E8D44D',
]

interface Props {
  manifest: Manifest
  updateManifest: (partial: Partial<Manifest>) => void
}

export function IconEditor({ manifest, updateManifest }: Props) {
  const { iconPreview, setIconPreview, currentBundlePath } = useStore()

  const [canvasSize, setCanvasSize] = useState<number>(32)
  const [pixels, setPixels] = useState<string[][]>([])
  const [currentColor, setCurrentColor] = useState<string>('#ffffff')
  const [tool, setTool] = useState<Tool>('pencil')
  const [saving, setSaving] = useState(false)

  // History stored in refs for speed — no React re-render on each push
  const historyRef = useRef<string[]>([])
  const historyIndexRef = useRef<number>(-1)
  const [historyLen, setHistoryLen] = useState(0) // trigger re-render for undo/redo button states
  const [historyPos, setHistoryPos] = useState(-1)

  // Working buffer — mutated directly during drawing, bypasses React state
  const workingPixelsRef = useRef<string[][]>([])
  const isDrawingRef = useRef(false)
  const lastPixelRef = useRef<{ x: number; y: number } | null>(null)

  const canvasRef = useRef<HTMLCanvasElement>(null)
  const previewCanvasRef = useRef<HTMLCanvasElement>(null)

  // Initialize pixel grid
  const initPixels = useCallback((size: number): string[][] => {
    return Array.from({ length: size }, () => Array.from({ length: size }, () => ''))
  }, [])

  // Initialize on mount or size change
  useEffect(() => {
    const newPixels = initPixels(canvasSize)
    workingPixelsRef.current = newPixels
    setPixels(newPixels)
    const serialized = JSON.stringify(newPixels)
    historyRef.current = [serialized]
    historyIndexRef.current = 0
    setHistoryLen(1)
    setHistoryPos(0)
  }, [canvasSize, initPixels])

  // Push to history (uses ref, fast)
  const pushHistory = useCallback((newPixels: string[][]) => {
    const serialized = JSON.stringify(newPixels)
    const truncated = historyRef.current.slice(0, historyIndexRef.current + 1)
    truncated.push(serialized)
    historyRef.current = truncated
    historyIndexRef.current = truncated.length - 1
    setHistoryLen(truncated.length)
    setHistoryPos(historyIndexRef.current)
  }, [])

  // Undo
  const undo = useCallback(() => {
    if (historyIndexRef.current <= 0) return
    historyIndexRef.current -= 1
    const entry = historyRef.current[historyIndexRef.current]
    if (entry) {
      const restored = JSON.parse(entry)
      workingPixelsRef.current = restored
      setPixels(restored)
      setHistoryPos(historyIndexRef.current)
    }
  }, [])

  // Redo
  const redo = useCallback(() => {
    if (historyIndexRef.current >= historyRef.current.length - 1) return
    historyIndexRef.current += 1
    const entry = historyRef.current[historyIndexRef.current]
    if (entry) {
      const restored = JSON.parse(entry)
      workingPixelsRef.current = restored
      setPixels(restored)
      setHistoryPos(historyIndexRef.current)
    }
  }, [])

  // Keyboard shortcut: Cmd+Z / Ctrl+Z for undo, Cmd+Shift+Z / Ctrl+Shift+Z for redo
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      const isCmd = e.metaKey || e.ctrlKey
      if (!isCmd) return

      if (e.key === 'z' && !e.shiftKey) {
        e.preventDefault()
        undo()
      } else if ((e.key === 'z' && e.shiftKey) || e.key === 'y') {
        e.preventDefault()
        redo()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [undo, redo])

  // Draw a single pixel directly to canvas (fast, no React state)
  const drawPixelToCanvas = useCallback((x: number, y: number, color: string) => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const pixelSize = DISPLAY_SIZE / canvasSize

    if (color) {
      ctx.fillStyle = color
    } else {
      // Redraw checkerboard for erased pixel
      const isLight = (x + y) % 2 === 0
      ctx.fillStyle = isLight ? '#2a2a3a' : '#222233'
    }
    ctx.fillRect(x * pixelSize, y * pixelSize, pixelSize, pixelSize)

    // Redraw grid line over this pixel if needed
    if (pixelSize >= 4) {
      ctx.strokeStyle = 'rgba(255,255,255,0.08)'
      ctx.lineWidth = 1
      const px = x * pixelSize
      const py = y * pixelSize
      ctx.strokeRect(px, py, pixelSize, pixelSize)
    }
  }, [canvasSize])

  // Full canvas redraw (used on undo/redo, zoom change, size change)
  const drawFullCanvas = useCallback(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const pxData = workingPixelsRef.current
    const pixelSize = DISPLAY_SIZE / canvasSize

    canvas.width = DISPLAY_SIZE
    canvas.height = DISPLAY_SIZE

    for (let y = 0; y < canvasSize; y++) {
      for (let x = 0; x < canvasSize; x++) {
        const px = x * pixelSize
        const py = y * pixelSize

        const isLight = (x + y) % 2 === 0
        ctx.fillStyle = isLight ? '#2a2a3a' : '#222233'
        ctx.fillRect(px, py, pixelSize, pixelSize)

        const color = pxData[y]?.[x]
        if (color) {
          ctx.fillStyle = color
          ctx.fillRect(px, py, pixelSize, pixelSize)
        }
      }
    }

    // Grid lines
    if (pixelSize >= 4) {
      ctx.strokeStyle = 'rgba(255,255,255,0.08)'
      ctx.lineWidth = 1
      for (let i = 0; i <= canvasSize; i++) {
        const pos = i * pixelSize
        ctx.beginPath()
        ctx.moveTo(pos, 0)
        ctx.lineTo(pos, DISPLAY_SIZE)
        ctx.stroke()
        ctx.beginPath()
        ctx.moveTo(0, pos)
        ctx.lineTo(DISPLAY_SIZE, pos)
        ctx.stroke()
      }
    }
  }, [canvasSize])

  // Redraw full canvas when pixels state changes (from undo/redo/init)
  useEffect(() => {
    drawFullCanvas()
  }, [pixels, drawFullCanvas])

  // Update preview canvas
  useEffect(() => {
    const canvas = previewCanvasRef.current
    if (!canvas) return
    const pxData = workingPixelsRef.current
    canvas.width = canvasSize
    canvas.height = canvasSize
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    ctx.clearRect(0, 0, canvasSize, canvasSize)
    for (let y = 0; y < canvasSize; y++) {
      for (let x = 0; x < canvasSize; x++) {
        const color = pxData[y]?.[x]
        if (color) {
          ctx.fillStyle = color
          ctx.fillRect(x, y, 1, 1)
        }
      }
    }
  }, [pixels, canvasSize])

  // Get pixel coordinates from mouse event
  function getPixelCoords(e: React.MouseEvent<HTMLCanvasElement>): { x: number; y: number } | null {
    const canvas = canvasRef.current
    if (!canvas) return null
    const rect = canvas.getBoundingClientRect()
    const pixelSize = DISPLAY_SIZE / canvasSize

    const cx = (e.clientX - rect.left) * (canvas.width / rect.width)
    const cy = (e.clientY - rect.top) * (canvas.height / rect.height)

    const x = Math.floor(cx / pixelSize)
    const y = Math.floor(cy / pixelSize)

    if (x < 0 || x >= canvasSize || y < 0 || y >= canvasSize) return null
    return { x, y }
  }

  // Bresenham line for smooth drawing between mouse move events
  function plotLine(x0: number, y0: number, x1: number, y1: number, plotFn: (x: number, y: number) => void) {
    const dx = Math.abs(x1 - x0)
    const dy = Math.abs(y1 - y0)
    const sx = x0 < x1 ? 1 : -1
    const sy = y0 < y1 ? 1 : -1
    let err = dx - dy

    let cx = x0
    let cy = y0

    while (true) {
      plotFn(cx, cy)
      if (cx === x1 && cy === y1) break
      const e2 = 2 * err
      if (e2 > -dy) { err -= dy; cx += sx }
      if (e2 < dx) { err += dx; cy += sy }
    }
  }

  // Apply pencil/eraser to working buffer + draw to canvas directly
  function applyBrush(x: number, y: number) {
    const buf = workingPixelsRef.current
    if (!buf[y]) return

    if (tool === 'pencil') {
      buf[y][x] = currentColor
    } else if (tool === 'eraser') {
      buf[y][x] = ''
    }

    drawPixelToCanvas(x, y, buf[y][x])
  }

  // Flood fill
  function floodFill(startX: number, startY: number, fillColor: string) {
    const buf = workingPixelsRef.current
    const targetColor = buf[startY][startX]
    if (targetColor === fillColor) return

    const stack: [number, number][] = [[startX, startY]]
    const visited = new Set<string>()

    while (stack.length > 0) {
      const [x, y] = stack.pop()!
      const key = `${x},${y}`
      if (visited.has(key)) continue
      if (x < 0 || x >= canvasSize || y < 0 || y >= canvasSize) continue
      if (buf[y][x] !== targetColor) continue

      visited.add(key)
      buf[y][x] = fillColor
      drawPixelToCanvas(x, y, fillColor)

      stack.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1])
    }

    // Commit to React state
    setPixels(buf.map(row => [...row]))
    pushHistory(buf.map(row => [...row]))
  }

  function handleMouseDown(e: React.MouseEvent<HTMLCanvasElement>) {
    const coords = getPixelCoords(e)
    if (!coords) return

    isDrawingRef.current = true
    lastPixelRef.current = coords

    if (tool === 'pencil' || tool === 'eraser') {
      applyBrush(coords.x, coords.y)
    } else if (tool === 'fill') {
      floodFill(coords.x, coords.y, currentColor)
    } else if (tool === 'picker') {
      const picked = workingPixelsRef.current[coords.y]?.[coords.x]
      if (picked) setCurrentColor(picked)
    }
  }

  function handleMouseMove(e: React.MouseEvent<HTMLCanvasElement>) {
    if (!isDrawingRef.current) return
    if (tool === 'fill' || tool === 'picker') return

    const coords = getPixelCoords(e)
    if (!coords) return

    // Use Bresenham line to fill gaps between mouse events
    const last = lastPixelRef.current
    if (last && (last.x !== coords.x || last.y !== coords.y)) {
      plotLine(last.x, last.y, coords.x, coords.y, (x, y) => {
        applyBrush(x, y)
      })
    } else {
      applyBrush(coords.x, coords.y)
    }

    lastPixelRef.current = coords
  }

  function handleMouseUp() {
    if (isDrawingRef.current) {
      isDrawingRef.current = false
      lastPixelRef.current = null

      // Commit working buffer to React state + history
      if (tool === 'pencil' || tool === 'eraser') {
        const snapshot = workingPixelsRef.current.map(row => [...row])
        setPixels(snapshot)
        pushHistory(snapshot)
      }
    }
  }

  // Clear canvas
  function handleClear() {
    const newPixels = initPixels(canvasSize)
    workingPixelsRef.current = newPixels
    setPixels(newPixels)
    pushHistory(newPixels)
  }

  // Save icon
  async function handleSave() {
    setSaving(true)
    try {
      // Render final icon at canvasSize resolution
      const outCanvas = document.createElement('canvas')
      outCanvas.width = canvasSize
      outCanvas.height = canvasSize
      const ctx = outCanvas.getContext('2d')
      if (!ctx) return

      const buf = workingPixelsRef.current
      ctx.clearRect(0, 0, canvasSize, canvasSize)
      for (let y = 0; y < canvasSize; y++) {
        for (let x = 0; x < canvasSize; x++) {
          const color = buf[y]?.[x]
          if (color) {
            ctx.fillStyle = color
            ctx.fillRect(x, y, 1, 1)
          }
        }
      }

      const base64Data = outCanvas.toDataURL('image/png').replace(/^data:image\/png;base64,/, '')
      setIconPreview(base64Data)
      updateManifest({ icon: 'icon.png' })

      if (currentBundlePath) {
        try {
          await window.electronAPI.bundleWriteBase64File(base64Data, currentBundlePath, 'icon.png')
        } catch {
          console.warn('bundleWriteBase64File failed. Icon saved to store only.')
        }
      }
    } finally {
      setSaving(false)
    }
  }

  // Export as PNG download
  function handleExport() {
    const outCanvas = document.createElement('canvas')
    outCanvas.width = canvasSize
    outCanvas.height = canvasSize
    const ctx = outCanvas.getContext('2d')
    if (!ctx) return

    const buf = workingPixelsRef.current
    ctx.clearRect(0, 0, canvasSize, canvasSize)
    for (let y = 0; y < canvasSize; y++) {
      for (let x = 0; x < canvasSize; x++) {
        const color = buf[y]?.[x]
        if (color) {
          ctx.fillStyle = color
          ctx.fillRect(x, y, 1, 1)
        }
      }
    }

    const link = document.createElement('a')
    link.download = `${manifest.mod_info.slug || 'icon'}-${canvasSize}x${canvasSize}.png`
    link.href = outCanvas.toDataURL('image/png')
    link.click()
  }

  // Load existing icon into editor
  function handleLoadExisting() {
    if (!iconPreview) return
    const img = new Image()
    img.onload = () => {
      const tempCanvas = document.createElement('canvas')
      tempCanvas.width = img.width
      tempCanvas.height = img.height
      const ctx = tempCanvas.getContext('2d')
      if (!ctx) return
      ctx.drawImage(img, 0, 0)

      const size = Math.min(img.width, 128) as typeof CANVAS_SIZES[number]
      setCanvasSize(size)
      const newPixels = initPixels(size)

      const imageData = ctx.getImageData(0, 0, img.width, img.height)
      for (let y = 0; y < size && y < img.height; y++) {
        for (let x = 0; x < size && x < img.width; x++) {
          const i = (y * img.width + x) * 4
          const r = imageData.data[i]
          const g = imageData.data[i + 1]
          const b = imageData.data[i + 2]
          const a = imageData.data[i + 3]
          if (a > 0) {
            newPixels[y][x] = `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`
          }
        }
      }
      workingPixelsRef.current = newPixels
      setPixels(newPixels)
      pushHistory(newPixels)
    }
    img.src = `data:image/png;base64,${iconPreview}`
  }

  const toolButtons: { id: Tool; icon: typeof Pencil; label: string }[] = [
    { id: 'pencil', icon: Pencil, label: 'Pencil (B)' },
    { id: 'eraser', icon: Eraser, label: 'Eraser (E)' },
    { id: 'fill', icon: Paintbrush, label: 'Fill (G)' },
    { id: 'picker', icon: Pipette, label: 'Pick Color (I)' },
  ]

  // Tool keyboard shortcuts
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.metaKey || e.ctrlKey || e.altKey) return
      switch (e.key.toLowerCase()) {
        case 'b': setTool('pencil'); break
        case 'e': setTool('eraser'); break
        case 'g': setTool('fill'); break
        case 'i': setTool('picker'); break
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [])

  const canUndo = historyPos > 0
  const canRedo = historyPos < historyLen - 1

  return (
    <div className="bg-charcoal/50 rounded-xl border border-white/5 p-5">
      <h2 className="text-sm font-semibold text-white mb-4">Icon Editor</h2>

      <div className="flex gap-5">
        {/* Left: Canvas */}
        <div className="flex-shrink-0">
          {/* Toolbar */}
          <div className="flex items-center gap-1 mb-2">
            {toolButtons.map(({ id, icon: Icon, label }) => (
              <button
                key={id}
                onClick={() => setTool(id)}
                title={label}
                className={`p-1.5 rounded-md transition-all ${
                  tool === id
                    ? 'bg-teal/15 text-teal border border-teal/30'
                    : 'text-white/30 hover:text-white/60 hover:bg-white/5 border border-transparent'
                }`}
              >
                <Icon size={14} />
              </button>
            ))}
            <div className="w-px h-5 bg-white/10 mx-1" />
            <button onClick={undo} disabled={!canUndo} title="Undo (⌘Z)" className="p-1.5 rounded-md text-white/30 hover:text-white/60 disabled:opacity-30 disabled:cursor-not-allowed">
              <Undo2 size={14} />
            </button>
            <button onClick={redo} disabled={!canRedo} title="Redo (⌘⇧Z)" className="p-1.5 rounded-md text-white/30 hover:text-white/60 disabled:opacity-30 disabled:cursor-not-allowed">
              <Redo2 size={14} />
            </button>
            <div className="w-px h-5 bg-white/10 mx-1" />
            <button onClick={handleClear} title="Clear" className="p-1.5 rounded-md text-white/30 hover:text-red-400">
              <Trash2 size={14} />
            </button>
          </div>

          {/* Canvas */}
          <div className="overflow-hidden rounded-lg border border-white/10 bg-surface" style={{ width: DISPLAY_SIZE, height: DISPLAY_SIZE }}>
            <canvas
              ref={canvasRef}
              className="cursor-crosshair"
              style={{
                width: DISPLAY_SIZE,
                height: DISPLAY_SIZE,
                imageRendering: 'pixelated',
              }}
              onMouseDown={handleMouseDown}
              onMouseMove={handleMouseMove}
              onMouseUp={handleMouseUp}
              onMouseLeave={handleMouseUp}
            />
          </div>
        </div>

        {/* Right: Controls & Preview */}
        <div className="flex-1 min-w-[180px] space-y-4">
          {/* Canvas size */}
          <div>
            <label className="block text-xs text-white/40 mb-1.5">Canvas Size</label>
            <div className="flex gap-1.5">
              {CANVAS_SIZES.map((size) => (
                <button
                  key={size}
                  onClick={() => setCanvasSize(size)}
                  className={`px-2 py-1 rounded-md text-[11px] font-medium transition-all ${
                    canvasSize === size
                      ? 'bg-teal/15 text-teal border border-teal/30'
                      : 'bg-white/5 text-white/30 border border-white/5 hover:text-white/50'
                  }`}
                >
                  {size}×{size}
                </button>
              ))}
            </div>
          </div>

          {/* Current color */}
          <div>
            <label className="block text-xs text-white/40 mb-1.5">Color</label>
            <div className="flex items-center gap-2">
              <div
                className="w-8 h-8 rounded-lg border-2 border-white/20"
                style={{ backgroundColor: currentColor || 'transparent' }}
              />
              <input
                type="color"
                value={currentColor}
                onChange={(e) => setCurrentColor(e.target.value)}
                className="w-8 h-8 rounded cursor-pointer bg-transparent border-0"
              />
              <span className="text-[10px] font-mono text-white/40">{currentColor}</span>
            </div>
          </div>

          {/* Palette */}
          <div>
            <label className="block text-xs text-white/40 mb-1.5">Palette</label>
            <div className="grid grid-cols-6 gap-1">
              {PALETTE.map((color) => (
                <button
                  key={color}
                  onClick={() => setCurrentColor(color)}
                  className={`w-6 h-6 rounded border-2 transition-all ${
                    currentColor === color ? 'border-teal scale-110' : 'border-white/10 hover:border-white/30'
                  }`}
                  style={{ backgroundColor: color }}
                  title={color}
                />
              ))}
            </div>
          </div>

          {/* Preview */}
          <div>
            <label className="block text-xs text-white/40 mb-1.5">Preview</label>
            <div className="flex items-center gap-3">
              <div className="w-16 h-16 rounded-lg bg-surface border border-white/10 overflow-hidden flex items-center justify-center"
                style={{ backgroundImage: 'linear-gradient(45deg, #2a2a3a 25%, transparent 25%), linear-gradient(-45deg, #2a2a3a 25%, transparent 25%), linear-gradient(45deg, transparent 75%, #2a2a3a 75%), linear-gradient(-45deg, transparent 75%, #2a2a3a 75%)', backgroundSize: '8px 8px', backgroundPosition: '0 0, 0 4px, 4px -4px, -4px 0px' }}>
                <canvas
                  ref={previewCanvasRef}
                  className="w-full h-full"
                  style={{ imageRendering: canvasSize <= 64 ? 'pixelated' : 'auto' }}
                />
              </div>
              <div className="text-[10px] text-white/20 space-y-0.5">
                <p>{canvasSize}×{canvasSize} pixels</p>
                <p>Tool: {tool}</p>
                <p>⌘Z undo · ⌘⇧Z redo</p>
              </div>
            </div>
          </div>

          {/* Actions */}
          <div className="space-y-2">
            <button
              onClick={handleSave}
              disabled={saving}
              className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium bg-teal/15 text-teal border border-teal/30 hover:bg-teal/25 transition-all disabled:opacity-50"
            >
              <Save size={12} />
              {saving ? 'Saving...' : 'Save to Bundle'}
            </button>
            <button
              onClick={handleExport}
              className="w-full flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-white/5 text-white/40 border border-white/5 hover:text-white/60 transition-all"
            >
              <Download size={12} />
              Export PNG
            </button>
            {iconPreview && (
              <button
                onClick={handleLoadExisting}
                className="w-full flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-white/5 text-white/40 border border-white/5 hover:text-white/60 transition-all"
              >
                Load Current Icon
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Hidden preview canvas for export */}
      <canvas ref={previewCanvasRef} className="hidden" />
    </div>
  )
}
