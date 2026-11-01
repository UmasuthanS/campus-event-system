import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';

export default function ClubAdminAssignments({ clubId, admins, active, onUpdated, disabled = false, onBusyChange }) {
  const { request } = useAuth();
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [fieldError, setFieldError] = useState('');
  const inputId = `admin-email-${clubId}`;
  async function perform(action, success) {
    if (busy || disabled) return;
    setBusy(true); onBusyChange?.(true); setError(''); setMessage(''); setFieldError('');
    try { onUpdated(await action()); setMessage(success); }
    catch (failure) { setError(failure.message); setFieldError(failure.fields?.email || ''); }
    finally { setBusy(false); onBusyChange?.(false); }
  }
  return <section aria-label="Club administrator assignments"><h2>Club administrators</h2><p className="form-hint">Assign an existing account by email. Multiple admins can share a club. Newly promoted admins must sign in again.</p>{!active && <p className="demo-notice">Reactivate this club before assigning another administrator.</p>}{error && <p className="form-error" role="alert">{error}</p>}{message && <p className="form-success" role="status">{message}</p>}<form onSubmit={event => { event.preventDefault(); perform(async () => { const result = await request(`/super-admin/clubs/${clubId}/admins`, { method: 'PUT', body: JSON.stringify({ email }) }); setEmail(''); return result; }, 'Club administrator assigned.'); }}><div className="form-field"><label htmlFor={inputId}>Account email</label><input id={inputId} type="email" required maxLength={254} value={email} disabled={busy || disabled || !active} aria-invalid={Boolean(fieldError)} aria-describedby={fieldError ? `${inputId}-error` : undefined} onChange={event => setEmail(event.target.value)} />{fieldError && <p id={`${inputId}-error`} className="field-error">{fieldError}</p>}</div><button className="button button-primary" disabled={busy || disabled || !active} type="submit">{busy ? 'Please wait…' : 'Assign admin'}</button></form><ul className="assigned-admins">{admins.map(admin => <li key={admin.id}><div><strong>{admin.fullName}</strong><span>{admin.email || admin.id}</span></div><button className="text-link" disabled={busy || disabled} aria-label={`Revoke access for ${admin.email || admin.fullName}`} onClick={() => perform(() => request(`/super-admin/clubs/${clubId}/admins/${admin.id}`, { method: 'DELETE' }), 'Club administrator access revoked.')}>Revoke</button></li>)}</ul>{admins.length === 0 && <p className="form-hint">No club administrators assigned.</p>}</section>;
}
