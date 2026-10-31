import { Link } from 'react-router';
import EventCard from '../components/EventCard';
import ClubCard from '../components/ClubCard';
import DemoNotice from '../components/DemoNotice';
import { events, clubs } from '../data/demo';

export default function Home() {
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
          <div className="feature-content"><span className="feature-date">12 <small>NOVEMBER / 2026</small></span><span className="feature-category">IEEE · WORKSHOP</span><h2>Ideas into<br /><em>impact.</em></h2><p>A little curiosity.<br />A whole world of possibilities.</p></div>
          <div className="feature-bottom"><span>Temporary demo event</span><Link to="/events#innovation-workshop" aria-label="Explore Ideas into impact demo event">↗</Link></div>
        </div>
      </div></section>
      <section className="section container" aria-labelledby="upcoming-title"><div className="section-heading"><div><p className="eyebrow">Find your next moment</p><h2 id="upcoming-title">Coming up on campus</h2></div><Link className="text-link" to="/events">All events <span aria-hidden="true">↗</span></Link></div><DemoNotice /><div className="card-grid">{events.map(event => <EventCard key={event.id} event={event} />)}</div></section>
      <section className="clubs-section"><div className="section container" aria-labelledby="clubs-title"><div className="section-heading"><div><p className="eyebrow">People. Passion. Possibility.</p><h2 id="clubs-title">Find your community</h2></div><Link className="text-link" to="/clubs">Meet the clubs <span aria-hidden="true">↗</span></Link></div><p className="section-description">Big ideas start with people who share your interests. Get to know our initial club lineup.</p><div className="card-grid">{clubs.map(club => <ClubCard key={club.name} club={club} />)}</div></div></section>
      <section className="container closing-banner"><div><p className="eyebrow">Your campus chapter</p><h2>There’s more to university.<br />Go discover it.</h2></div><Link className="button button-gold" to="/calendar">Explore the calendar <span aria-hidden="true">↗</span></Link></section>
    </>
  );
}
