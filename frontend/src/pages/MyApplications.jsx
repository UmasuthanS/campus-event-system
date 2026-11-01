import PageHeading from '../components/PageHeading';
import OrganizerApplications from '../organizers/OrganizerApplications';

export default function MyApplications() {
  return <section className="container page-section"><PageHeading eyebrow="Student · Organizing" title="My organizer applications">Track decisions and withdraw eligible applications. Times use Asia/Colombo.</PageHeading><OrganizerApplications /></section>;
}
