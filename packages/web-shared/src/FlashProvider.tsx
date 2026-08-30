import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

type FlashVariant = 'info' | 'error';

interface FlashState {
  show: (message: string, variant?: FlashVariant) => void;
}

const FlashContext = createContext<FlashState | null>(null);

export function FlashProvider({ children }: { children: ReactNode }) {
  const [flash, setFlash] = useState<{ message: string; variant: FlashVariant } | null>(null);

  const show = useCallback((message: string, variant: FlashVariant = 'info') => {
    setFlash({ message, variant });
    window.setTimeout(() => setFlash(null), 4000);
  }, []);

  const value = useMemo(() => ({ show }), [show]);

  return (
    <FlashContext.Provider value={value}>
      {flash && (
        <div className={`flash-banner flash-banner--${flash.variant}`} role="status">
          {flash.message}
        </div>
      )}
      {children}
    </FlashContext.Provider>
  );
}

export function useFlash() {
  const ctx = useContext(FlashContext);
  if (!ctx) throw new Error('useFlash outside FlashProvider');
  return ctx;
}
