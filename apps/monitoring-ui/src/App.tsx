import { useCallback, useEffect, useMemo, useState } from 'react'

type Resource = 'universities' | 'sites' | 'rooms' | 'machines' | 'sensors'
type Entity = { id: string; type: Resource; name: string; parentId: string | null; metricType: string | null; unit: string | null; active: boolean }
type Reading = { sensorId: string; metricType: string; value: number; unit: string; occurredAt: string; quality: string }

const labels: Record<Resource, string> = { universities: 'Universities', sites: 'Sites', rooms: 'Rooms', machines: 'Machines', sensors: 'Sensors' }

async function get<T>(url: string): Promise<T> {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`)
  return response.json() as Promise<T>
}

function App() {
  const [entities, setEntities] = useState<Record<Resource, Entity[]>>({ universities: [], sites: [], rooms: [], machines: [], sensors: [] })
  const [latest, setLatest] = useState<Record<string, Reading>>({})
  const [history, setHistory] = useState<Reading[]>([])
  const [selectedSensor, setSelectedSensor] = useState<string>('')
  const [selectedUniversity, setSelectedUniversity] = useState<string>('')
  const [selectedSite, setSelectedSite] = useState<string>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)

  const load = useCallback(async () => {
    setError('')
    try {
      const values = await Promise.all((Object.keys(labels) as Resource[]).map(async (key) => [key, await get<Entity[]>(`/api/${key}`)] as const))
      const next = Object.fromEntries(values) as Record<Resource, Entity[]>
      setEntities(next)
      const readings = await Promise.all(next.sensors.map(async (sensor) => {
        try { return await get<Reading>(`/api/telemetry/latest?sensorId=${sensor.id}`) } catch { return null }
      }))
      const mapped = Object.fromEntries(readings.filter((reading): reading is Reading => reading !== null).map((reading) => [reading.sensorId, reading]))
      setLatest(mapped)
      const sensorId = selectedSensor || next.sensors[0]?.id || ''
      if (sensorId) {
        setSelectedSensor(sensorId)
        try { setHistory(await get<Reading[]>(`/api/telemetry/readings?sensorId=${sensorId}`)) } catch { setHistory([]) }
      } else setHistory([])
      setLastUpdated(new Date())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load monitoring data')
    } finally { setLoading(false) }
  }, [selectedSensor])

  useEffect(() => { void load(); const timer = window.setInterval(() => void load(), 60000); return () => window.clearInterval(timer) }, [load])

  const selectedReading = selectedSensor ? latest[selectedSensor] : undefined
  const availableSites = useMemo(() => selectedUniversity ? entities.sites.filter((site) => site.parentId === selectedUniversity) : entities.sites, [entities.sites, selectedUniversity])
  const machinesWithSensors = useMemo(() => entities.machines
    .map((machine) => ({ machine, sensors: entities.sensors.filter((sensor) => sensor.parentId === machine.id) }))
    .filter(({ machine }) => {
      const room = entities.rooms.find((item) => item.id === machine.parentId)
      const site = room && entities.sites.find((item) => item.id === room.parentId)
      return (!selectedUniversity || site?.parentId === selectedUniversity) && (!selectedSite || site?.id === selectedSite)
    }), [entities, selectedUniversity, selectedSite])
  const location = (sensor: Entity) => {
    const machine = entities.machines.find((item) => item.id === sensor.parentId)
    const room = machine && entities.rooms.find((item) => item.id === machine.parentId)
    const site = room && entities.sites.find((item) => item.id === room.parentId)
    const university = site && entities.universities.find((item) => item.id === site.parentId)
    return [university?.name, site?.name, room?.name, machine?.name].filter(Boolean).join(' / ') || 'Location not configured'
  }

  return <div className="dashboard">
    <header className="topbar"><div><p className="eyebrow">GRAPHCHART / LIVE OPERATIONS</p><h1>Machine monitoring</h1><p className="intro">Topology from Apache AGE, measurements from the telemetry stream.</p></div><div className="live"><span /> LIVE {lastUpdated && <small>Updated {lastUpdated.toLocaleTimeString()}</small>}</div></header>
    <main className="main">
      {error && <div className="alert">{error}</div>}
      <section className="stats"><div className="stat"><span>Universities</span><strong>{entities.universities.length}</strong></div><div className="stat"><span>Machines</span><strong>{entities.machines.length}</strong></div><div className="stat"><span>Active sensors</span><strong>{entities.sensors.filter((sensor) => sensor.active).length}</strong></div><div className="stat"><span>Reporting now</span><strong>{Object.keys(latest).length}</strong></div></section>
      <section className="layout">
        <div className="left-column">
          <div className="section-heading"><div><p className="eyebrow">TOPOLOGY</p><h2>Machine locations</h2></div><div className="filters"><select aria-label="Filter by university" value={selectedUniversity} onChange={(event) => { setSelectedUniversity(event.target.value); setSelectedSite('') }}><option value="">All universities</option>{entities.universities.map((university) => <option key={university.id} value={university.id}>{university.name}</option>)}</select><select aria-label="Filter by site" value={selectedSite} onChange={(event) => setSelectedSite(event.target.value)}><option value="">All sites</option>{availableSites.map((site) => <option key={site.id} value={site.id}>{site.name}</option>)}</select><button onClick={() => void load()} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button></div></div>
          {machinesWithSensors.length === 0 ? <div className="empty">No machines have been configured yet.</div> : <div className="machine-grid">{machinesWithSensors.map(({ machine, sensors }) => <article className="machine" key={machine.id}><div className="machine-head"><div><span className="machine-label">MACHINE</span><h3>{machine.name}</h3></div><span className="machine-status">{sensors.some((sensor) => latest[sensor.id]) ? 'Reporting' : 'Waiting'}</span></div><p className="location">{sensors[0] ? location(sensors[0]) : 'No sensors assigned'}</p><div className="sensor-list">{sensors.map((sensor) => { const reading = latest[sensor.id]; return <button className={selectedSensor === sensor.id ? 'sensor selected' : 'sensor'} key={sensor.id} onClick={() => setSelectedSensor(sensor.id)}><span className="sensor-name"><i className={reading ? 'sensor-dot on' : 'sensor-dot'} />{sensor.name}</span><strong>{reading ? `${reading.value} ${reading.unit}` : 'No data'}</strong><small>{sensor.metricType}</small></button> })}</div></article>)}</div>}
        </div>
        <aside className="panel detail"><p className="eyebrow">TELEMETRY DETAIL</p><h2>{selectedReading?.metricType ?? 'Select a sensor'}</h2>{selectedSensor && <p className="detail-location">{entities.sensors.find((sensor) => sensor.id === selectedSensor) ? location(entities.sensors.find((sensor) => sensor.id === selectedSensor)!) : ''}</p>}{selectedReading ? <><div className="big-reading"><strong>{selectedReading.value}</strong><span>{selectedReading.unit}</span></div><p className="reading-time">Last reading {new Date(selectedReading.occurredAt).toLocaleString()}</p><div className="history"><div className="history-label"><span>Recent history</span><span>{history.length} readings</span></div><div className="bars">{history.slice(-24).map((reading, index) => <div className="bar" key={`${reading.occurredAt}-${index}`} style={{ height: `${Math.max(12, Math.min(100, Number(reading.value) * (reading.metricType === 'temperature' ? 3 : .06)))}%` }} title={`${reading.value} ${reading.unit}`} />)}</div></div></> : <div className="empty detail-empty">Select a sensor card to inspect its latest reading.</div>}</aside>
      </section>
    </main>
  </div>
}

export default App
