import { Link } from 'react-router';

export default function ClubCard({ club }) {
  return <article className="club-card"><div className="club-card-top"><span className="club-mark">{club.logoUrl && <img src={club.logoUrl} alt="" loading="lazy" onError={event => { event.currentTarget.hidden = true; }} />}{club.shortName || club.name}</span>{club.mark && <span className="club-number" aria-hidden="true">{club.mark}</span>}</div><p className="eyebrow">{club.id ? 'Campus community' : 'Demo club'}</p><h3>{club.name}</h3><p className="card-description">{club.description}</p><Link className="text-link" to={club.id ? `/clubs/${club.id}` : `/events?club=${encodeURIComponent(club.name)}`}>{club.id ? 'View club profile' : 'Explore demo events'} <span aria-hidden="true">↗</span></Link></article>;
}
