import { useSearchParams } from 'react-router';
import PageHeading from '../components/PageHeading';
import DemoNotice from '../components/DemoNotice';
import EventCard from '../components/EventCard';
import { events, clubs } from '../data/demo';

export default function Events() {
  const [params, setParams] = useSearchParams();
  const selectedClub = params.get('club') || '';
  const visibleEvents = events.filter(event => !selectedClub || event.club === selectedClub);
  return <section className="container page-section"><PageHeading eyebrow="Explore & connect" title="What’s happening on campus">From a new skill to a new connection, your next experience starts here.</PageHeading><DemoNotice /><div className="filter-bar"><label htmlFor="club-filter">Browse by club</label><select id="club-filter" value={selectedClub} onChange={e => setParams(e.target.value ? { club: e.target.value } : {})}><option value="">All clubs</option>{!clubs.some(club => club.name === selectedClub) && selectedClub && <option value={selectedClub}>{selectedClub}</option>}{clubs.map(club => <option key={club.name}>{club.name}</option>)}</select><span aria-live="polite">{visibleEvents.length} demo {visibleEvents.length === 1 ? 'event' : 'events'}</span></div><div className="card-grid">{visibleEvents.map(event => <EventCard key={event.id} event={event} />)}</div>{visibleEvents.length === 0 && <p className="empty-state">No demo events for this club. Choose “All clubs” to explore the sample lineup.</p>}</section>;
}
