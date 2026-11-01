import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { dashboardPath } from '../auth/roles';

export default function AccountPage({ register = false }) {
  const { user, authenticate } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const requested = params.get('returnTo') || '';
  const returnTo = /^\/events\/[0-9a-f]{24}$/i.test(requested) || ['/student/my-events', '/student/my-applications'].includes(requested) ? requested : '';
  const destination = account => account.role === 'STUDENT' && returnTo ? returnTo : dashboardPath(account.role);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [fields, setFields] = useState({});
  if (user) return <Navigate to={destination(user)} replace />;

  async function submit(event) {
    event.preventDefault();
    if (loading) return;
    setLoading(true);
    setError('');
    setFields({});
    try {
      const account = await authenticate(register ? 'register' : 'login', Object.fromEntries(new FormData(event.currentTarget)));
      navigate(destination(account), { replace: true });
    } catch (failure) {
      setError(failure.message);
      setFields(failure.fields || {});
    } finally {
      setLoading(false);
    }
  }

  function field(name, label, type = 'text', attributes = {}) {
    return <div className="form-field"><label htmlFor={name}>{label}</label><input id={name} name={name} type={type} required disabled={loading} aria-invalid={Boolean(fields[name])} aria-describedby={fields[name] ? `${name}-error` : undefined} {...attributes} />{fields[name] && <p id={`${name}-error`} className="field-error">{fields[name]}</p>}</div>;
  }

  return (
    <section className="container account-page">
      <div className="account-panel">
        <img src="/assets/university-logo.png" width="48" height="68" alt="University of Ruhuna crest" />
        <p className="eyebrow">Your campus community</p>
        <h1>{register ? 'Your next chapter starts here.' : 'Welcome back to campus.'}</h1>
        <p className="intro">{register ? 'Create your student account to join the campus community.' : 'Sign in with your email and password.'}</p>
        <form onSubmit={submit} aria-busy={loading}>
          {error && <div className="form-error" role="alert">{error}</div>}
          {register && field('fullName', 'Full name', 'text', { autoComplete: 'name', minLength: 2, maxLength: 100 })}
          {register && field('registrationNumber', 'University registration number', 'text', { autoComplete: 'off', placeholder: 'EG/2026/001', maxLength: 12 })}
          {field('email', 'Email address', 'email', { autoComplete: 'email', maxLength: 254 })}
          {field('password', 'Password', 'password', { autoComplete: register ? 'new-password' : 'current-password', minLength: register ? 12 : 1, maxLength: 72 })}
          {register && <p className="form-hint">Use EG/YYYY/NNN or EG/YYYY/NNNN for your registration number. Passwords need 12–72 characters, a letter, a number, and at most 72 UTF-8 bytes.</p>}
          <button className="button button-primary" type="submit" disabled={loading}>{loading ? 'Please wait…' : register ? 'Create student account' : 'Sign in'} <span aria-hidden="true">↗</span></button>
        </form>
        <p className="form-hint">For your security, your session lasts up to 15 minutes or when this page reloads.</p>
        <p className="account-switch">{register ? 'Already part of the community?' : 'New to Campus Connect?'} <Link to={`${register ? '/login' : '/register'}${returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : ''}`}>{register ? 'Sign in' : 'Create an account'}</Link></p>
      </div>
      <aside className="account-aside"><span className="eyebrow">Ruhuna · Campus Connect</span><p>Curiosity brings us here.<br /><em>Community makes it home.</em></p><span>University of Ruhuna<br />Faculty of Engineering</span></aside>
    </section>
  );
}
