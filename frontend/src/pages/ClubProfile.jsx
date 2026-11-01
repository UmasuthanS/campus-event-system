import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { apiRequest } from '../auth/api';
import PageHeading from '../components/PageHeading';

export default function ClubProfile() {
  const { id } = useParams();
  const [club, setClub] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setClub(null); setError('');
    apiRequest(`/clubs/${id}`).then(data => { if (active) setClub(data); }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [id]);
  return <section className="container page-section"><Link className="text-link" to="/clubs">← All clubs</Link>{error && <p className="form-error" role="alert">{error}</p>}{!club && !error && <p role="status">Loading club profile…</p>}{club && <><PageHeading eyebrow={club.shortName} title={club.name}>An active campus community at the University of Ruhuna.</PageHeading>{club.logoUrl && <img className="club-profile-logo" src={club.logoUrl} alt={`${club.shortName} logo`} onError={event => { event.currentTarget.hidden = true; }} />}<p className="club-description">{club.description}</p>{club.contactEmail && <p className="intro">Contact: <a className="text-link" href={`mailto:${club.contactEmail}`}>{club.contactEmail}</a></p>}</>}</section>;
}
