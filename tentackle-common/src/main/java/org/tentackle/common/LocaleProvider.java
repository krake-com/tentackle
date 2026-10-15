/*
 * Tentackle - https://tentackle.org.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.common;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

interface LocaleProviderHolder {
  LocaleProvider INSTANCE = ServiceFactory.createService(LocaleProvider.class, LocaleProvider.class);
}

/**
 * A provider for {@link Locale}s.<br>
 * Supports thread-local locales and mapping of locales according to the application requirements.
 * <p>
 * The default implementation maps each locale to itself and supports only {@link Locale#ENGLISH},
 * which is also the fallback locale. Applications supporting more languages, especially for {@link I18NText},
 * must provide their own implementation via {@code @Service(LocaleProvider.class)} and override
 * {@link #getEffectiveLocale(Locale)}, {@link #getEffectiveLocales()} and {@link #isLocaleSupported(Locale)}.
 *
 * @author harald
 */
// @Service does not work in tentackle-common, since the analyze goal of the tentackle-maven-plugin is not available at this build step yet.
// Needs explicit config in src/main/resources/META-INF/services.
@Service(LocaleProvider.class)    // defaults to self
public class LocaleProvider {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static LocaleProvider getInstance() {
    return LocaleProviderHolder.INSTANCE;
  }


  /**
   * The thread-local locales.
   */
  private final ThreadLocal<Locale> tlLocale = new ThreadLocal<>();

  /**
   * The default supported effective locales.
   */
  private final List<Locale> EFFECTIVE_LOCALES = List.of(Locale.ENGLISH);

  /**
   * The initial locale when the provider was created.
   */
  private final Locale initialLocale;

  /**
   * The effective initial locale, created lazily.
   */
  private volatile Locale effectiveInitialLocale;

  /**
   * Map of locales to default currencies.
   */
  private final Map<Locale, Currency> currencyMap = new ConcurrentHashMap<>();


  /**
   * Creates a locale provider.<br>
   * The current default locale becomes the initial locale.
   */
  public LocaleProvider() {
    initialLocale = Locale.getDefault();
  }

  /**
   * Gets the initial locale.<br>
   * This is the JVM's default locale at the time the provider was created,
   * usually when it is used for the first time.
   *
   * @return the initial locale
   */
  public Locale getInitialLocale() {
    return initialLocale;
  }

  /**
   * Gets the effective initial locale.
   *
   * @return the effective initial locale
   * @see #getInitialLocale()
   * @see #getEffectiveLocale(Locale)
   */
  public Locale getEffectiveInitialLocale() {
    Locale locale = effectiveInitialLocale;
    if (locale == null) {
      locale = getEffectiveLocale(initialLocale);
      effectiveInitialLocale = locale;
    }
    return locale;
  }

  /**
   * Gets the locale used by the current thread.
   *
   * @return the locale, null if no thread-local Locale set
   * @see #getLocale()
   */
  public Locale getCurrentLocale() {
    return tlLocale.get();
  }

  /**
   * Gets the locale.
   * <p>
   * If there is no thread-local Locale, the default Locale is returned.
   *
   * @return the current locale, never null
   */
  public Locale getLocale() {
    Locale locale = getCurrentLocale();
    if (locale == null) {
      locale = Locale.getDefault();
    }
    return locale;
  }

  /**
   * Sets the locale used by the current thread.<br>
   * The locale is stored as {@link ThreadLocal}.
   *
   * @param locale the locale, null to remove the thread-local locale
   */
  public void setCurrentLocale(Locale locale) {
    if (locale == null) {
      tlLocale.remove();
    }
    else {
      tlLocale.set(locale);
    }
  }

  /**
   * Asserts that a thread-local Locale is set.
   *
   * @throws TentackleRuntimeException if the thread-local locale is null
   */
  public void assertCurrentLocaleValid() {
    if (getCurrentLocale() == null) {
      throw new TentackleRuntimeException("no thread-local Locale");
    }
  }


  /**
   * Gets the effective locale.<br>
   * This is application-specific and allows narrowing the locales to the effectively
   * supported locales. If, for example, the default language in all resource bundles
   * is "en" and there is a second language "de", then all locales beginning with "de"
   * will map to "de" and all others to "en".
   *
   * @param locale the requested locale
   * @return the mapped locale
   */
  public Locale getEffectiveLocale(Locale locale) {
    // the default just maps 1:1
    return locale;
  }

