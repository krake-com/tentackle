/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.locale;

import java.util.Locale;

/**
 * An i18n-translated string.
 *
 * @author harald
 */
public class DefaultI18nTranslation implements I18nTranslation {

  private final Locale locale;  // the locale used for translation
  private final String key;     // the original string used as a key
  private final String text;    // the translated text


  /**
   * Creates an I18N translation.
   *
   * @param key the original string used as a key
   * @param locale the locale used for translation
   * @param text the translated text
   */
  public DefaultI18nTranslation(String key, Locale locale, String text) {
    this.key = key;
    this.locale = locale;
    this.text = text;
  }


  @Override
  public String getKey() {
    return key;
  }

  @Override
  public Locale getLocale() {
    return locale;
  }

  @Override
  public String getText() {
    return text;
  }

}
