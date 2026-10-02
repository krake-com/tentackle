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

package org.tentackle.fx.rdc;

import org.tentackle.fx.ShortLongTextCellFactory;
import org.tentackle.misc.ShortLongText;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Extended {@link ShortLongTextCellFactory} with security checks.<br>
 * The texts of PDOs the user is not allowed to view are not shown.
 *
 * @param <T> the type
 */
public class RdcShortLongTextCellFactory<T> extends ShortLongTextCellFactory<T> {

  /**
   * Creates the RDC cell factory for short/long texts.
   */
  public RdcShortLongTextCellFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  protected ShortLongText getShortLongText(T item) {
    ShortLongText slItem = super.getShortLongText(item);
    if (slItem instanceof PersistentDomainObject && !((PersistentDomainObject<?>) slItem).isViewAllowed()) {
      slItem = null;
    }
    return slItem;
  }

}
