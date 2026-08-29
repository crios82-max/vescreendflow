import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth, useI18n } from '@ride-app/web-shared';
import Login from './pages/Login';
import Register from './pages/Register';
import Home from './pages/Home';
import Share from './pages/Share';
import SplitPay from './pages/SplitPay';
import ResetPassword from './pages/ResetPassword';
import Terms from './pages/Terms';
import Privacy from './pages/Privacy';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const { t } = useI18n();
  if (loading) return <div className="auth-page">{t('common.loading')}</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'passenger') return <div className="auth-page">{t('common.passengersOnly')}</div>;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/terms" element={<Terms />} />
      <Route path="/privacy" element={<Privacy />} />
      <Route path="/share/:token" element={<Share />} />
      <Route path="/split-pay/:token" element={<SplitPay />} />
      <Route path="/" element={<PrivateRoute><Home /></PrivateRoute>} />
    </Routes>
  );
}
