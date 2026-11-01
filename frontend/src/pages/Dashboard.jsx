import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { rolePages } from '../auth/roles';
import { useAuth } from '../auth/AuthContext';
import PageHeading from '../components/PageHeading';

export default function Dashboard({ role }) {
  const { request, logout } = useAuth();
  const navigate = useNavigate();
  const page = rolePages[role];
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    Promise.all([request('/users/me'), request(page.access)])
      .then(([data]) => { if (active) setProfile(data); })
      .catch(failure => {
        if (!active) return;
        if (failure.status === 403) navigate('/unauthorized', { replace: true });
        else setError(failure.message);
      });
    return () => { active = false; };
  }, [request, page.access, navigate]);
  return <section className="container page-section"><PageHeading eyebrow={page.label} title={profile ? `Welcome, ${profile.fullName}.` : page.label}>{role === 'STUDENT' ? 'Discover campus events and manage your registrations using My events.' : role === 'SUPER_ADMIN' ? 'Manage campus clubs and administrator assignments using the links below.' : 'Manage profiles and events for your assigned clubs using the links below.'}</PageHeading>{error && <p className="form-error" role="alert">{error}</p>}{!profile && !error && <p role="status">Loading your profile…</p>}{profile && <dl className="profile-details"><div><dt>Email</dt><dd>{profile.email}</dd></div><div><dt>Registration number</dt><dd>{profile.registrationNumber}</dd></div><div><dt>Role</dt><dd>{profile.role}</dd></div></dl>}<div className="hero-actions">{role === 'STUDENT' && <Link className="button button-outline" to="/student/my-applications">My applications ↗</Link>}{role === 'STUDENT' && <Link className="button button-primary" to="/student/my-events">My events ↗</Link>}{role === 'SUPER_ADMIN' && <Link className="button button-primary" to="/super-admin/club-administrators">Manage club administrators ↗</Link>}{['CLUB_ADMIN', 'SUPER_ADMIN'].includes(role) && <Link className="button button-outline" to="/manage/clubs">Manage clubs ↗</Link>}<Link className="button button-primary" to="/events">Explore events ↗</Link><button className="button button-outline" onClick={logout}>Logout</button></div></section>;
}
