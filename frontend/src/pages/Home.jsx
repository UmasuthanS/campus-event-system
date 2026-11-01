import { Link } from 'react-router';
import EventCard from '../components/EventCard';
import ClubCard from '../components/ClubCard';
import { usePublicData, dateText } from '../events/discovery';

export default function Home() {
  const upcoming = usePublicData('/events?period=upcoming&sort=asc');
  const directory = usePublicData('/clubs');
  const events = upcoming.data?.slice(0, 3) || [];
  const clubs = directory.data || [];
  const featured = events[0];
  const featuredClub = clubs.find(club => club.id === featured?.clubId);
  return (
    <>
      <section className="hero"><div className="container hero-grid">
        <div className="hero-copy"><p className="eyebrow"><span className="gold-line" /> Campus life, connected</p>
          <h1>Make room for<br />your next <em>great experience.</em></h1>
          <p className="hero-description">Discover new interests, meet your community, and find your place beyond the lecture hall.</p>
          <div className="hero-actions"><Link className="button button-primary" to="/events">Explore events <span aria-hidden="true">↗</span></Link><Link className="button button-outline" to="/clubs">Meet the clubs</Link></div>
          <p className="hero-caption">Campus Event Discovery &amp; RSVP System</p>
        </div>
        <div className="hero-feature">
          <div className="orbit orbit-one" aria-hidden="true" /><div className="orbit orbit-two" aria-hidden="true" />
          <div className="feature-heading"><span className="eyebrow">On the horizon</span><span className="feature-star" aria-hidden="true">✳</span></div>
          <div className="feature-content">{featured && <span className="feature-date">{dateText(featured.startAt, { day: '2-digit' })} <small>{dateText(featured.startAt, { month: 'long', year: 'numeric' })}</small></span>}<span className="feature-category">{featured ? `${featuredClub?.shortName || 'Campus'} · ${featured.category || 'Event'}` : 'CAMPUS LIFE'}</span><h2>{featured?.title || 'Your next great experience.'}</h2><p>{featured ? dateText(featured.startAt) + ' · Sri Lanka time' : 'Explore the calendar and find your next connection.'}</p></div>
          <div className="feature-bottom"><span>{featured ? 'Published campus event' : 'A campus of possibilities'}</span><Link to={featured ? `/events/${featured.id}` : '/calendar'} aria-label={featured ? `View ${featured.title}` : 'Explore the campus calendar'}>↗</Link></div>
        </div>
      </div></section>
      <section className="section container" aria-labelledby="upcoming-title"><div className="section-heading"><div><p className="eyebrow">Find your next moment</p><h2 id="upcoming-title">Coming up on campus</h2></div><Link className="text-link" to="/events">All events <span aria-hidden="true">↗</span></Link></div>{upcoming.loading && <p role="status">Loading upcoming events…</p>}{upcoming.error && <div className="form-error" role="alert">{upcoming.error} <button className="text-link" onClick={upcoming.retry}>Try again</button></div>}{upcoming.data?.length === 0 && <p className="empty-state">No upcoming published events yet. Check back soon.</p>}<div className="card-grid">{events.map(event => <EventCard key={event.id} event={event} club={clubs.find(club => club.id === event.clubId)} />)}</div></section>
      <section className="clubs-section"><div className="section container" aria-labelledby="clubs-title"><div className="section-heading"><div><p className="eyebrow">People. Passion. Possibility.</p><h2 id="clubs-title">Find your community</h2></div><Link className="text-link" to="/clubs">Meet the clubs <span aria-hidden="true">↗</span></Link></div><p className="section-description">Big ideas start with people who share your interests. Get to know our initial club lineup.</p>{directory.loading && <p role="status">Loading campus clubs…</p>}{directory.error && <div className="form-error" role="alert">{directory.error} <button className="text-link" onClick={directory.retry}>Try again</button></div>}{directory.data?.length === 0 && <p className="empty-state">No active clubs are listed yet.</p>}<div className="card-grid">{clubs.slice(0, 3).map(club => <ClubCard key={club.id} club={club} />)}</div></div></section>
      <section className="container closing-banner"><div><p className="eyebrow">Your campus chapter</p><h2>There’s more to university.<br />Go discover it.</h2></div><Link className="button button-gold" to="/calendar">Explore the calendar <span aria-hidden="true">↗</span></Link></section>
    </>
  );
}
