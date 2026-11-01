import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { dateText, usePublicData } from '../events/discovery';

export default function OrganizerApplications({ event, admin = false, onChanged }) {
  const { user, request } = useAuth();
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);
  const lock = useRef(false);
  const student = user?.role === 'STUDENT';
  const enabled = admin || student;
  const path = admin ? `/events/${event.id}/organizer-applications` : '/users/me/organizer-applications';
  useEffect(() => {
    let active = true;
    setRows(null); setError('');
    if (enabled) request(path).then(data => { if (active) setRows(data); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [path, enabled, request, version]);
  async function perform(action, success) {
    if (lock.current) return;
    lock.current = true;
    if (!window.confirm(`${success.replace(/\.$/, '')}?`)) { lock.current = false; return; }
    setBusy(true); setError(''); setMessage('');
    try { await action(); setMessage(success); setVersion(value => value + 1); onChanged?.(); }
    catch (failure) { setError(failure.message + (failure.fields ? ' ' + Object.values(failure.fields).join(' ') : '')); }
    finally { lock.current = false; setBusy(false); }
  }
  function apply(e) {
    e.preventDefault();
    const fields = Object.fromEntries(new FormData(e.currentTarget));
    perform(() => request(`/events/${event.id}/organizer-applications`, { method: 'POST', body: JSON.stringify(fields) }), 'Submit organizer application.');
  }
  const visible = event && !admin ? rows?.filter(row => row.eventId === event.id) : rows;
  const activeApplication = visible?.find(row => ['PENDING', 'APPROVED'].includes(row.status));
  return <section id={admin ? 'organizer-applications' : undefined} className="rsvp-panel participants-panel" aria-label={admin ? 'Organizer applications' : 'My organizer applications'}><h2>{admin ? 'Organizing applications' : event ? 'Apply as Organizer' : 'My applications'}</h2>{event && <p className="form-hint">{event.remainingOrganizerPlaces} organizing places available · Deadline {dateText(event.organizingApplicationDeadline)}</p>}<button className="text-link" disabled={busy} onClick={() => setVersion(value => value + 1)}>Refresh applications</button><p className="form-hint">An RSVP and an active organizer application cannot be held together. Cancel or withdraw your current role before switching.</p>{error && <p className="form-error" role="alert">{error} <button className="text-link" onClick={() => setVersion(value => value + 1)}>Reload applications</button></p>}{message && <p className="form-success" role="status">{message.replace('Submit organizer application.', 'Application submitted.').replace('Withdraw application.', 'Application withdrawn.').replace('Approve application.', 'Application approved.').replace('Reject application.', 'Application rejected.')}</p>}{enabled && !rows && !error && <p role="status">Loading applications…</p>}{!user && event?.organizingApplicationsOpen && <Link className="button button-primary" to={`/login?returnTo=${encodeURIComponent(`/events/${event.id}`)}`}>Apply as Organizer</Link>}{event && student && rows && !activeApplication && (event.organizingApplicationsOpen ? <form onSubmit={apply}><fieldset disabled={busy}><div className="form-field"><label htmlFor="preferredRole">Preferred role</label><input id="preferredRole" name="preferredRole" required maxLength={100} /></div><div className="form-field"><label htmlFor="motivation">Motivation</label><textarea id="motivation" name="motivation" required maxLength={2000} rows={3} /></div><div className="form-field"><label htmlFor="experience">Experience (optional)</label><textarea id="experience" name="experience" maxLength={2000} rows={3} /></div><button className="button button-primary" disabled={busy || Boolean(error)}>{busy ? 'Submitting…' : 'Submit application'}</button></fieldset></form> : <p className="form-hint">Organizer applications are closed.</p>)}{visible?.length === 0 && <p className="empty-state">No applications yet.</p>}{admin && visible?.some(row => row.status === 'APPROVED') && <div className="organizer-team"><h3>Approved organizing team</h3><ul>{visible.filter(row => row.status === 'APPROVED').map(row => <li key={row.id}>{row.fullName} · {row.preferredRole}</li>)}</ul></div>}{visible?.length > 0 && <div className="table-scroll"><table><caption>Event organizing applications</caption><thead><tr><th>{admin ? 'Student / role' : 'Event / role'}</th><th>Application</th><th>Status</th><th>Actions</th></tr></thead><tbody>{visible.map(row => <tr key={row.id}><td>{admin ? <>{row.fullName}<br />{row.registrationNumber}</> : row.event?.status === 'PUBLISHED' ? <Link className="text-link" to={`/events/${row.eventId}`}>{row.event.title}</Link> : row.event?.title || 'Event unavailable'}<br />{row.preferredRole}</td><td><p className="application-copy">{row.motivation}</p><p className="application-copy">{row.experience || 'No experience provided'}</p><small>Applied {dateText(row.createdAt)}{row.reviewedAt && <> · Reviewed {dateText(row.reviewedAt)}</>}</small></td><td>{row.status}</td><td>{admin && row.status === 'PENDING' && <div className="event-actions">{['APPROVED', 'REJECTED'].map(status => <button className="button button-outline" disabled={busy} key={status} onClick={() => perform(() => request(`/organizer-applications/${row.id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }), status === 'APPROVED' ? 'Approve application.' : 'Reject application.')}>{status === 'APPROVED' ? 'Approve' : 'Reject'}</button>)}</div>}{!admin && row.canWithdraw && <button className="button button-outline" disabled={busy} onClick={() => perform(() => request(`/organizer-applications/${row.id}`, { method: 'DELETE' }), 'Withdraw application.')}>{busy ? 'Working…' : 'Withdraw'}</button>}</td></tr>)}</tbody></table></div>}{student && event && <Link className="text-link" to="/student/my-applications">My applications ↗</Link>}</section>;
}

export function OrganizingTeam({ eventId }) {
  const result = usePublicData(`/events/${eventId}/organizing-team`);
  return <section className="rsvp-panel"><h2>Organizing team</h2>{result.loading && <p role="status">Loading organizing team…</p>}{result.error && <p className="form-error" role="alert">{result.error} <button className="text-link" onClick={result.retry}>Try again</button></p>}{result.data?.length === 0 && <p className="form-hint">The organizing team has not been announced yet.</p>}<ul>{result.data?.map((member, index) => <li key={index}>{member.fullName} · {member.preferredRole}</li>)}</ul></section>;
}
