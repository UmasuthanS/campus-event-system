import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import PageHeading from '../components/PageHeading';

export default function ClubManagement() {
  const { user, request } = useAuth();
  const [clubs, setClubs] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    request('/club-admin/clubs').then(data => { if (active) setClubs(data); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [request]);
  return <section className="container page-section"><PageHeading eyebrow="Club management" title={user.role === 'SUPER_ADMIN' ? 'Manage campus clubs' : 'Your assigned clubs'}>{user.role === 'SUPER_ADMIN' ? 'Create and maintain club profiles, status, and administrator assignments.' : 'Edit profiles for the active clubs assigned to your account.'}</PageHeading><div className="hero-actions"><Link className="button button-outline" to="/clubs">Public club listing</Link><Link className="button button-outline" to="/manage/events">Manage events ↗</Link>{user.role === 'SUPER_ADMIN' && <Link className="button button-primary" to="/manage/clubs/new">Create club ↗</Link>}</div>{error && <p className="form-error" role="alert">{error}</p>}{!clubs && !error && <p role="status">Loading club assignments…</p>}{clubs?.length === 0 && <p className="empty-state">No clubs are assigned to your account.</p>}<div className="management-list">{clubs?.map(club => <article className="management-row" key={club.id}><div><p className="eyebrow">{club.shortName} · {club.status}</p><h2>{club.name}</h2></div><div className="event-actions"><Link className="text-link" to={`/manage/events?clubId=${club.id}`}>Events ↗</Link><Link className="text-link" to={`/manage/clubs/${club.id}`}>{club.status === 'INACTIVE' && user.role !== 'SUPER_ADMIN' ? 'View profile' : 'Manage profile'} ↗</Link></div></article>)}</div></section>;
}
