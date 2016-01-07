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

import java.util.Enumeration;
import java.util.Locale;

/**
 * Pluggable bundle access.
 *
 * @author harald
 */
public interface BundleAccess {


  /**
   * Gets the basename.
   * <p>
   * The basename is defined by the constructor of the implementing class.
   *
   * @return the basename
   */
  public String getBaseName();

  /**
   * Invalidates the bundle access.
   * <p>
   * Invaludating a bundle may be necessary if the translations change.
   * An invalidated bundle will bre reloaded at first access.
   */
  public void invalidate();


  /**
   * Gets the translation for a given locale.
   *
   * @param locale the locale, null if default locale
   * @param key the key to be translated (with respect to the baseName)
   * @return the translated key
   * @throws BundleException if translation failed
   */
  public String getString(Locale locale, String key) throws BundleException;


  /**
   * Returns an enumeration of the keys.
   *
   * @return an <code>Enumeration</code> of the keys managed by this bundle access.
   */
  public Enumeration<String> getKeys();

}
