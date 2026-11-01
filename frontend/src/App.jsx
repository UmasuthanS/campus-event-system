import { lazy, Suspense, useEffect, useRef } from 'react';
import { BrowserRouter, Link, Route, Routes, useLocation } from 'react-router';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import Home from './pages/Home';
import Events from './pages/Events';
import EventDetails from './pages/EventDetails';
import Clubs from './pages/Clubs';
import ClubProfile from './pages/ClubProfile';
import ClubManagement from './pages/ClubManagement';
import ClubEditor from './pages/ClubEditor';
import ClubAdministrators from './pages/ClubAdministrators';
import EventManagement from './pages/EventManagement';
import EventEditor from './pages/EventEditor';
import AccountPage from './pages/AccountPage';
import Dashboard from './pages/Dashboard';
import MyEvents from './pages/MyEvents';
import MyApplications from './pages/MyApplications';
import Unauthorized from './pages/Unauthorized';
import { AuthProvider } from './auth/AuthContext';
import RoleRoute from './auth/RoleRoute';
import { rolePages } from './auth/roles';

const Calendar = lazy(() => import('./pages/Calendar'));

function Layout() {
  const { pathname, hash } = useLocation();
  const main = useRef(null);
  useEffect(() => {
    const titles = { '/': 'Home', '/events': 'Events', '/calendar': 'Calendar', '/clubs': 'Clubs', '/login': 'Login', '/register': 'Register', '/student/dashboard': 'Student dashboard', '/student/my-events': 'My events', '/student/my-applications': 'My applications', '/club-admin/dashboard': 'Club Admin dashboard', '/super-admin/dashboard': 'Super Admin dashboard', '/super-admin/club-administrators': 'Club administrator assignments', '/unauthorized': 'Access denied', '/manage/clubs': 'Manage clubs', '/manage/clubs/new': 'Create club', '/manage/events': 'Manage events', '/manage/events/new': 'Create event' };
    document.title = `${titles[pathname] || (pathname.startsWith('/events/') ? 'Event details' : pathname.startsWith('/manage/events/') ? 'Edit event' : pathname.startsWith('/clubs/') ? 'Club profile' : pathname.startsWith('/manage/clubs/') ? 'Edit club' : 'Page not found')} | Ruhuna Campus Connect`;
    if (hash && document.getElementById(hash.slice(1))) {
      document.getElementById(hash.slice(1)).scrollIntoView();
    } else {
      window.scrollTo(0, 0);
      main.current?.focus({ preventScroll: true });
    }
  }, [pathname, hash]);
  return <><a className="skip-link" href="#main-content">Skip to content</a><Navbar /><main id="main-content" ref={main} tabIndex={-1}><Routes><Route path="/" element={<Home />} /><Route path="/events" element={<Events />} /><Route path="/events/:id" element={<EventDetails />} /><Route path="/calendar" element={<Suspense fallback={<p className="container" role="status">Loading calendar…</p>}><Calendar /></Suspense>} /><Route path="/clubs" element={<Clubs />} /><Route path="/clubs/:id" element={<ClubProfile />} /><Route path="/manage/clubs" element={<RoleRoute roles={['CLUB_ADMIN', 'SUPER_ADMIN']}><ClubManagement /></RoleRoute>} /><Route path="/manage/clubs/new" element={<RoleRoute roles={['SUPER_ADMIN']}><ClubEditor /></RoleRoute>} /><Route path="/manage/clubs/:id" element={<RoleRoute roles={['CLUB_ADMIN', 'SUPER_ADMIN']}><ClubEditor /></RoleRoute>} /><Route path="/manage/events" element={<RoleRoute roles={['CLUB_ADMIN', 'SUPER_ADMIN']}><EventManagement /></RoleRoute>} /><Route path="/manage/events/new" element={<RoleRoute roles={['CLUB_ADMIN', 'SUPER_ADMIN']}><EventEditor /></RoleRoute>} /><Route path="/manage/events/:id" element={<RoleRoute roles={['CLUB_ADMIN', 'SUPER_ADMIN']}><EventEditor /></RoleRoute>} /><Route path="/student/my-applications" element={<RoleRoute roles={['STUDENT']}><MyApplications /></RoleRoute>} /><Route path="/student/my-events" element={<RoleRoute roles={['STUDENT']}><MyEvents /></RoleRoute>} /><Route path="/login" element={<AccountPage key="login" />} /><Route path="/register" element={<AccountPage key="register" register />} />{Object.entries(rolePages).map(([role, page]) => <Route key={role} path={page.path} element={<RoleRoute roles={[role]}><Dashboard key={role} role={role} /></RoleRoute>} />)}<Route path="/super-admin/club-administrators" element={<RoleRoute roles={['SUPER_ADMIN']}><ClubAdministrators /></RoleRoute>} /><Route path="/unauthorized" element={<Unauthorized />} /><Route path="*" element={<section className="container page-section"><p className="eyebrow">404</p><h1>That page is off campus.</h1><p className="intro">Let’s get you back to exploring.</p><Link className="button button-primary" to="/">Back to home ↗</Link></section>} /></Routes></main><Footer /></>;
}

export default function App() {
  return <BrowserRouter><AuthProvider><Layout /></AuthProvider></BrowserRouter>;
}
