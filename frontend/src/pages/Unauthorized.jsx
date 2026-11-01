import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { dashboardPath } from '../auth/roles';
import PageHeading from '../components/PageHeading';

export default function Unauthorized() {
  const { user } = useAuth();
  return <section className="container page-section"><PageHeading eyebrow="403 · Access denied" title="This area needs a different permission.">Your account does not have access to this page.</PageHeading><Link className="button button-primary" to={user ? dashboardPath(user.role) : '/login'}>{user ? 'Back to your dashboard' : 'Go to login'} ↗</Link></section>;
}
