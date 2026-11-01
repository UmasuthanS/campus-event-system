import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import PageHeading from '../components/PageHeading';
import ClubAdminAssignments from '../components/ClubAdminAssignments';

export default function ClubAdministrators() {
  const { request } = useAuth();
  const [items, setItems] = useState(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setError(''); setItems(null);
    request('/club-admin/clubs').then(clubs => Promise.all(clubs.map(club => request(`/club-admin/clubs/${club.id}`))))
      .then(data => { if (active) setItems(data); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [request, retry]);
  return <section className="container page-section"><PageHeading eyebrow="Super Admin · Access management" title="Club administrator assignments">Manage access for IEEE, ComES, AIESEC, and other campus clubs. Only Super Admins can assign or revoke administrators.</PageHeading><Link className="text-link" to="/manage/clubs">Manage club profiles ↗</Link>{!items && !error && <p role="status">Loading clubs and administrator assignments…</p>}{error && <div className="form-error" role="alert">{error} <button className="text-link" onClick={() => setRetry(retry + 1)}>Try again</button></div>}{items?.length === 0 && <p className="empty-state">No clubs are available. Create clubs or enable the existing development club seed.</p>}<div className="card-grid club-list admin-assignment-grid">{items?.map(item => <article className="club-admin-panel" key={item.club.id} aria-label={`${item.club.shortName} administrators`}><p className="eyebrow">{item.club.shortName} · {item.club.status}</p><h2>{item.club.name}</h2><ClubAdminAssignments clubId={item.club.id} admins={item.admins} active={item.club.status === 'ACTIVE'} onUpdated={updated => setItems(current => current.map(existing => existing.club.id === updated.club.id ? updated : existing))} /></article>)}</div></section>;
}
