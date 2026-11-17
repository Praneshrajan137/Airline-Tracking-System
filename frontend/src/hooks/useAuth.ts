import { useState, useCallback } from 'react';

const API_BASE = '/api/v1';

export interface AuthUser {
  username: string;
  email: string;
}

export interface AuthError {
  error: string;
  message: string;
}

export function useAuth() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<AuthError | null>(null);

  const getToken = useCallback(() => {
    return localStorage.getItem('auth_token');
  }, []);

  const isAuthenticated = useCallback(() => {
    return !!getToken();
  }, [getToken]);

  const login = useCallback(async (username: string, password: string) => {
    setLoading(true);
    setError(null);

    try {
      const response = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });

      if (!response.ok) {
        const err: AuthError = await response.json().catch(() => ({
          error: 'Login failed',
          message: response.statusText,
        }));
        setError(err);
        return false;
      }

      const data = await response.json();
      localStorage.setItem('auth_token', data.token);
      setUser({ username: data.username, email: data.email });
      return true;
    } catch (e) {
      setError({
        error: 'Network error',
        message: 'Could not connect to the server.',
      });
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  const register = useCallback(async (username: string, email: string, password: string) => {
    setLoading(true);
    setError(null);

    try {
      const response = await fetch(`${API_BASE}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, email, password }),
      });

      if (!response.ok) {
        const err: AuthError = await response.json().catch(() => ({
          error: 'Registration failed',
          message: response.statusText,
        }));
        setError(err);
        return false;
      }

      const data = await response.json();
      localStorage.setItem('auth_token', data.token);
      setUser({ username: data.username, email: data.email });
      return true;
    } catch (e) {
      setError({
        error: 'Network error',
        message: 'Could not connect to the server.',
      });
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token');
    setUser(null);
  }, []);

  // Restore session on mount
  useState(() => {
    const token = localStorage.getItem('auth_token');
    if (token) {
      setUser({ username: 'user', email: '' });
    }
  });

  return { user, loading, error, login, register, logout, isAuthenticated, getToken };
}
