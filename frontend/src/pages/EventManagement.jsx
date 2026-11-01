import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import PageHeading from '../components/PageHeading';
import EventStatusActions from '../components/EventStatusActions';

export default function EventManagement() {
  const { request } = useAuth();
  const [params, setParams] = useSearchParams();
  const [clubs, setClubs] = useState(null);
  const [events, setEvents] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    request('/club-admin/clubs').then(data => { if (active) setClubs(data); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [request]);
  const selected = clubs?.find(club => club.id === params.get('clubId')) || clubs?.[0];
  useEffect(() => {
    let active = true;
    setEvents(null); setError(''); setMessage('');
    if (selected) request(`/club-admin/clubs/${selected.id}/events`).then(data => { if (active) setEvents(data); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [selected?.id, request]);
  async function changeStatus(event, status) {
    if (busy) return;
    setBusy(true); setError(''); setMessage('');
    try {
      const updated = await request(`/events/${event.id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) });
      setEvents(current => current.map(item => item.id === updated.id ? updated : item));
      setMessage(`Event ${status.toLowerCase()}.`);
    } catch (failure) { setError(failure.message + (failure.fields ? ' ' + Object.values(failure.fields).join(' ') : '')); }
    finally { setBusy(false); }
  }
  return <section className="container page-section"><PageHeading eyebrow="Event management" title="Manage your club’s events">Create drafts, update event details, and publish or cancel events for your authorized clubs.</PageHeading><Link className="text-link" to="/manage/clubs">← Manage clubs</Link>{clubs?.length > 0 && <div className="filter-bar event-toolbar"><label htmlFor="event-club">Club</label><select id="event-club" value={selected?.id || ''} disabled={busy} onChange={event => setParams({ clubId: event.target.value })}>{clubs.map(club => <option value={club.id} key={club.id}>{club.shortName} · {club.status}</option>)}</select>{selected?.status === 'ACTIVE' && <Link className="button button-primary" to={`/manage/events/new?clubId=${selected.id}`}>Create event ↗</Link>}</div>}{error && <p className="form-error" role="alert">{error}</p>}{message && <p className="form-success" role="status">{message}</p>}{!clubs && !error && <p role="status">Loading authorized clubs…</p>}{clubs?.length === 0 && <p className="empty-state">No clubs are assigned to your account.</p>}{selected && !events && !error && <p role="status">Loading events…</p>}{events?.length === 0 && <p className="empty-state">No events for this club yet.</p>}<div className="management-list">{events?.map(event => <article className="management-row event-management-row" key={event.id}><div><p className="eyebrow">{event.status} · {event.category}</p><h2>{event.title}</h2><p className="form-hint">{event.startAt ? new Date(event.startAt).toLocaleString() : 'Schedule not set'}</p></div><div><Link className="text-link" to={`/manage/events/${event.id}`}>{['CANCELLED', 'COMPLETED'].includes(event.status) ? 'View details' : 'Edit details'} ↗</Link><EventStatusActions event={event} busy={busy} inactive={selected?.status !== 'ACTIVE'} onChange={status => changeStatus(event, status)} /></div></article>)}</div></section>;
}
