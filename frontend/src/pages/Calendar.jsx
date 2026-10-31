import { Link } from 'react-router';
import PageHeading from '../components/PageHeading';
import DemoNotice from '../components/DemoNotice';
import { events } from '../data/demo';

export default function Calendar() {
  return <section className="container page-section"><PageHeading eyebrow="A little planning, a lot to discover" title="Your campus calendar">A first look at the month ahead. Full calendar features will arrive in a later stage.</PageHeading><DemoNotice /><div className="agenda"><div className="agenda-header"><h2>November 2026</h2><span className="tag">Demo agenda</span></div>{events.map(event => <article className="agenda-row" key={event.id}><time dateTime={event.date} className="date-block"><span>{event.month}</span><strong>{event.day}</strong></time><div><p className="eyebrow">{event.club} · {event.category}</p><h3><Link to={`/events#${event.id}`}>{event.title}</Link></h3><p>{event.time} · {event.venue}</p></div><Link className="agenda-link" to={`/events#${event.id}`} aria-label={`View ${event.title} demo event`}>↗</Link></article>)}</div></section>;
}
