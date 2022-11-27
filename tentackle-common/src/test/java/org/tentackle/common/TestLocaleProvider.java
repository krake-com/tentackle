/*
 * Copyright (C) 2015 by:
 *
 * Hydro Aluminium Rolled Products GmbH
 * Koblenzer Strasse 122
 * D-41468 Neuss
 * Germany
 * Tel. +49 2131 382 0
 * http://www.hydro.com/Deutschland
 *
 * All Rights Reserved.
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
