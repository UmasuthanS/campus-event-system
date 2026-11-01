import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { rolePages } from '../auth/roles';
import { useAuth } from '../auth/AuthContext';
import PageHeading from '../components/PageHeading';
import { StudentPanel, AdministratorPanel } from '../dashboards/DashboardPanels';

export default function Dashboard({ role }) {
  const { request, logout } = useAuth();
  const navigate = useNavigate();
  const page = rolePages[role];
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setData(null); setError('');
    const calls = role === 'STUDENT' ? [request('/users/me/registrations'), request('/users/me/organizer-applications')] : [request(role === 'SUPER_ADMIN' ? '/super-admin/dashboard' : '/club-admin/dashboard')];
    Promise.all([request('/users/me'), ...calls]).then(([profile, first, second]) => {
      if (active) setData(role === 'STUDENT' ? { profile, registrations: first, applications: second } : { profile, summary: first });
    }).catch(failure => {
      if (!active) return;
      if (failure.status === 403) navigate('/unauthorized', { replace: true });
      else setError(failure.message);
    });
    return () => { active = false; };
  }, [request, role, navigate, version]);
  return <section className="container page-section"><PageHeading eyebrow={page.label} title={data ? `Welcome, ${data.profile.fullName}.` : page.label}>{role === 'STUDENT' ? 'Your registrations, organizing applications, and campus profile.' : role === 'SUPER_ADMIN' ? 'Platform statistics, clubs, and administrator assignments.' : 'Events, participants, and organizing applications for your assigned clubs.'}</PageHeading>{error && <p className="form-error" role="alert">{error} <button className="text-link" onClick={() => setVersion(value => value + 1)}>Try again</button></p>}{!data && !error && <p role="status">Loading your dashboard…</p>}{message && <p className="form-success" role="status">{message}</p>}{data && <><div className="dashboard-toolbar"><button className="text-link" onClick={() => { setMessage(''); setVersion(value => value + 1); }}>Refresh dashboard</button><button className="text-link" onClick={logout}>Logout</button></div><section className="dashboard-section"><h2>Your profile</h2><dl className="profile-details"><div><dt>Email</dt><dd>{data.profile.email}</dd></div><div><dt>Registration number</dt><dd>{data.profile.registrationNumber}</dd></div><div><dt>Role</dt><dd>{data.profile.role}</dd></div></dl></section>{role === 'STUDENT' ? <StudentPanel registrations={data.registrations} applications={data.applications} onCancelled={updated => { setData(current => ({ ...current, registrations: current.registrations.map(row => row.id === updated.id ? updated : row) })); setMessage('Registration cancelled.'); }} /> : <AdministratorPanel summary={data.summary} platform={role === 'SUPER_ADMIN'} />}</>}</section>;
}
