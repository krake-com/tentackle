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

package org.tentackle.i18n;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Bundle enumeration to implement {@link java.util.ResourceBundle#getKeys()}.
 * <p>
 * Enumerates the bundle's own keys first, followed by the keys of the parent bundle that the bundle does not
 * define itself. Thus, each key is returned once.
 * @author harald
 */
public class BundleEnumeration implements Enumeration<String> {

  private final Set<String> keys;
  private final Iterator<String> iterator;
  private final Enumeration<String> parentEnumeration;
  private String next = null;

  /**
   * Creates a bundle enumeration.
   *
   * @param keys              the bundle keys
   * @param parentEnumeration the parent key enumeration, null if no parent
   */
  public BundleEnumeration(Set<String> keys, Enumeration<String> parentEnumeration) {
    this.keys = keys;
    this.iterator = keys.iterator();
    this.parentEnumeration = parentEnumeration;
  }

  @Override
  public boolean hasMoreElements() {
    if (next == null) {
      if (iterator.hasNext()) {
        next = iterator.next();
      }
      else if (parentEnumeration != null) {
        while (next == null && parentEnumeration.hasMoreElements()) {
          next = parentEnumeration.nextElement();
          if (keys.contains(next)) {
            next = null;
          }
        }
      }
    }
    return next != null;
  }

  @Override
  public String nextElement() {
    if (hasMoreElements()) {
      String result = next;
      next = null;
      return result;
    }
    else {
      throw new NoSuchElementException();
    }
  }
}
