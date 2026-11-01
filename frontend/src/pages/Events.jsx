import { Link } from 'react-router';
import PageHeading from '../components/PageHeading';
import EventCard from '../components/EventCard';
import EventFilters from '../components/EventFilters';
import { useEventDiscovery, usePublicData } from '../events/discovery';

export default function Events() {
  const discovery = useEventDiscovery();
  const events = usePublicData(`/events?${discovery.query}`);
  const error = events.error || discovery.clubs.error || discovery.categories.error;
  return <section className="container page-section"><PageHeading eyebrow="Explore & connect" title="What’s happening on campus">Discover published events from the Faculty of Engineering. All times are shown in Asia/Colombo.</PageHeading><Link className="text-link" to={`/calendar?${discovery.query}`}>Explore the interactive calendar ↗</Link><EventFilters discovery={discovery} />{error && <div className="form-error" role="alert">{error} <button className="text-link" onClick={() => { if (events.error) events.retry(); if (discovery.clubs.error) discovery.clubs.retry(); if (discovery.categories.error) discovery.categories.retry(); }}>Try again</button></div>}{events.loading && <p role="status">Loading published events…</p>}{!events.loading && events.data && <p className="form-hint" role="status">{events.data.length} published {events.data.length === 1 ? 'event' : 'events'}</p>}{events.data?.length === 0 && <p className="empty-state">No published events match your filters. Try another club, category, or date period.</p>}<div className="card-grid">{events.data?.map(event => <EventCard key={event.id} event={event} club={discovery.clubs.data?.find(club => club.id === event.clubId)} />)}</div></section>;
}
