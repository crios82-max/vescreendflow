import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '@ride-app/web-shared';
import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';
import Share from './pages/Share';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <div className="auth-page">Cargando...</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'passenger') return <div className="auth-page">Esta app es solo para pasajeros</div>;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/share/:token" element={<Share />} />
      <Route path="/" element={<PrivateRoute><Home /></PrivateRoute>} />
    </Routes>
  );
}
