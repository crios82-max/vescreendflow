import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import type { User } from '@ride-app/shared';
import { api } from './api';

interface AuthState {
  user: User | null;
  token: string | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (data: Record<string, string>) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

const DEFAULT_TOKEN_KEY = 'ride_token';

export function AuthProvider({
  children,
  storageKey = DEFAULT_TOKEN_KEY,
}: {
  children: ReactNode;
  storageKey?: string;
}) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(localStorage.getItem(storageKey));
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) {
      setLoading(false);
      return;
    }
    api.setToken(token);
    api.getMe()
      .then((data) => setUser(data.user))
      .catch(() => {
        localStorage.removeItem(storageKey);
        setToken(null);
      })
      .finally(() => setLoading(false));
  }, [token, storageKey]);

  const login = async (email: string, password: string) => {
    const data = await api.login(email, password);
    localStorage.setItem(storageKey, data.token);
    api.setToken(data.token);
    setToken(data.token);
    setUser(data.user);
  };

  const register = async (body: Record<string, string>) => {
    const data = await api.register(body);
    localStorage.setItem(storageKey, data.token);
    api.setToken(data.token);
    setToken(data.token);
    setUser(data.user);
  };

  const logout = () => {
    localStorage.removeItem(storageKey);
    api.setToken(null);
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside provider');
  return ctx;
}
