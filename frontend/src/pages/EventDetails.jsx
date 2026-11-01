import { Link, useParams } from 'react-router';
import PageHeading from '../components/PageHeading';
import OrganizerApplications, { OrganizingTeam } from '../organizers/OrganizerApplications';
import EventRsvp from '../registrations/EventRsvp';
import { usePublicData, dateText, clubColor } from '../events/discovery';

export default function EventDetails() {
  const { id } = useParams();
  const result = usePublicData(`/events/${id}`);
  const clubs = usePublicData('/clubs');
  const event = result.data;
  const club = clubs.data?.find(item => item.id === event?.clubId);
  return <section className="container page-section"><Link className="text-link" to="/events">← Discover events</Link>{result.loading && <p role="status">Loading event details…</p>}{result.error && <div className="form-error" role="alert">{result.error} <button className="text-link" onClick={result.retry}>Try again</button></div>}{clubs.error && <p className="form-error" role="alert">Club information is unavailable. <button className="text-link" onClick={clubs.retry}>Try again</button></p>}{event && <><PageHeading eyebrow={`${club?.shortName || 'Campus club'} · ${event.category || 'Campus event'}`} title={event.title}>Published campus event · Times in Asia/Colombo</PageHeading><div className="event-detail-panel" style={{ borderTopColor: clubColor(club) }}><p className="club-description">{event.description}</p><dl className="profile-details"><div><dt>Club</dt><dd>{club ? <Link className="text-link" to={`/clubs/${club.id}`}>{club.name}</Link> : 'Campus club'}</dd></div><div><dt>Starts</dt><dd><time dateTime={event.startAt}>{dateText(event.startAt)}</time></dd></div><div><dt>Ends</dt><dd><time dateTime={event.endAt}>{dateText(event.endAt)}</time></dd></div><div><dt>Venue</dt><dd>{event.venue}</dd></div><div><dt>Status</dt><dd>{event.status}</dd></div><div><dt>Capacity</dt><dd>{event.capacity ?? 'Not specified'}</dd></div><div><dt>Registration deadline</dt><dd>{dateText(event.registrationDeadline)}</dd></div></dl><EventRsvp event={event} onChanged={result.retry} /><OrganizerApplications event={event} onChanged={result.retry} /><OrganizingTeam eventId={event.id} /></div><Link className="button button-outline" to="/calendar">View the calendar ↗</Link></>}</section>;
}
