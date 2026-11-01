import { Link } from 'react-router';
import { clubColor, dateText } from '../events/discovery';

export default function EventCard({ event, club }) {
  return <article className="event-card" style={{ borderTop: `3px solid ${clubColor(club)}` }}><div className="event-card-top"><time className="date-block" dateTime={event.startAt}><span>{dateText(event.startAt, { month: 'short' })}</span><strong>{dateText(event.startAt, { day: '2-digit' })}</strong></time><span className="tag">{event.category || 'Campus event'}</span></div><p className="eyebrow">{club?.shortName || 'Campus club'} <span className="demo-label">{event.status}</span></p><h3><Link to={`/events/${event.id}`}>{event.title} <span aria-hidden="true">↗</span></Link></h3><p className="card-description">{event.description}</p><div className="event-meta"><span>{dateText(event.startAt)} · Sri Lanka time</span><span>{event.venue}</span></div></article>;
}
