import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import PageHeading from '../components/PageHeading';
import ClubForm from '../components/ClubForm';
import ClubAdminAssignments from '../components/ClubAdminAssignments';

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
  const platformAdmin = user.role === 'SUPER_ADMIN';
  useEffect(() => {
    let active = true;
    setData(null); setError(''); setErrors({}); setMessage(location.state?.message || '');
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
  return <section className="container page-section"><Link className="text-link" to="/manage/clubs">← Manage clubs</Link><PageHeading eyebrow="Club profile" title={id ? club?.name || 'Manage club' : 'Create a campus club'}>Profile changes are saved to the university club directory.</PageHeading>{error && <p className="form-error" role="alert">{error}</p>}{message && <p className="form-success" role="status">{message}</p>}{id && !data && !error && <p role="status">Loading club profile…</p>}{(!id || data) && <div className="club-editor-grid"><div>{readOnly && <p className="demo-notice">This club is inactive. A Super Admin must reactivate it before you can edit its profile.</p>}<ClubForm key={`${id || 'new'}-${club?.updatedAt || ''}`} initialValues={club} onSubmit={save} loading={busy} errors={errors} readOnly={readOnly} /></div>{platformAdmin && id && data && <aside className="club-admin-panel"><h2>Club status</h2><p className="intro">{club.status === 'ACTIVE' ? 'Active clubs appear in public discovery.' : 'Inactive clubs are hidden from public discovery. Their records are retained.'}</p><button className="button button-outline" disabled={busy} onClick={() => perform(async () => { const updated = await request(`/super-admin/clubs/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status: club.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' }) }); setData(current => ({ ...current, club: updated })); }, club.status === 'ACTIVE' ? 'Club deactivated.' : 'Club reactivated.')}>{club.status === 'ACTIVE' ? 'Deactivate club' : 'Reactivate club'}</button><ClubAdminAssignments clubId={id} admins={data.admins} active={club.status === 'ACTIVE'} disabled={busy} onBusyChange={setBusy} onUpdated={setData} /></aside>}</div>}</section>;
}
