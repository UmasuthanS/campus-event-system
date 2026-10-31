import { useState } from 'react';
import { Link, NavLink } from 'react-router';

const links = [['/', 'Home'], ['/events', 'Events'], ['/calendar', 'Calendar'], ['/clubs', 'Clubs'], ['/login', 'Login'], ['/register', 'Register']];

export default function Navbar() {
  const [open, setOpen] = useState(false);
  return (
    <header>
      <div className="university-bar"><div className="container">University of Ruhuna <span>Faculty of Engineering</span></div></div>
      <div className="container navigation">
        <Link className="brand" to="/" onClick={() => setOpen(false)} aria-label="Ruhuna Campus home">
          <img src="/assets/university-logo.png" width="38" height="54" alt="University of Ruhuna crest" />
          <span>RUHUNA<span className="brand-subtitle">CAMPUS CONNECT</span></span>
        </Link>
        <button className="menu-toggle" type="button" aria-expanded={open} aria-controls="main-navigation" onClick={() => setOpen(!open)}>{open ? 'Close ✕' : 'Menu ☰'}</button>
        <nav id="main-navigation" aria-label="Main navigation" className={open ? 'nav-links is-open' : 'nav-links'}>
          {links.map(([path, label]) => <NavLink key={path} to={path} end={path === '/'} className={path === '/register' ? 'nav-register' : undefined} onClick={() => setOpen(false)}>{label}</NavLink>)}
        </nav>
      </div>
    </header>
  );
}
