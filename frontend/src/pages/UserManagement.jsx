import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import PageHeading from '../components/PageHeading';

export default function UserManagement() {
  const { request } = useAuth();
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setData(null); setError('');
    request(`/super-admin/users?${new URLSearchParams({ search: query, page })}`).then(result => { if (active) setData(result); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [request, query, page, version]);
  return <section className="container page-section"><PageHeading eyebrow="Super Admin · Users" title="Campus user directory">Find accounts and manage club access through administrator assignments.</PageHeading><div className="hero-actions"><Link className="text-link" to="/super-admin/dashboard">← Dashboard</Link><Link className="button button-primary" to="/super-admin/club-administrators">Manage club access ↗</Link></div><form className="filter-bar" onSubmit={event => { event.preventDefault(); setPage(0); setQuery(search.trim()); setVersion(value => value + 1); }}><label htmlFor="user-search">Name, email, or registration number</label><input id="user-search" maxLength={100} value={search} onChange={event => setSearch(event.target.value)} /><button className="button button-outline">Search</button></form>{error && <p className="form-error" role="alert">{error} <button className="text-link" onClick={() => setVersion(value => value + 1)}>Try again</button></p>}{!data && !error && <p role="status">Loading users…</p>}{data && <section className="participants-panel">{data.items.length ? <div className="table-scroll"><table><caption>User accounts · Page {page + 1}</caption><thead><tr><th>Name</th><th>Email</th><th>Registration number</th><th>Role</th></tr></thead><tbody>{data.items.map(user => <tr key={user.id}><td>{user.fullName}</td><td>{user.email}</td><td>{user.registrationNumber}</td><td>{user.role}</td></tr>)}</tbody></table></div> : <p className="empty-state">No matching user accounts.</p>}<div className="event-actions"><button className="button button-outline" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button><span>Page {page + 1}</span><button className="button button-outline" disabled={!data.hasNext} onClick={() => setPage(value => value + 1)}>Next</button></div></section>}</section>;
}
