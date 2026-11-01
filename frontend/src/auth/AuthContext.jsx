import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { apiRequest } from './api';
import { rolePages } from './roles';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // Access tokens stay in React memory. No cookies, localStorage, or sessionStorage.
  const [session, setSession] = useState(null);
  const logout = useCallback(() => setSession(null), []);
  useEffect(() => {
    if (!session) return;
    const timeout = setTimeout(logout, Math.max(0, Date.parse(session.expiresAt) - Date.now()));
    return () => clearTimeout(timeout);
  }, [session, logout]);

  async function authenticate(mode, fields) {
    const result = await apiRequest(`/auth/${mode}`, { method: 'POST', body: JSON.stringify(fields) });
    if (!Object.hasOwn(rolePages, result.user?.role) || !result.accessToken || !Number.isFinite(Date.parse(result.expiresAt))) {
      throw new Error('A valid account session is required.');
    }
    setSession(result);
    return result.user;
  }

  const request = useCallback(async (path, options = {}) => {
    try {
      return await apiRequest(path, { ...options, token: session?.accessToken });
    } catch (error) {
      if (error.status === 401) logout();
      throw error;
    }
  }, [session, logout]);

  return <AuthContext.Provider value={{ user: session?.user || null, authenticate, logout, request }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
