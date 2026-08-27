import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '@ride-app/web-shared';
import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="auth-page">Cargando...</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'driver') return <div className="auth-page">Esta app es solo para conductores</div>;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/" element={<PrivateRoute><Home /></PrivateRoute>} />
    </Routes>
  );
}
