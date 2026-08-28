import { BRAND } from '@ride-app/shared';

type BrandMarkProps = {
  size?: 'sm' | 'md' | 'lg';
  showTagline?: boolean;
  variant?: 'icon' | 'wordmark';
};

const iconSizes = { sm: 28, md: 40, lg: 56 } as const;
const wordmarkHeights = { sm: 32, md: 40, lg: 52 } as const;

export function BrandMark({ size = 'md', showTagline = false, variant }: BrandMarkProps) {
  const useWordmark = variant === 'wordmark' || (variant !== 'icon' && size === 'lg' && showTagline);

  if (useWordmark) {
    return (
      <div className="brand-mark" style={{ display: 'grid', gap: showTagline ? 8 : 0 }}>
        <img
          src="/logo.png"
          alt={BRAND.name}
          style={{ height: wordmarkHeights[size], width: 'auto', maxWidth: '100%', objectFit: 'contain' }}
        />
        {showTagline ? (
          <p style={{ margin: 0, color: '#aaa', fontSize: '0.95rem' }}>{BRAND.tagline}</p>
        ) : null}
      </div>
    );
  }

  const px = iconSizes[size];
  return (
    <div className="brand-mark" style={{ display: 'grid', gap: showTagline ? 4 : 0 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <img
          src="/favicon.png"
          alt=""
          width={px}
          height={px}
          style={{ borderRadius: px * 0.22 }}
        />
        <span
          style={{
            fontSize: size === 'lg' ? '1.75rem' : size === 'md' ? '1.5rem' : '1.15rem',
            fontWeight: 800,
            letterSpacing: '-0.02em',
          }}
        >
          {BRAND.name}
        </span>
      </div>
      {showTagline ? (
        <p style={{ margin: 0, color: '#aaa', fontSize: '0.95rem' }}>{BRAND.tagline}</p>
      ) : null}
    </div>
  );
}
