import { Navigate } from 'react-router';
import { useAuth } from './AuthContext';

export default function RoleRoute({ roles, children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return roles.includes(user.role) ? children : <Navigate to="/unauthorized" replace />;
}
