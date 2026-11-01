import { useCallback, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import listPlugin from '@fullcalendar/list';
import luxonPlugin from '@fullcalendar/luxon3';
import PageHeading from '../components/PageHeading';
import EventFilters from '../components/EventFilters';
import { TIME_ZONE, clubColor, dateText, useEventDiscovery, usePublicData } from '../events/discovery';

const plugins = [dayGridPlugin, timeGridPlugin, listPlugin, luxonPlugin];
export default function Calendar() {
  const navigate = useNavigate();
  const discovery = useEventDiscovery();
  const [range, setRange] = useState(null);
  const [initialView] = useState(() => window.matchMedia('(max-width: 600px)').matches ? 'listMonth' : 'dayGridMonth');
  const datesSet = useCallback(info => {
    const next = { from: info.start.toISOString(), to: info.end.toISOString() };
    setRange(current => current?.from === next.from && current?.to === next.to ? current : next);
  }, []);
  const query = new URLSearchParams(discovery.query);
  if (range) { query.set('from', range.from); query.set('to', range.to); }
  const data = usePublicData(range ? `/events?${query}` : null);
  const events = useMemo(() => (data.data || []).filter(event => event.startAt).map(event => {
    const club = discovery.clubs.data?.find(club => club.id === event.clubId);
    const color = clubColor(club);
    return { id: event.id, title: `${club?.shortName || 'Campus'} · ${event.title}`, start: event.startAt, end: event.endAt, url: `/events/${event.id}`, backgroundColor: color, borderColor: color, textColor: '#ffffff', extendedProps: { venue: event.venue } };
  }), [data.data, discovery.clubs.data]);
  const error = data.error || discovery.clubs.error || discovery.categories.error;
  return <section className="container page-section"><PageHeading eyebrow="Make time for campus life" title="Your campus calendar">Explore month, week, and list views. Event dates and times use Asia/Colombo (UTC+05:30).</PageHeading><Link className="text-link" to={`/events?${discovery.query}`}>Browse event cards ↗</Link><EventFilters discovery={discovery} calendar /><div className="calendar-legend">{discovery.clubs.data?.map(club => <span key={club.id}><i style={{ background: clubColor(club) }} aria-hidden="true" />{club.shortName}</span>)}</div>{error && <div className="form-error" role="alert">{error} <button className="text-link" onClick={() => { if (data.error) data.retry(); if (discovery.clubs.error) discovery.clubs.retry(); if (discovery.categories.error) discovery.categories.retry(); }}>Try again</button></div>}{data.loading && <p role="status">Loading calendar events…</p>}{data.data?.length === 0 && <p className="empty-state">No published events in this range match your filters.</p>}<div className="campus-calendar" aria-label="Published campus event calendar" aria-busy={data.loading}><FullCalendar plugins={plugins} timeZone={TIME_ZONE} initialView={initialView} datesSet={datesSet} events={events} height="auto" eventDisplay="block" firstDay={1} nowIndicator dayMaxEvents={3} displayEventEnd eventInteractive eventTimeFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }} headerToolbar={{ left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,listMonth' }} buttonText={{ today: 'Today', month: 'Month', week: 'Week', list: 'List' }} eventClick={info => { info.jsEvent.preventDefault(); navigate(`/events/${info.event.id}`); }} eventDidMount={info => { info.el.setAttribute('aria-label', `${info.event.title}, ${dateText(info.event.start)}, ${info.event.extendedProps.venue || ''}`); }} /></div></section>;
}
