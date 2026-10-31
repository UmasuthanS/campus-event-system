import { useEffect, useRef } from 'react';
import { BrowserRouter, Link, Route, Routes, useLocation } from 'react-router';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import Home from './pages/Home';
import Events from './pages/Events';
import Calendar from './pages/Calendar';
import Clubs from './pages/Clubs';
import AccountPage from './pages/AccountPage';

function Layout() {
  const { pathname, hash } = useLocation();
  const main = useRef(null);
  useEffect(() => {
    const titles = { '/': 'Home', '/events': 'Events', '/calendar': 'Calendar', '/clubs': 'Clubs', '/login': 'Login', '/register': 'Register' };
    document.title = `${titles[pathname] || 'Page not found'} | Ruhuna Campus Connect`;
    if (hash && document.getElementById(hash.slice(1))) {
      document.getElementById(hash.slice(1)).scrollIntoView();
    } else {
      window.scrollTo(0, 0);
      main.current?.focus({ preventScroll: true });
    }
  }, [pathname, hash]);
  return <><a className="skip-link" href="#main-content">Skip to content</a><Navbar /><main id="main-content" ref={main} tabIndex={-1}><Routes><Route path="/" element={<Home />} /><Route path="/events" element={<Events />} /><Route path="/calendar" element={<Calendar />} /><Route path="/clubs" element={<Clubs />} /><Route path="/login" element={<AccountPage />} /><Route path="/register" element={<AccountPage register />} /><Route path="*" element={<section className="container page-section"><p className="eyebrow">404</p><h1>That page is off campus.</h1><p className="intro">Let’s get you back to exploring.</p><Link className="button button-primary" to="/">Back to home ↗</Link></section>} /></Routes></main><Footer /></>;
}

export default function App() {
  return <BrowserRouter><Layout /></BrowserRouter>;
}
