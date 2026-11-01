import { useRef, useState } from 'react';
import { useAuth } from '../auth/AuthContext';

export default function CancelRegistrationButton({ registration, onCancelled }) {
  const { request } = useAuth();
  const lock = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function cancel() {
    if (lock.current) return;
    lock.current = true;
    if (!window.confirm(`Cancel your registration for “${registration.event?.title || 'this event'}”?`)) { lock.current = false; return; }
    setBusy(true); setError('');
    try { onCancelled(await request(`/registrations/${registration.id}`, { method: 'DELETE' })); }
    catch (failure) { setError(failure.message); }
    finally { lock.current = false; setBusy(false); }
  }
  return <div>{registration.canCancel && <button className="button button-outline" disabled={busy} onClick={cancel}>{busy ? 'Cancelling…' : 'Cancel registration'}</button>}{error && <p className="form-error" role="alert">{error}</p>}</div>;
}
