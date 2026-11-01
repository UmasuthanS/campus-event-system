import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { dateText } from '../events/discovery';
import PageHeading from '../components/PageHeading';
import CancelRegistrationButton from '../registrations/CancelRegistrationButton';

export default function MyEvents() {
  const { request } = useAuth();
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [period, setPeriod] = useState('upcoming');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setError('');
    request('/users/me/registrations').then(data => { if (active) setRows(data); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [request, retry]);
  const visible = rows?.filter(row => period === 'all' || (new Date(row.event?.endAt || 0).getTime() > Date.now() ? period === 'upcoming' : period === 'past'));
  return <section className="container page-section"><PageHeading eyebrow="Student · My events" title="Your campus experiences">View your real registrations and cancel eligible reservations. Times use Asia/Colombo.</PageHeading><div className="filter-bar"><label htmlFor="my-event-period">Show</label><select id="my-event-period" value={period} onChange={event => setPeriod(event.target.value)}><option value="upcoming">Upcoming &amp; ongoing</option><option value="past">Past events</option><option value="all">All registrations</option></select><Link className="text-link" to="/events">Discover events ↗</Link></div>{error && <p className="form-error" role="alert">{error} <button className="text-link" onClick={() => setRetry(value => value + 1)}>Try again</button></p>}{message && <p className="form-success" role="status">{message}</p>}{!rows && !error && <p role="status">Loading your registrations…</p>}{visible?.length === 0 && <p className="empty-state">No registrations in this period yet.</p>}<div className="management-list">{visible?.map(row => <article className="management-row" key={row.id}><div><p className="eyebrow">{row.status} · Event {row.event?.status || 'unavailable'}</p><h2>{row.event?.title || 'Event unavailable'}</h2><p className="form-hint">{dateText(row.event?.startAt)} · {row.event?.venue}</p>{row.cancelledAt && <p className="form-hint">Cancelled {dateText(row.cancelledAt)}</p>}</div><div className="event-actions">{row.event?.status === 'PUBLISHED' && <Link className="text-link" to={`/events/${row.eventId}`}>View event ↗</Link>}<CancelRegistrationButton registration={row} onCancelled={updated => { setRows(current => current.map(item => item.id === updated.id ? updated : item)); setMessage('Registration cancelled.'); }} /></div></article>)}</div></section>;
}
