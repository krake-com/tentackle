/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.swing.rdc;

import java.util.Locale;
import java.util.ResourceBundle;
import org.tentackle.common.Bundle;
import org.tentackle.locale.I18nFactory;
import org.tentackle.locale.I18nTranslation;

/**
 * Bundle for rdc-package in tentackle-swing-rdc.
 *
 * @author harald
 */
@Bundle
public class RdcSwingRdcBundle {

  /**
   * Gets the translation for a given key.
   *
   * @param key the key
   * @return the translation, never null
   * @throws NullPointerException if <code>key</code> is <code>null</code>
   * @throws java.util.MissingResourceException if no object for the given key can be found
   */
  public static I18nTranslation getTranslation(String key) {
    Locale locale = Locale.getDefault();
    ResourceBundle bundle = ResourceBundle.getBundle(RdcSwingRdcBundle.class.getName(), locale);
    String text = bundle.getString(key);
    return I18nFactory.getInstance().createTranslation(key, locale, text);
  }

  /**
   * Gets a string for the given key.
   *
   * @param key the key
   * @return the string from the bundle
   */
  public static String getString(String key) {
    return getTranslation(key).getText();
  }

  private RdcSwingRdcBundle() {
  }

}
