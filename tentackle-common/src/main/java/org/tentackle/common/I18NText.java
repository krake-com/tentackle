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

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Multilingual text.<br>
 * This is an immutable object.
 */
public class I18NText implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** parameter name for the default locale. */
  private static final String DEFAULT_PAR = "default";


  /**
   * Creates an I18NText from a string in the current locale.
   *
   * @param text the string
   * @return the i18n object
   */
  public static I18NText valueOf(String text) {
    return new I18NText(text);
  }


  /**
   * Localized strings.<br>
   * {@link LinkedHashMap} to keep the order of insertions.
   */
  private final LinkedHashMap<Locale, String> textMap;

  /**
   * The default locale, if the value hasn't been set for the fallback locale so far.<br>
   * Null if textMap is empty or the translation for the fallback language is provided.
   */
  private final Locale defaultLocale;


  /**
   * Creates an empty I18N object.
   */
  public I18NText() {
    textMap = new LinkedHashMap<>();
    defaultLocale = null;
  }

  /**
   * Creates an I18N object from a string in the current locale.
   *
   * @param text the string
   */
  public I18NText(String text) {
    this(LocaleProvider.getInstance().getLocale(), text);
  }

  /**
   * Creates an I18N object from a string for the given locale.
   *
   * @param locale the locale
   * @param text the string
   */
  public I18NText(Locale locale, String text) {
    textMap = new LinkedHashMap<>();
    defaultLocale = apply(locale, text, textMap, null);
  }



  /**
   * Creates an I18N object from a parameter string.
   *
   * @param parameterString the parameter string
   * @see #toParameterString()
   */
  public I18NText(ParameterString parameterString) {
    LocaleProvider localeProvider = LocaleProvider.getInstance();
    String parameter = parameterString.getParameter(DEFAULT_PAR);
    defaultLocale = parameter == null ? null : localeProvider.fromTag(parameter);
    textMap = new LinkedHashMap<>();
    for (String tag : parameterString.getParameterNames()) {
      Locale locale = localeProvider.fromTag(tag);
      if (locale != null && localeProvider.isLocaleSupported(locale)) {
        parameter = parameterString.getParameter(tag);
        if (parameter != null) {
          textMap.put(localeProvider.getEffectiveLocale(locale), parameter);
        }
      }
    }
  }


  /**
   * Protected constructor for internal use.
   *
   * @param textMap the translations
   * @param defaultLocale the default locale
   */
  protected I18NText(LinkedHashMap<Locale, String> textMap, Locale defaultLocale) {
    this.textMap = textMap;
    this.defaultLocale = defaultLocale;
  }

  /**
   * Applies a translation to an existing map and default locale.
   *
   * @param locale the locale to apply
   * @param text the translation to apply
   * @param withTextMap the current translations
   * @param withDefaultLocale the current default locale
   * @return the new default locale
   */
  protected Locale apply(Locale locale, String text, LinkedHashMap<Locale, String> withTextMap, Locale withDefaultLocale) {
    LocaleProvider localeProvider = LocaleProvider.getInstance();
    if (!localeProvider.isLocaleSupported(locale)) {
      // avoid overriding the fallback locale by an unsupported locale (see getEffectiveLocale below)
      throw new TentackleRuntimeException("unsupported locale: " + locale);
    }

    locale = localeProvider.getEffectiveLocale(locale);
    if (StringHelper.isAllWhitespace(text)) {
      // remove locale
      withTextMap.remove(locale);
      if (locale.equals(withDefaultLocale)) {   // default overrides fallback
        withDefaultLocale = null;
        if (!withTextMap.isEmpty()) {
          // take the default from the locale that was added next after the old default
          withDefaultLocale = localeProvider.getEffectiveLocale(withTextMap.keySet().iterator().next());
        }
        // else: now empty -> no default anymore
      }
    }
    else {
      // add locale
      if (withDefaultLocale == null) {
        if (withTextMap.isEmpty() && !locale.equals(localeProvider.getFallbackLocale())) {
          withDefaultLocale = localeProvider.getEffectiveLocale(locale);   // first locale, if not the fallback, determines the temporary default locale
        }
      }
      else if (locale.equals(localeProvider.getFallbackLocale())) {
        withDefaultLocale = null;   // fallback is now the default
      }
      withTextMap.put(locale, text);
    }

    return withDefaultLocale;
  }


  /**
   * Derives an I18NText object with a translation for the given locale.
   *
   * @param locale the locale
   * @param text the string
   * @return the old translation, null if new
   */
  @SuppressWarnings("unchecked")
  public I18NText with(Locale locale, String text) {
    LinkedHashMap<Locale, String> withTextMap = (LinkedHashMap<Locale, String>) textMap.clone();
    Locale withDefaultLocale = apply(locale, text, withTextMap, defaultLocale);
    return new I18NText(withTextMap, withDefaultLocale);
  }

  /**
   * Derives an I18NText object with a translation for the current locale.
   *
   * @param text the string
   * @return the old translation, null if new
   */
  public I18NText with(String text) {
    return with(LocaleProvider.getInstance().getLocale(), text);
  }

  /**
   * Gets the translation for the given locale.
   *
   * @param locale the locale
   * @return the string, null if no translations at all
   */
  public String get(Locale locale) {
    LocaleProvider localeProvider = LocaleProvider.getInstance();
    String text = textMap.get(localeProvider.getEffectiveLocale(locale));
    if (text == null) {
      if (defaultLocale != null) {
        text = textMap.get(defaultLocale);
      }
      else {
        text = textMap.get(localeProvider.getFallbackLocale());
      }
    }
    return text;
  }

  /**
   * Gets the translation for the current locale.
   *
   * @return the string, null if no translations at all
   */
  public String get() {
    return get(LocaleProvider.getInstance().getLocale());
  }

  /**
   * Gets the current default locale.<br>
   *
   * @return the default locale, null if no translations at all or the translation for the fallback locale is set
   */
  public Locale getDefaultLocale() {
    return defaultLocale;
  }

  /**
   * Gets the mapping between locales and translations.
   *
   * @return the unmodifiable map of translations
   */
  public Map<Locale, String> getTranslations() {
    return Map.copyOf(textMap);
  }

  /**
   * Creates a parameter string of the current translations.
   *
   * @return the parameter string
   */
  public ParameterString toParameterString() {
    ParameterString ps = new ParameterString();
    LocaleProvider localeProvider = LocaleProvider.getInstance();
    if (defaultLocale != null) {
      ps.setParameter(DEFAULT_PAR, localeProvider.toTag(defaultLocale));
    }
    for (Map.Entry<Locale, String> entry : textMap.entrySet()) {
      ps.setParameter(localeProvider.toTag(entry.getKey()), entry.getValue());
    }
    return ps;
  }


  @Override
  public String toString() {
    return get();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    I18NText i18NText = (I18NText) o;

    if (!textMap.equals(i18NText.textMap)) {
      return false;
    }
    return Objects.equals(defaultLocale, i18NText.defaultLocale);
  }

  @Override
  public int hashCode() {
    int result = textMap.hashCode();
    result = 31 * result + (defaultLocale != null ? defaultLocale.hashCode() : 0);
    return result;
  }

}
