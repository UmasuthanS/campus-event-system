import { Link } from 'react-router';

export default function ClubCard({ club }) {
  return (
    <article className="club-card">
      <div className="club-card-top"><span className="club-mark" aria-hidden="true">{club.name}</span><span className="club-number" aria-hidden="true">{club.mark}</span></div>
      <p className="eyebrow">{club.category}</p><h3>{club.name}</h3>
      <p className="card-description">{club.description}</p>
      <Link className="text-link" to={`/events?club=${encodeURIComponent(club.name)}`}>Explore demo events <span aria-hidden="true">↗</span></Link>
    </article>
  );
}
