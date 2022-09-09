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

import java.util.Locale;

interface LocaleProviderHolder {
  LocaleProvider INSTANCE = ServiceFactory.createService(LocaleProvider.class, LocaleProvider.class);
}

/**
 * A provider for the Locale.
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


  private final ThreadLocal<Locale> tlLocale = new ThreadLocal<>();


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

}