  /**
   * Gets the effective locale for the current locale.
   *
   * @return the mapped locale
   */
  public Locale getEffectiveLocale() {
    return getEffectiveLocale(getLocale());
  }


  /**
   * Returns the fallback locale.<br>
   * If there is no translation provided for a given locale, the resource bundles eventually
   * fall back to the "no-locale" translation.<br>
   * In most cases this corresponds to the "en"-locale ({@link Locale#ENGLISH}),
   * but applications can change this by overriding this method.
   * <p>
   * Important: the fallback-locale must be language-only! No country or variant!
   *
   * @return the fallback locale
   */
  public Locale getFallbackLocale() {
    return Locale.ENGLISH;
  }


  /**
   * Returns an unmodifiable list of all supported effective locales.<br>
   * The default is {@link Locale#ENGLISH} only.
   *
   * @return the possible effective locales
   */
  public List<Locale> getEffectiveLocales() {
    return EFFECTIVE_LOCALES;
  }

  /**
   * Returns whether the given locale is supported.<br>
   * The default implementation checks whether the locale is exactly one of the {@link #getEffectiveLocales()}.
   * Applications mapping wider locales to effective locales, such as {@code de_DE} to {@code de},
   * should override this method accordingly.
   *
   * @param locale the locale
   * @return true if locale is one of the effective locales
   */
  public boolean isLocaleSupported(Locale locale) {
    for (Locale effectiveLocale : getEffectiveLocales()) {
      if (effectiveLocale.equals(locale)) {
        return true;
      }
    }
    return false;
  }


  /**
   * Converts a locale to a tag.<br>
   * Since there is no guarantee that {@link Locale#toLanguageTag()} and {@link Locale#forLanguageTag(String)} will round-trip,
   * this method is provided to guarantee that at least for the supported locales.
   * The tag consists of the language, country and variant separated by underscores, such as {@code de_DE}
   * (not a BCP 47 language tag).
   *
   * @param locale the locale
   * @return the tag
   * @see #fromTag(String)
   */
  public String toTag(Locale locale) {
    StringBuilder buf = new StringBuilder();
    buf.append(locale.getLanguage());
    String str = locale.getCountry();
    if (!str.isEmpty()) {
      buf.append('_').append(str);
      str = locale.getVariant();
      if (!str.isEmpty()) {
        buf.append('_').append(str);
      }
    }
    return buf.toString();
  }

  /**
   * Converts a tag to a locale.<br>
   * The locale is not checked to be supported.
   *
   * @param tag the tag
   * @return the locale, null if the tag is empty or null
   * @see #toTag(Locale)
   */
  public Locale fromTag(String tag) {
    Locale locale = null;
    String language = null;
    String country = null;
    String variant = null;

    if (tag != null) {
      StringTokenizer stok = new StringTokenizer(tag, "_");
      while (stok.hasMoreTokens()) {
        String token = stok.nextToken();
        if (language == null) {
          language = token;
        }
        else if (country == null) {
          country = token;
        }
        else if (variant == null) {
          variant = token;
        }
      }

      if (variant != null) {
        locale = Locale.of(language, country, variant);
      }
      else if (country != null) {
        locale = Locale.of(language, country);
      }
      else if (language != null) {
        locale = Locale.of(language);
      }
    }

    return locale;
  }

  /**
   * Derives the currency from the given locale.<br>
   * If the locale does not specify a country, the country is derived from the system property {@code "user.country"}.
   *
   * @param locale the locale
   * @return the currency, never null
   * @throws IllegalArgumentException if no country could be determined or the country is not supported
   */
  public Currency getCurrency(Locale locale) {
    if (locale.getCountry().isEmpty()) {    // country is mandatory to determine the currency from a locale
      String countryCode = System.getProperty("user.country");
      if (countryCode != null) {
        locale = Locale.of(locale.getLanguage(), countryCode.toUpperCase(Locale.ROOT));
        // Never null. Even uncommon combinations, such as de_US, are allowed.
      }
    }
    return Currency.getInstance(locale);
  }

  /**
   * Gets the currency for the current locale.<br>
   * The currencies are cached per locale.
   *
   * @return the currency, never null
   * @throws IllegalArgumentException if no country could be determined or the country is not supported
   */
  public Currency getCurrency() {
    return currencyMap.computeIfAbsent(getLocale(), this::getCurrency);
  }

}
