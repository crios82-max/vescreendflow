import { useEffect } from 'react';
import { useAuth } from './AuthContext';
import { useI18n } from './I18nProvider';
import { api } from './api';

/** Syncs UI locale to the API when the user is logged in. */
export function LocaleSync() {
  const { user } = useAuth();
  const { locale } = useI18n();

  useEffect(() => {
    if (!user) return;
    void api.setPreferredLocale(locale);
  }, [user?.id, locale]);

  return null;
}
