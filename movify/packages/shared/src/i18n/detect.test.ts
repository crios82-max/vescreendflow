import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { detectLocaleFromLanguageTag, resolveLocaleFromPlace, isLocale, translateApiError } from './index.js';
import { localeFromCountry } from './regions.js';

describe('i18n locale detection', () => {
  it('maps countries to locales', () => {
    assert.equal(localeFromCountry('VE'), 'es');
    assert.equal(localeFromCountry('BR'), 'pt');
    assert.equal(localeFromCountry('IT'), 'it');
    assert.equal(localeFromCountry('US'), 'en');
    assert.equal(localeFromCountry('xx'), null);
  });

  it('parses BCP-47 tags with region', () => {
    assert.equal(detectLocaleFromLanguageTag('pt-BR'), 'pt');
    assert.equal(detectLocaleFromLanguageTag('es-VE'), 'es');
    assert.equal(detectLocaleFromLanguageTag('en-US'), 'en');
    assert.equal(detectLocaleFromLanguageTag('it-IT'), 'it');
  });

  it('resolves place: country wins over timezone', () => {
    assert.equal(resolveLocaleFromPlace({ countryCode: 'BR', timeZone: 'America/Caracas' }), 'pt');
    assert.equal(resolveLocaleFromPlace({ timeZone: 'America/Sao_Paulo' }), 'pt');
    assert.equal(resolveLocaleFromPlace({ timeZone: 'Europe/Rome' }), 'it');
  });

  it('validates locale codes', () => {
    assert.equal(isLocale('es'), true);
    assert.equal(isLocale('pt'), true);
    assert.equal(isLocale('fr'), false);
  });

  it('translates API error messages', () => {
    assert.equal(translateApiError('en', 'Credenciales inválidas'), 'Invalid credentials');
    assert.equal(translateApiError('es', 'Credenciales inválidas'), 'Credenciales inválidas');
    assert.equal(translateApiError('en', 'Unknown error'), 'Unknown error');
  });
});
