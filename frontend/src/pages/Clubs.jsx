import PageHeading from '../components/PageHeading';
import ClubCard from '../components/ClubCard';
import { clubs } from '../data/demo';

export default function Clubs() {
  return <section className="container page-section"><PageHeading eyebrow="Find your people" title="Built around shared passions">Meet IEEE, ComES, and AIESEC, our initial club lineup. Explore technology, computing, leadership, and community.</PageHeading><p className="demo-notice"><strong>Frontend preview</strong> Club descriptions are introductory sample copy. Club profiles and membership features will be added in a later stage.</p><div className="card-grid">{clubs.map(club => <ClubCard key={club.name} club={club} />)}</div></section>;
}
