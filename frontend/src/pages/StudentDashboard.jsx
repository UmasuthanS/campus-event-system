import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import PageHeading from '../components/PageHeading';

export default function StudentDashboard() {
  const { request, logout } = useAuth();
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    request('/users/me').then(data => { if (active) setProfile(data); })
      .catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [request]);
  return <section className="container page-section"><PageHeading eyebrow="Student account" title={profile ? `Welcome, ${profile.fullName}.` : 'Your student account'}>You’re signed in. Event discovery and RSVP features will arrive in later stages.</PageHeading>{error && <p className="form-error" role="alert">{error}</p>}{!profile && !error && <p role="status">Loading your profile…</p>}{profile && <dl className="profile-details"><div><dt>Email</dt><dd>{profile.email}</dd></div><div><dt>Registration number</dt><dd>{profile.registrationNumber}</dd></div><div><dt>Role</dt><dd>{profile.role}</dd></div></dl>}<div className="hero-actions"><Link className="button button-primary" to="/events">Explore demo events ↗</Link><button className="button button-outline" onClick={logout}>Logout</button></div></section>;
}
