import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { dateText } from '../events/discovery';

export default function EventParticipants({ eventId, version }) {
  const { request } = useAuth();
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setRows(null); setError('');
    request(`/events/${eventId}/registrations`).then(data => { if (active) setRows(data); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [eventId, version, request, retry]);
  return <section id="participants" className="participants-panel"><h2>Event participants</h2><p className="form-hint">Private registration details for authorized club administrators.</p><button className="text-link" onClick={() => setRetry(value => value + 1)}>Refresh participants</button>{error && <p className="form-error" role="alert">{error}</p>}{!rows && !error && <p role="status">Loading participants…</p>}{rows?.length === 0 && <p className="empty-state">No registrations yet.</p>}{rows?.length > 0 && <><p className="form-hint">{rows.filter(row => row.status === 'CONFIRMED').length} confirmed registrations</p><div className="table-scroll"><table><caption>Student event registrations</caption><thead><tr><th>Name</th><th>University registration number</th><th>Status</th><th>Registered</th><th>Cancelled</th></tr></thead><tbody>{rows.map(row => <tr key={row.id}><td>{row.fullName}</td><td>{row.registrationNumber}</td><td>{row.status}</td><td>{dateText(row.createdAt)}</td><td>{row.cancelledAt ? dateText(row.cancelledAt) : '—'}</td></tr>)}</tbody></table></div></>}</section>;
}
