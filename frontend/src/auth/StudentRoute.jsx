import { Navigate } from 'react-router';
import { useAuth } from './AuthContext';

export default function StudentRoute({ children }) {
  const { user } = useAuth();
  return user?.role === 'STUDENT' ? children : <Navigate to="/login" replace />;
}
