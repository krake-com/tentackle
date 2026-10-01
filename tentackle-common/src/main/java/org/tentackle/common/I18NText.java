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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * Multilingual text.<br>
 * This is an immutable and serializable object which is supported as a regular datatype
 * via <code>org.tentackle.sql.datatypes.I18NTextType</code>.
 * <p>
 * Model example:<br>
 * <pre>
 *   ...
 *   I18NText        description   description    internationalized description [COLS=30]
 *   ...
 * </pre>
 * <p>
 * Don't forget to add the <code>[COLS=...]</code> option if you want to use auto-binding for the field width in FX views.
 * <p>
 * Please keep in mind that <code>I18NText</code> implements
 * {@link CharSequence} and {@link Comparable} only for the current locale (see {@code LocaleProvider.getInstance().getLocale()}),
 * but {@link Object#equals(Object)} and {@link Object#hashCode()} cover all translations.
 * As a consequence, sorted sets of <code>I18NText</code> elements require appropriate attention.
 * <p>
 * Translations can only be added for locales supported by the {@link LocaleProvider} (see
 * {@link LocaleProvider#isLocaleSupported(Locale)}). The locales are mapped to their effective locales
 * and narrowed to the language. Since the default provider supports {@link Locale#ENGLISH} only,
 * multilingual applications must provide their own {@link LocaleProvider}.
 * <p>
 * If there is no translation for the requested locale, the translation of the default locale is returned.
 * The default locale is the locale of the first translation, unless a translation for the
 * fallback locale ({@link LocaleProvider#getFallbackLocale()}) is provided, which then takes precedence.
 * Setting a blank or null text removes the translation for that locale.
 */
public class I18NText implements CharSequence, Comparable<I18NText>, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** parameter name for the default locale. */
  private static final String DEFAULT_PAR = "default";


  /**
   * Creates an <code>I18NText</code> from a string in the current locale.
   *
   * @param text the string
   * @return the i18n object
   * @throws TentackleRuntimeException if the locale is not supported by the {@link LocaleProvider}
   */
  public static I18NText valueOf(String text) {
    return new I18NText(text);
  }


  /**
   * Localized strings.<br>
   * {@link LinkedHashMap} to keep the order of insertions.
   * The keys are narrowed to the language.
   */
  private final LinkedHashMap<Locale, String> textMap;

  /**
   * The default locale, if the value hasn't been set for the fallback locale so far.<br>
   * Narrowed to the language, like the keys of the textMap.<br>
   * Null if textMap is empty or the translation for the fallback language is provided.
   */
  private final Locale defaultLocale;


  /**
   * Creates a vacant <code>I18NText</code>.
   */
  public I18NText() {
    textMap = new LinkedHashMap<>();
    defaultLocale = null;
  }

  /**
   * Creates an <code>I18NText</code> from a string in the current locale.
   *
   * @param text the string
   * @throws TentackleRuntimeException if the locale is not supported by the {@link LocaleProvider}
   */
  public I18NText(String text) {
    this(LocaleProvider.getInstance().getLocale(), text);
  }

  /**
   * Creates an <code>I18NText</code> from a string for the given locale.
   *
   * @param locale the locale
   * @param text the string
   * @throws TentackleRuntimeException if the locale is not supported by the {@link LocaleProvider}
   */
  public I18NText(Locale locale, String text) {
    textMap = new LinkedHashMap<>();
    defaultLocale = apply(locale, text, textMap, null);
  }

  /**
   * Creates an <code>I18NText</code> from a parameter string.<br>
   * Translations for unsupported locales are silently ignored.
   *
   * @param parameterString the parameter string
   * @see #toParameterString()
   */
  public I18NText(ParameterString parameterString) {
    LocaleProvider localeProvider = LocaleProvider.getInstance();
    String parameter = parameterString.getParameter(DEFAULT_PAR);
    Locale locale = parameter == null ? null : localeProvider.fromTag(parameter);
    defaultLocale = locale == null ? null : narrowLocale(localeProvider.getEffectiveLocale(locale));
    textMap = new LinkedHashMap<>();
    for (String tag : parameterString.getParameterNames()) {
      locale = localeProvider.fromTag(tag);
      if (locale != null && localeProvider.isLocaleSupported(locale)) {
        parameter = parameterString.getParameter(tag);
        if (parameter != null) {
          textMap.put(narrowLocale(localeProvider.getEffectiveLocale(locale)), parameter);
        }
      }
    }
  }


  /**
   * Derives an I18NText object with a translation for the given locale.<br>
   * This object is not modified.
   *
   * @param locale the locale
   * @param text the string, null or blank to remove the translation
   * @return a new I18NText with the translation applied
   * @throws TentackleRuntimeException if the locale is not supported by the {@link LocaleProvider}
   */
  @SuppressWarnings("unchecked")
  public I18NText with(Locale locale, String text) {
    LinkedHashMap<Locale, String> withTextMap = (LinkedHashMap<Locale, String>) textMap.clone();
    Locale withDefaultLocale = apply(locale, text, withTextMap, defaultLocale);
    return new I18NText(withTextMap, withDefaultLocale);
  }

  /**
   * Derives an I18NText object with a translation for the current locale.<br>
   * This object is not modified.
   *
   * @param text the string, null or blank to remove the translation
   * @return a new I18NText with the translation applied
   * @throws TentackleRuntimeException if the locale is not supported by the {@link LocaleProvider}
   */
  public I18NText with(String text) {
    return with(LocaleProvider.getInstance().getLocale(), text);
  }

  /**
   * Gets the translation for the given locale.<br>
   * If there is no translation for the locale, the translation of the default locale,
   * or if there is none, of the fallback locale is returned.
   *
   * @param locale the locale
   * @return the string, null if no translations at all or no translation for the default or fallback locale
   */
  public String get(Locale locale) {
    LocaleProvider localeProvider = LocaleProvider.getInstance();
    String text = textMap.get(narrowLocale(localeProvider.getEffectiveLocale(locale)));
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
   * @return the string, null if no translations at all or no translation for the default or fallback locale
   * @see #get(Locale)
   */
  public String get() {
    return get(LocaleProvider.getInstance().getLocale());
  }

  /**
   * Gets the current default locale.
   *
   * @return the default locale, null if no translations at all or the translation for the fallback locale is set
   */
  public Locale getDefaultLocale() {
    return defaultLocale;
  }

  /**
   * Gets the mapping between locales and translations.<br>
   * Notice that the returned map does not preserve the insertion order.
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

  /**
   * Returns whether there are no translations at all.
   *
   * @return true if the map of translations is empty
   */
  public boolean isVacant() {
    return textMap.isEmpty();
  }


  @Override
  public int length() {
    String text = get();
    return text == null ? 0 : text.length();
  }

  @Override
  public boolean isEmpty() {
    return isVacant() || length() == 0;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Refers to the translation for the current locale.
   *
   * @throws IndexOutOfBoundsException if there is no translation or the index is out of range
   */
  @Override
  public char charAt(int index) {
    String text = get();
    if (text != null) {
      return text.charAt(index);
    }
    throw new IndexOutOfBoundsException("no translation for the current locale");
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    String text = get();
    return text == null ? "".subSequence(start, end) : text.subSequence(start, end);
  }

  @Override
  public IntStream chars() {
    String text = get();
    int length = text == null ? 0 : text.length();

    class CharIterator implements PrimitiveIterator.OfInt {
      int cur = 0;

      public boolean hasNext() {
        return cur < length;
      }

      public int nextInt() {
        if (hasNext()) {
          return text.charAt(cur++);
        }
        else {
          throw new NoSuchElementException();
        }
      }

      @Override
      public void forEachRemaining(IntConsumer block) {
        for (; cur < length; cur++) {
          block.accept(text.charAt(cur));
        }
      }
    }

    return StreamSupport.intStream(() ->
                                     Spliterators.spliterator(
                                       new CharIterator(),
                                       length,
                                       Spliterator.ORDERED),
      Spliterator.SUBSIZED | Spliterator.SIZED | Spliterator.ORDERED,
      false);
  }

  @Override
  public IntStream codePoints() {
    String text = get();
    int length = text == null ? 0 : text.length();

    class CodePointIterator implements PrimitiveIterator.OfInt {
      int cur = 0;

      @Override
      public void forEachRemaining(IntConsumer block) {
        int i = cur;
        try {
          while (i < length) {
            char c1 = text.charAt(i++);
            if (!Character.isHighSurrogate(c1) || i >= length) {
              block.accept(c1);
            }
            else {
              char c2 = text.charAt(i);
              if (Character.isLowSurrogate(c2)) {
                i++;
                block.accept(Character.toCodePoint(c1, c2));
              }
              else {
                block.accept(c1);
              }
            }
          }
        }
        finally {
          cur = i;
        }
      }

      public boolean hasNext() {
        return cur < length;
      }

      public int nextInt() {
        if (cur >= length) {
          throw new NoSuchElementException();
        }
        char c1 = text.charAt(cur++);
        if (Character.isHighSurrogate(c1) && cur < length) {
          char c2 = text.charAt(cur);
          if (Character.isLowSurrogate(c2)) {
            cur++;
            return Character.toCodePoint(c1, c2);
          }
        }
        return c1;
      }
    }

    return StreamSupport.intStream(() ->
                                     Spliterators.spliteratorUnknownSize(
                                       new CodePointIterator(),
                                       Spliterator.ORDERED),
      Spliterator.ORDERED,
      false);
  }

  /**
   * Compares the translations for the current locale.<br>
   * Missing translations come first.
   *
   * @param o the other text
   * @return the comparison result
   */
  @Override
  public int compareTo(I18NText o) {
    if (this == o) {
      return 0;
    }
    String text = get();
    String oText = o.get();
    if (text == null) {
      return oText == null ? 0 : -1;
    }
    return oText == null ? 1 : text.compareTo(oText);
  }

  /**
   * Returns the translation for the current locale.
   *
   * @return the translation, null if none
   * @see #get()
   */
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


  /**
   * Protected constructor for internal use only.
   *
   * @param textMap the translations
   * @param defaultLocale the default locale
   */
  protected I18NText(LinkedHashMap<Locale, String> textMap, Locale defaultLocale) {
    this.textMap = textMap;
    this.defaultLocale = defaultLocale;
  }

  /**
   * Applies a translation to an existing map and default locale.<br>
   * A null or blank text removes the translation.
   *
   * @param locale the locale to apply
   * @param text the translation to apply
   * @param withTextMap the current translations
   * @param withDefaultLocale the current default locale
   * @return the new default locale
   * @throws TentackleRuntimeException if the locale is not supported by the {@link LocaleProvider}
   */
  protected Locale apply(Locale locale, String text, LinkedHashMap<Locale, String> withTextMap, Locale withDefaultLocale) {
    LocaleProvider localeProvider = LocaleProvider.getInstance();
    if (!localeProvider.isLocaleSupported(locale)) {
      // avoid overriding the fallback locale by an unsupported locale (see getEffectiveLocale below)
      throw new TentackleRuntimeException("unsupported locale: " + locale);
    }

    locale = narrowLocale(localeProvider.getEffectiveLocale(locale));

    if (StringHelper.isAllWhitespace(text)) {
      // remove locale
      withTextMap.remove(locale);
      if (locale.equals(withDefaultLocale)) {   // default overrides fallback
        withDefaultLocale = null;
        if (!withTextMap.isEmpty()) {
          // take the default from the locale that was added next after the old default
          withDefaultLocale = withTextMap.keySet().iterator().next();   // keys are already narrowed
        }
        // else: now empty -> no default anymore
      }
    }
    else {
      // add locale
      if (withDefaultLocale == null) {
        if (withTextMap.isEmpty() && !locale.equals(localeProvider.getFallbackLocale())) {
          withDefaultLocale = locale;   // first locale, if not the fallback, determines the temporary default locale
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
   * Narrows the effective locale returned by the {@link LocaleProvider} further to language.<br>
   * The LocaleProvider may allow wider locales for ResourceBundles,
   * but for I18NText this would complicate things way too much.<br>
   * In most applications, the LocaleProvider narrows to language anyway.
   *
   * @param locale the effective locale
   * @return the language-only locale
   */
  protected Locale narrowLocale(Locale locale) {
    if (!locale.getVariant().isEmpty() || !locale.getCountry().isEmpty()) {
      // variant or country given: narrow to language only.
      // The LocaleProvider may allow wider locales for ResourceBundles,
      // but for I18NText this would complicate things way too much.
      // In most applications, the LocaleProvider narrows to language anyway.
      locale = Locale.of(locale.getLanguage());
    }
    return locale;
  }

}
