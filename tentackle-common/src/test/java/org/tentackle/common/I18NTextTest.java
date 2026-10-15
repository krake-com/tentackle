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

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Locale;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@SuppressWarnings("missing-explicit-ctor")
public class I18NTextTest {

  private static final String SOME_ENGLISH = "the raven";
  private static final String SOME_GERMAN = "Bockwurst";
  private static final String SOME_FRENCH = "c'est la vie";


  @Test
  public void testLocales() {
    assertEquals(LocaleProvider.getInstance().getFallbackLocale().toString(), "en");

    Locale effectiveLocale = LocaleProvider.getInstance().getEffectiveLocale();
    assertEquals(effectiveLocale.toString(), "en");       // default is en_US (see pom.xml)

    // countries are narrowed to languages by the TestLocaleProvider
    Locale germany = Locale.GERMANY;
    assertEquals(germany.toString(), "de_DE");

    Locale france = Locale.FRANCE;
    assertEquals(france.toString(), "fr_FR");

    Locale italy = Locale.ITALY;
    assertEquals(italy.toString(), "it_IT");    // italy is not considered as supported (see TestLocaleProvider)


    I18NText i18NText = I18NText.valueOf(SOME_ENGLISH);
    assertEquals(i18NText.get(), SOME_ENGLISH);

    i18NText = i18NText.with(null);
    assertNull(i18NText.get());
    assertTrue(i18NText.isVacant());

    i18NText = i18NText.with(germany, SOME_GERMAN);
    assertEquals(i18NText.get(germany), SOME_GERMAN);
    assertEquals(i18NText.getDefaultLocale().toString(), "de");
    assertEquals(i18NText.get(france), SOME_GERMAN);    // same, because german is not the fallback

    ParameterString parameterString = i18NText.toParameterString();
    assertEquals(parameterString.toString(), "default='de', de='Bockwurst'");
    I18NText otherI18N = new I18NText(parameterString);
    assertEquals(otherI18N, i18NText);


    i18NText = i18NText.with(SOME_ENGLISH);
    assertNull(i18NText.getDefaultLocale());
    assertEquals(i18NText.get(france), SOME_ENGLISH);
    assertEquals(i18NText.toParameterString().toString(), "de='Bockwurst', en='the raven'");
    I18NText oldI18N = i18NText;

    i18NText = i18NText.with(france, SOME_FRENCH);
    assertEquals(i18NText.get(france), SOME_FRENCH);
    assertEquals(i18NText.get(italy), SOME_ENGLISH);
    assertEquals(i18NText.getTranslations().size(), 3);
    parameterString = i18NText.toParameterString();
    assertEquals(parameterString.toString(), "de='Bockwurst', en='the raven', fr='c''est la vie'");
    otherI18N = new I18NText(parameterString);
    assertEquals(otherI18N, i18NText);
    assertNotEquals(oldI18N, i18NText);

    i18NText = new I18NText();
    parameterString = i18NText.toParameterString();
    assertEquals(parameterString.toString(), "");
    otherI18N = new I18NText(parameterString);
    assertEquals(otherI18N, i18NText);

    i18NText = i18NText.with(france, SOME_FRENCH);
    i18NText = i18NText.with(germany, SOME_GERMAN);
    assertEquals(i18NText.get(), SOME_FRENCH);
    i18NText = i18NText.with(france, null);
    assertEquals(i18NText.get(), SOME_GERMAN);    // german is now the default

    try {
      i18NText = i18NText.with(italy, "parmesan");
      fail("italy is an unsupported locale!");
    }
    catch (TentackleRuntimeException rx) {
      // expected exception!
      assertEquals(i18NText.get(italy), SOME_GERMAN);
    }
  }

  @Test
  public void testDefaultLocaleFromParameterStringIsNarrowed() throws java.text.ParseException {
    // the default locale must be stored the same way as the translation keys
    I18NText text = new I18NText(new ParameterString("default='de_DE', de_DE='Bockwurst'"));
    assertEquals(text.getDefaultLocale(), Locale.GERMAN);
    assertEquals(text.getTranslations().keySet(), java.util.Set.of(Locale.GERMAN));
    assertEquals(text.get(Locale.FRANCE), "Bockwurst");
  }

  /**
   * Counts the resolutions of a locale.
   */
  private static class CountingI18NText extends I18NText {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private int narrowCount;

    CountingI18NText(I18NText text) {
      super(new LinkedHashMap<>(text.getTranslations()), text.getDefaultLocale());
    }

    @Override
    protected Locale narrowLocale(Locale locale) {
      narrowCount++;
      return super.narrowLocale(locale);
    }
  }

  @Test
  public void testResolvedTranslationIsCached() {
    I18NText text = new I18NText(Locale.ENGLISH, SOME_ENGLISH).with(Locale.GERMAN, SOME_GERMAN);
    CountingI18NText counting = new CountingI18NText(text);

    // a CharSequence consumer accessing each character resolves the locale only once
    assertEquals(new StringBuilder().append(counting, 0, counting.length()).toString(), SOME_ENGLISH);
    assertEquals(counting.narrowCount, 1);

    // another locale is resolved again, and still yields the correct translation
    assertEquals(counting.get(Locale.GERMANY), SOME_GERMAN);
    assertEquals(counting.narrowCount, 2);
    assertEquals(counting.get(Locale.GERMANY), SOME_GERMAN);
    assertEquals(counting.narrowCount, 2);
    assertEquals(counting.get(Locale.ENGLISH), SOME_ENGLISH);
    assertEquals(counting.get(Locale.FRANCE), SOME_ENGLISH);    // fallback
    assertEquals(counting.get(Locale.GERMAN), SOME_GERMAN);
    assertEquals(counting.narrowCount, 5);
  }

  @Test
  public void testCurrentLocaleChangeIsHonored() {
    LocaleProvider provider = LocaleProvider.getInstance();
    Locale oldLocale = provider.getCurrentLocale();
    try {
      I18NText text = new I18NText(Locale.ENGLISH, SOME_ENGLISH).with(Locale.GERMAN, SOME_GERMAN);
      provider.setCurrentLocale(Locale.GERMANY);
      assertEquals(text.toString(), SOME_GERMAN);
      assertEquals(text.length(), SOME_GERMAN.length());
      provider.setCurrentLocale(Locale.ENGLISH);
      assertEquals(text.toString(), SOME_ENGLISH);
      assertEquals(text.charAt(0), SOME_ENGLISH.charAt(0));
    }
    finally {
      provider.setCurrentLocale(oldLocale);
    }
  }

  @Test
  public void testCacheDoesNotAffectEqualityAndSerialization() throws Exception {
    I18NText text = new I18NText(Locale.ENGLISH, SOME_ENGLISH).with(Locale.GERMAN, SOME_GERMAN);
    I18NText fresh = new I18NText(Locale.ENGLISH, SOME_ENGLISH).with(Locale.GERMAN, SOME_GERMAN);
    int hash = text.hashCode();
    assertEquals(text.get(Locale.GERMAN), SOME_GERMAN);   // fills the cache
    assertEquals(text, fresh);
    assertEquals(fresh, text);
    assertEquals(text.hashCode(), hash);

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(text);
    }
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
      I18NText copy = (I18NText) ois.readObject();
      assertEquals(copy, text);
      assertEquals(copy.get(Locale.ENGLISH), SOME_ENGLISH);
      assertEquals(copy.get(Locale.GERMAN), SOME_GERMAN);
    }
  }

}
