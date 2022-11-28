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

import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

interface LocaleProviderHolder {
  LocaleProvider INSTANCE = ServiceFactory.createService(LocaleProvider.class, LocaleProvider.class);
}

/**
 * A provider for {@link Locale}s.<br>
 * Supports thread-local locales and mapping of locales according to the application requirements.
 *
 * @author harald
 */
// @Service does not work in tentackle-common but it improves readability
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
   * Creates a locale provider.
   */
  public LocaleProvider() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Gets the locale used by the current thread.<br>
   *
   * @return the locale, null if no thread-local Locale set
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
   * Sets the session used by the current thread.<br>
   * The session is stored as {@link ThreadLocal}.
   *
   * @param locale the locale
   */
  public void setCurrentLocale(Locale locale) {
    tlLocale.set(locale);
  }

  /**
   * Asserts that a thread-local Locale is set.
   *
   * @throws TentackleRuntimeException if thread-local locale is null
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
   * Returns an unmodifiable list of all supported effective locales.
   *
   * @return the possible effective locales
   */
  public List<Locale> getEffectiveLocales() {
    return EFFECTIVE_LOCALES;
  }

  /**
   * Returns whether given locale is covered by the effective locales.
   *
   * @param locale the locale
   * @return true if locale would map to a valid effective locale
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
   * Converts a tag to a supported locale.
   *
   * @param tag the tag
   * @return the locale, null if tag is empty or null
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


}
