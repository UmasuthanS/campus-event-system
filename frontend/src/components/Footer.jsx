import { Link } from 'react-router';

export default function Footer() {
  return (
    <footer className="footer">
      <div className="container footer-top">
        <div className="footer-intro"><p className="footer-wordmark">A campus of possibilities.</p><p>Campus Event Discovery &amp; RSVP System<br />University of Ruhuna · Faculty of Engineering</p></div>
        <nav aria-label="Footer navigation"><p className="eyebrow">Explore campus</p><Link to="/events">Events</Link><Link to="/calendar">Calendar</Link><Link to="/clubs">Student clubs</Link></nav>
        <nav aria-label="Account navigation"><p className="eyebrow">Stay connected</p><Link to="/login">Login</Link><Link to="/register">Register</Link><Link to="/">Back to home ↗</Link></nav>
      </div>
      <div className="container footer-bottom"><span>© {new Date().getFullYear()} Campus Connect</span><span>Event times are shown in Asia/Colombo.</span></div>
    </footer>
  );
}
