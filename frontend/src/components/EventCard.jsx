import { Link, useLocation } from 'react-router';

export default function EventCard({ event }) {
  const { hash } = useLocation();
  return (
    <article className={hash === `#${event.id}` ? 'event-card is-selected' : 'event-card'} id={event.id}>
      <div className="event-card-top"><time className="date-block" dateTime={event.date}><span>{event.month}</span><strong>{event.day}</strong></time><span className="tag">{event.category}</span></div>
      <p className="eyebrow">{event.club} <span className="demo-label">Demo event</span></p>
      <h3><Link to={`/events#${event.id}`}>{event.title} <span aria-hidden="true">↗</span></Link></h3>
      <p className="card-description">{event.description}</p>
      <div className="event-meta"><span>{event.time}</span><span>{event.venue}</span></div>
    </article>
  );
}
