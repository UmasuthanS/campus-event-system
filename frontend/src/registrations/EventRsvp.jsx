import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import CancelRegistrationButton from './CancelRegistrationButton';

export default function EventRsvp({ event, onChanged }) {
  const { user, request } = useAuth();
  const navigate = useNavigate();
  const lock = useRef(false);
  const [registration, setRegistration] = useState(null);
  const [loading, setLoading] = useState(user?.role === 'STUDENT');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setRegistration(null); setError(''); setMessage('');
    if (user?.role !== 'STUDENT') { setLoading(false); return; }
    setLoading(true);
    request('/users/me/registrations').then(rows => { if (active) setRegistration(rows.find(row => row.eventId === event.id) || null); })
      .catch(failure => { if (active) setError(failure.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [event.id, user?.role, request, retry]);
  async function register() {
    if (!user) { navigate(`/login?returnTo=${encodeURIComponent(`/events/${event.id}`)}`); return; }
    if (lock.current) return;
    lock.current = true;
    if (!window.confirm(`Register for “${event.title}”?`)) { lock.current = false; return; }
    setBusy(true); setError(''); setMessage('');
    try { setRegistration(await request(`/events/${event.id}/registrations`, { method: 'POST' })); setMessage('Registration confirmed.'); onChanged(); }
    catch (failure) { setError(failure.message); }
    finally { lock.current = false; setBusy(false); }
  }
  const confirmed = registration?.status === 'CONFIRMED';
  return <section className="rsvp-panel" aria-label="Event registration"><h2>Join this event</h2><p className="intro"><strong>{event.remainingSeats}</strong> remaining seats · Capacity {event.capacity}</p>{loading && <p role="status">Checking your registration…</p>}{error && <p className="form-error" role="alert">{error} <button className="text-link" onClick={() => setRetry(value => value + 1)}>Check again</button></p>}{message && <p className="form-success" role="status">{message}</p>}{registration?.status === 'CANCELLED' && <p className="form-success" role="status">Your registration is cancelled.</p>}{confirmed ? <><p className="form-success" role="status">Registered</p><CancelRegistrationButton registration={registration} onCancelled={updated => { setRegistration(updated); setMessage('Registration cancelled.'); onChanged(); }} />{!registration.canCancel && <p className="form-hint">Cancellation is closed for this event.</p>}</> : user && user.role !== 'STUDENT' ? <p className="form-hint">Only student accounts can register for events.</p> : <><button className="button button-primary" disabled={loading || busy || Boolean(error) || Boolean(user) && (!event.registrationOpen || event.remainingSeats === 0)} onClick={register}>{busy ? 'Registering…' : 'Register for Event'}</button>{!event.registrationOpen && <p className="form-hint">The registration window is closed.</p>}{event.registrationOpen && event.remainingSeats === 0 && <p className="form-hint">This event is full.</p>}</>}{user?.role === 'STUDENT' && <Link className="text-link" to="/student/my-events">My events ↗</Link>}</section>;
}
