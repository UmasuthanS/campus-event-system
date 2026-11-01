import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import PageHeading from '../components/PageHeading';
import ClubCard from '../components/ClubCard';
import { apiRequest } from '../auth/api';
import { useAuth } from '../auth/AuthContext';

export default function Clubs() {
  const { user } = useAuth();
  const [clubs, setClubs] = useState(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setError(''); setClubs(null);
    apiRequest('/clubs').then(data => { if (active) setClubs(data); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [retry]);
  return <section className="container page-section"><PageHeading eyebrow="Find your people" title="Built around shared passions">Explore active communities at the Faculty of Engineering, including IEEE, ComES, and AIESEC.</PageHeading>{['CLUB_ADMIN', 'SUPER_ADMIN'].includes(user?.role) && <Link className="button button-primary" to="/manage/clubs">Manage clubs ↗</Link>}{error && <div className="form-error" role="alert">{error} <button className="text-link" onClick={() => setRetry(retry + 1)}>Try again</button></div>}{!clubs && !error && <p role="status">Loading clubs…</p>}{clubs?.length === 0 && <p className="empty-state">No active clubs are available yet.</p>}<div className="card-grid club-list">{clubs?.map(club => <ClubCard key={club.id} club={club} />)}</div></section>;
}
