import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import PageHeading from '../components/PageHeading';
import EventForm from '../components/EventForm';
import EventStatusActions from '../components/EventStatusActions';

export default function EventEditor() {
  const { id } = useParams();
  const { request } = useAuth();
  const [params] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [clubs, setClubs] = useState(null);
  const [clubId, setClubId] = useState('');
  const [event, setEvent] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  useEffect(() => {
    let active = true;
    setClubs(null); setEvent(null); setError(''); setErrors({}); setMessage(location.state?.message || '');
    Promise.all([request('/club-admin/clubs'), id ? request(`/club-admin/events/${id}`) : Promise.resolve(null)]).then(([available, saved]) => {
      if (!active) return;
      setClubs(available); setEvent(saved);
      setClubId(saved?.clubId || available.find(club => club.id === params.get('clubId') && club.status === 'ACTIVE')?.id || available.find(club => club.status === 'ACTIVE')?.id || '');
    }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [id, request, params, location.state]);
  async function perform(action, success) {
    if (busy) return;
    setBusy(true); setError(''); setErrors({}); setMessage('');
    try { await action(); setMessage(success); }
    catch (failure) { setError(failure.message); setErrors(failure.fields || {}); }
    finally { setBusy(false); }
  }
  async function save(values) {
    await perform(async () => {
      const saved = await request(id ? `/events/${id}` : `/clubs/${clubId}/events`, { method: id ? 'PATCH' : 'POST', body: JSON.stringify(values) });
      if (id) setEvent(saved);
      else navigate(`/manage/events/${saved.id}`, { replace: true, state: { message: 'Draft event created.' } });
    }, 'Event saved.');
  }
  const selected = clubs?.find(club => club.id === clubId);
  const readOnly = event && (['CANCELLED', 'COMPLETED'].includes(event.status) || selected?.status !== 'ACTIVE');
  return <section className="container page-section"><Link className="text-link" to={`/manage/events${clubId ? `?clubId=${clubId}` : ''}`}>← Manage events</Link><PageHeading eyebrow={event ? `${event.status} event` : 'Draft event'} title={event?.title || 'Create a campus event'}>Plan the details for your club. Publication is a separate action.</PageHeading>{error && <p className="form-error" role="alert">{error}</p>}{message && <p className="form-success" role="status">{message}</p>}{!clubs && !error && <p role="status">Loading event editor…</p>}{clubs && !clubId && <p className="empty-state">An active, assigned club is required to create events.</p>}{clubs && clubId && <div className="event-editor"><div className="form-field"><label htmlFor="editor-club">Club</label><select id="editor-club" value={clubId} disabled={Boolean(id) || busy} onChange={value => setClubId(value.target.value)}>{clubs.filter(club => id || club.status === 'ACTIVE').map(club => <option key={club.id} value={club.id}>{club.shortName}</option>)}</select></div>{readOnly && <p className="demo-notice">This profile is read-only because the event is terminal or its club is inactive.</p>}<EventForm key={`${id || 'new'}-${event?.updatedAt || ''}`} initialValues={event || undefined} onSubmit={save} loading={busy} errors={errors} readOnly={Boolean(readOnly)} />{event && <EventStatusActions event={event} busy={busy} inactive={selected?.status !== 'ACTIVE'} onChange={status => perform(async () => setEvent(await request(`/events/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) })), `Event ${status.toLowerCase()}.`)} />}</div>}</section>;
}
