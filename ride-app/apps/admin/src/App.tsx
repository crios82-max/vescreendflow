import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth, useI18n } from '@ride-app/web-shared';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { user, loading, logout } = useAuth();
  const { t } = useI18n();
  if (loading) return <div className="auth-page">{t('common.loading')}</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (!user.isAdmin) {
    return (
      <div className="admin-page">
        <h1>{t('common.accessDenied')}</h1>
        <p className="error-text">{t('common.noAdminAccess')}</p>
        <button className="btn-secondary" type="button" onClick={logout}>{t('common.logout')}</button>
      </div>
    );
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<PrivateRoute><Dashboard /></PrivateRoute>} />
    </Routes>
  );
}
