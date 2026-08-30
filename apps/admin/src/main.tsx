import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider, I18nProvider, FlashProvider, LocaleSync } from '@ride-app/web-shared';
import '@ride-app/web-shared/src/styles.css';
import App from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <I18nProvider>
        <FlashProvider>
          <AuthProvider storageKey="ride_admin_token">
            <LocaleSync />
            <App />
          </AuthProvider>
        </FlashProvider>
      </I18nProvider>
    </BrowserRouter>
  </StrictMode>,
);
