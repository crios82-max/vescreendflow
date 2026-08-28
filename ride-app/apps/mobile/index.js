import { registerRootComponent } from 'expo';
import App from './App';
import { MobileI18nProvider } from './src/i18n';

function Root() {
  return (
    <MobileI18nProvider>
      <App />
    </MobileI18nProvider>
  );
}

registerRootComponent(Root);
