import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import PageHeading from '../components/PageHeading';
import ClubForm from '../components/ClubForm';

export default function ClubEditor() {
  const { id } = useParams();
  const { user, request } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [data, setData] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [email, setEmail] = useState('');
  const platformAdmin = user.role === 'SUPER_ADMIN';
  useEffect(() => {
    let active = true;
    setData(null); setError(''); setErrors({}); setEmail(''); setMessage(location.state?.message || '');
    if (id) request(`/club-admin/clubs/${id}`).then(result => { if (active) setData(result); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [id, request, location.state]);

  async function perform(action, success) {
    if (busy) return;
    setBusy(true); setError(''); setErrors({}); setMessage('');
    try { await action(); setMessage(success); }
    catch (failure) { setError(failure.message); setErrors(failure.fields || {}); }
    finally { setBusy(false); }
  }
  async function save(values) {
    await perform(async () => {
      const club = await request(id ? `/club-admin/clubs/${id}` : '/super-admin/clubs', { method: id ? 'PUT' : 'POST', body: JSON.stringify(values) });
      if (!id) navigate(`/manage/clubs/${club.id}`, { replace: true, state: { message: 'Club created.' } });
      else setData(current => ({ ...current, club }));
    }, 'Club saved.');
  }
  const club = data?.club;
  const readOnly = !platformAdmin && club?.status === 'INACTIVE';
  return <section className="container page-section"><Link className="text-link" to="/manage/clubs">← Manage clubs</Link><PageHeading eyebrow="Club profile" title={id ? club?.name || 'Manage club' : 'Create a campus club'}>Profile changes are saved to the university club directory.</PageHeading>{error && <p className="form-error" role="alert">{error}</p>}{message && <p className="form-success" role="status">{message}</p>}{id && !data && !error && <p role="status">Loading club profile…</p>}{(!id || data) && <div className="club-editor-grid"><div>{readOnly && <p className="demo-notice">This club is inactive. A Super Admin must reactivate it before you can edit its profile.</p>}<ClubForm key={`${id || 'new'}-${club?.updatedAt || ''}`} initialValues={club} onSubmit={save} loading={busy} errors={errors} readOnly={readOnly} /></div>{platformAdmin && id && data && <aside className="club-admin-panel"><h2>Club status</h2><p className="intro">{club.status === 'ACTIVE' ? 'Active clubs appear in public discovery.' : 'Inactive clubs are hidden from public discovery. Their records are retained.'}</p><button className="button button-outline" disabled={busy} onClick={() => perform(async () => { const updated = await request(`/super-admin/clubs/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status: club.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' }) }); setData(current => ({ ...current, club: updated })); }, club.status === 'ACTIVE' ? 'Club deactivated.' : 'Club reactivated.')}>{club.status === 'ACTIVE' ? 'Deactivate club' : 'Reactivate club'}</button><h2>Club administrators</h2><p className="form-hint">Assign an existing account by email. Newly promoted admins must sign in again to refresh their role.</p><form onSubmit={event => { event.preventDefault(); perform(async () => { setData(await request(`/super-admin/clubs/${id}/admins`, { method: 'PUT', body: JSON.stringify({ email }) })); setEmail(''); }, 'Club admin assigned.'); }}><div className="form-field"><label htmlFor="admin-email">Account email</label><input id="admin-email" type="email" required value={email} disabled={busy || club.status !== 'ACTIVE'} onChange={event => setEmail(event.target.value)} />{errors.email && <p className="field-error">{errors.email}</p>}</div><button className="button button-primary" disabled={busy || club.status !== 'ACTIVE'} type="submit">Assign admin</button></form><ul className="assigned-admins">{data.admins.map(admin => <li key={admin.id}><div><strong>{admin.fullName}</strong><span>{admin.email || admin.id}</span></div><button className="text-link" disabled={busy} aria-label={`Revoke access for ${admin.email || admin.fullName}`} onClick={() => perform(async () => setData(await request(`/super-admin/clubs/${id}/admins/${admin.id}`, { method: 'DELETE' })), 'Club admin access revoked.')}>Revoke</button></li>)}</ul>{data.admins.length === 0 && <p className="form-hint">No club administrators assigned.</p>}</aside>}</div>}</section>;
}
