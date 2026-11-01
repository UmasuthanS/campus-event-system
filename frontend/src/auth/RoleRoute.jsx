import { Navigate, useLocation } from 'react-router';
import { useAuth } from './AuthContext';

export default function RoleRoute({ roles, children }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to={`/login?returnTo=${encodeURIComponent(location.pathname)}`} replace />;
  return roles.includes(user.role) ? children : <Navigate to="/unauthorized" replace />;
}
