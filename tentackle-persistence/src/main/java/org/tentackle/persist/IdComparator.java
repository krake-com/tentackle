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


package org.tentackle.persist;

import java.util.Comparator;

/**
 * Compares the object ids of two {@link AbstractDbObject}s.
 *
 * @param <T>
 * @author harald
 */
public class IdComparator<T extends AbstractDbObject<?>> implements Comparator<T> {
  @Override
  public int compare (T o1, T o2) {
    if (o1 == null) {
      return o2 == null ? 0 : -1;
    }
    else if (o2 == null) {
      return o1 == null ? 0 : 1;
    }
    else  {
      // use getId() cause of "reserved Ids"
      return Long.compare(o1.getId(), o2.getId());
    }
  }
}
