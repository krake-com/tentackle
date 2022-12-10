/*
 * Tentackle - https://tentackle.org
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

/**
 * Narrowing locale provider for tests.
 */
@SuppressWarnings("missing-explicit-ctor")
@Service(LocaleProvider.class)    // @Service does not work in tentackle-common but it improves readability
public class TestLocaleProvider extends LocaleProvider {

  private static final List<Locale> EFFECTIVE_LOCALES = List.of(Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH);

  @Override
  public Locale getEffectiveLocale(Locale locale) {
    Locale effectiveLocale = Locale.ENGLISH;    // fallback locale
    if (locale != null) {
      for (Locale secondaryLocale : EFFECTIVE_LOCALES) {
        if (locale.getLanguage().equals(secondaryLocale.getLanguage())) {
          effectiveLocale = secondaryLocale;
          break;
        }
      }
    }
    return effectiveLocale;
  }


  @Override
  public List<Locale> getEffectiveLocales() {
    return EFFECTIVE_LOCALES;
  }

  @Override
  public boolean isLocaleSupported(Locale locale) {
    if (locale != null) {
      for (Locale secondaryLocale : EFFECTIVE_LOCALES) {
        if (secondaryLocale.getLanguage().equals(locale.getLanguage())) {
          return true;
        }
      }
    }
    return false;
  }

}
