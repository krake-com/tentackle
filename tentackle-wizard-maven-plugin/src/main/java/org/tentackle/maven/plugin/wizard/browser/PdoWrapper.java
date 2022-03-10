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

package org.tentackle.maven.plugin.wizard.browser;

import org.tentackle.bind.Bindable;
import org.tentackle.misc.ShortLongText;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Wrapper for a PDO.<br>
 * Adds the UDK property.
 */
public class PdoWrapper {

  private final PersistentDomainObject<?> pdo;

  public PdoWrapper(PersistentDomainObject<?> pdo) {
    this.pdo = pdo;
  }

  public PersistentDomainObject<?> getPdo() {
    return pdo;
  }

  @Bindable
  public long getId() {
    return pdo.getId();
  }

  @Bindable
  public String getUDK() {
    String udkStr = null;
    if (pdo instanceof ShortLongText shortLongText) {
      udkStr = shortLongText.getShortText() + " (" + shortLongText.getLongText() + ")";
    }
    else if (pdo.isUniqueDomainKeyProvided()) {
      Object uniqueDomainKey = pdo.getUniqueDomainKey();
      if (uniqueDomainKey != null) {
        udkStr = uniqueDomainKey.toString();
      }
    }
    return udkStr;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PdoWrapper that = (PdoWrapper) o;

    return pdo.equals(that.pdo);
  }

  @Override
  public int hashCode() {
    return pdo.hashCode();
  }

}
