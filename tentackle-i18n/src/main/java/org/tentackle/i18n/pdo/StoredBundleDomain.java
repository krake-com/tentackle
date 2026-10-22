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


package org.tentackle.i18n.pdo;

import org.tentackle.i18n.pdo.StoredBundle.StoredBundleUDK;
import org.tentackle.pdo.DomainObject;


/**
 * Stored bundle domain interface.
 *
 * @author harald
 */
public interface StoredBundleDomain extends DomainObject<StoredBundle> {

  @Override
  StoredBundleUDK getUniqueDomainKey();

  /**
   * Gets the translated string.
   * <p>
   * The translations are cached in a map built on first access and rebuilt after
   * {@link #setTranslation(String, String)}.
   *
   * @param key the bundle key
   * @return the translated string, null if there is no translation
   */
  String getTranslation(String key);

  /**
   * Sets a translation.
   * <p>
   * Adds, changes or removes the {@link StoredBundleKey} component for the given key.
   * Removing a key that does not exist is a no-op.
   *
   * @param key the bundle key, never null
   * @param value the translated string, null to remove the translation
   */
  void setTranslation(String key, String value);

}
