import { FormEvent, useEffect, useMemo, useState } from 'react'

type Resource = 'universities' | 'sites' | 'rooms' | 'machines' | 'sensors'

type Entity = {
  id: string
  type: Resource
  name: string
  parentId: string | null
  metricType: string | null
  unit: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

type Draft = {
  name: string
  parentId: string
  metricType: 'temperature' | 'speed'
  unit: string
  active: boolean
}

const resources: Array<{ key: Resource; label: string; parent?: Resource }> = [
  { key: 'universities', label: 'Universities' },
  { key: 'sites', label: 'Sites', parent: 'universities' },
  { key: 'rooms', label: 'Rooms', parent: 'sites' },
  { key: 'machines', label: 'Machines', parent: 'rooms' },
  { key: 'sensors', label: 'Sensors', parent: 'machines' },
]

const emptyDraft: Draft = {
  name: '',
  parentId: '',
  metricType: 'temperature',
  unit: '°C',
  active: true,
}

const labels: Record<Resource, string> = {
  universities: 'University',
  sites: 'Site',
  rooms: 'Room',
  machines: 'Machine',
  sensors: 'Sensor',
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(options?.headers ?? {}) },
    ...options,
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.error ?? `Request failed with status ${response.status}`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function App() {
  const [selected, setSelected] = useState<Resource>('universities')
  const [entities, setEntities] = useState<Record<Resource, Entity[]>>({
    universities: [], sites: [], rooms: [], machines: [], sensors: [],
  })
  const [draft, setDraft] = useState<Draft>(emptyDraft)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const selectedConfig = resources.find((item) => item.key === selected)!
  const parentOptions = useMemo(
    () => selectedConfig.parent ? entities[selectedConfig.parent] : [],
    [entities, selectedConfig.parent],
  )

  async function loadEntities() {
    setLoading(true)
    setError('')
    try {
      const values = await Promise.all(resources.map(async ({ key }) => [key, await request<Entity[]>(`/api/${key}`)] as const))
      setEntities(Object.fromEntries(values) as Record<Resource, Entity[]>)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load data')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void loadEntities() }, [])

  function chooseResource(resource: Resource) {
    setSelected(resource)
    setEditingId(null)
    setDraft(emptyDraft)
    setError('')
  }

  function edit(entity: Entity) {
    setEditingId(entity.id)
    setDraft({
      name: entity.name,
      parentId: entity.parentId ?? '',
      metricType: entity.metricType === 'speed' ? 'speed' : 'temperature',
      unit: entity.unit ?? (entity.metricType === 'speed' ? 'rpm' : '°C'),
      active: entity.active,
    })
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      const payload = {
        name: draft.name,
        parentId: selectedConfig.parent ? draft.parentId : null,
        ...(selected === 'sensors' ? {
          metricType: draft.metricType,
          unit: draft.unit,
          active: draft.active,
        } : {}),
      }
      const url = editingId ? `/api/${selected}/${editingId}` : `/api/${selected}`
      await request<Entity>(url, { method: editingId ? 'PUT' : 'POST', body: JSON.stringify(payload) })
      setDraft(emptyDraft)
      setEditingId(null)
      await loadEntities()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save entity')
    } finally {
      setSaving(false)
    }
  }

  async function remove(entity: Entity) {
    if (!window.confirm(`Delete ${entity.name}?`)) return
    setError('')
    try {
      await request<void>(`/api/${selected}/${entity.id}`, { method: 'DELETE' })
      await loadEntities()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete entity')
    }
  }

  return (
    <div className="shell">
      <header className="hero">
        <div>
          <p className="eyebrow">GRAPHCHART / ADMINISTRATION</p>
          <h1>University equipment map</h1>
          <p className="subtitle">Manage the hierarchy that connects places, machines, and sensors.</p>
        </div>
        <div className="health"><span className="health-dot" /> Graph API connected</div>
      </header>

      <main className="workspace">
        <nav className="tabs" aria-label="Entity types">
          {resources.map((resource) => (
            <button className={resource.key === selected ? 'tab active' : 'tab'} key={resource.key} onClick={() => chooseResource(resource.key)}>
              <span>{resource.label}</span><small>{entities[resource.key].length}</small>
            </button>
          ))}
        </nav>

        <section className="content-grid">
          <section className="panel table-panel">
            <div className="panel-heading">
              <div><p className="eyebrow">CURRENT INVENTORY</p><h2>{selectedConfig.label}</h2></div>
              <button className="refresh" onClick={() => void loadEntities()} disabled={loading}>{loading ? 'Loading…' : 'Refresh'}</button>
            </div>
            {error && <div className="error">{error}</div>}
            {loading ? <div className="empty">Loading inventory…</div> : entities[selected].length === 0 ? <div className="empty">No {selectedConfig.label.toLowerCase()} yet. Add the first one.</div> : (
              <div className="table-wrap"><table><thead><tr><th>Name</th><th>{selectedConfig.parent ? 'Parent' : 'Details'}</th>{selected === 'sensors' && <th>Metric</th>}<th>Actions</th></tr></thead><tbody>
                {entities[selected].map((entity) => <tr key={entity.id}><td><strong>{entity.name}</strong><span className="muted">{entity.id.slice(0, 8)}…</span></td><td>{selectedConfig.parent ? (entities[selectedConfig.parent].find((parent) => parent.id === entity.parentId)?.name ?? 'Unknown') : 'Top level'}</td>{selected === 'sensors' && <td><span className="badge">{entity.metricType} · {entity.unit}</span></td>}<td><button className="link-button" onClick={() => edit(entity)}>Edit</button><button className="link-button danger" onClick={() => void remove(entity)}>Delete</button></td></tr>)}
              </tbody></table></div>
            )}
          </section>

          <section className="panel form-panel">
            <p className="eyebrow">{editingId ? 'EDIT RECORD' : 'ADD RECORD'}</p>
            <h2>{editingId ? `Edit ${labels[selected]}` : `New ${labels[selected]}`}</h2>
            <form onSubmit={submit}>
              <label>Name<input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} placeholder={`e.g. ${selected === 'sensors' ? 'Bearing temperature' : `Main ${labels[selected].toLowerCase()}`}`} required /></label>
              {selectedConfig.parent && <label>{labels[selectedConfig.parent]}<select value={draft.parentId} onChange={(event) => setDraft({ ...draft, parentId: event.target.value })} required><option value="">Select parent…</option>{parentOptions.map((parent) => <option key={parent.id} value={parent.id}>{parent.name}</option>)}</select></label>}
              {selected === 'sensors' && <><label>Metric<select value={draft.metricType} onChange={(event) => setDraft({ ...draft, metricType: event.target.value as Draft['metricType'], unit: event.target.value === 'speed' ? 'rpm' : '°C' })}><option value="temperature">Temperature</option><option value="speed">Speed</option></select></label><label>Unit<input value={draft.unit} onChange={(event) => setDraft({ ...draft, unit: event.target.value })} required /></label><label className="check"><input type="checkbox" checked={draft.active} onChange={(event) => setDraft({ ...draft, active: event.target.checked })} /> Sensor is active</label></>}
              <div className="form-actions"><button className="primary" disabled={saving}>{saving ? 'Saving…' : editingId ? 'Save changes' : `Add ${labels[selected]}`}</button>{editingId && <button type="button" className="secondary" onClick={() => { setEditingId(null); setDraft(emptyDraft) }}>Cancel</button>}</div>
            </form>
          </section>
        </section>
      </main>
    </div>
  )
}

export default App
