/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.model;

/**
 * Describes the sorting of an attribute.
 *
 * @param attribute the attribute
 * @param columnSuffix the optional column suffix for multi-column types, null if none
 * @param sortType  the sort type
 *
 * @author harald
 */
public record AttributeSorting(Attribute attribute, String columnSuffix, SortType sortType) {

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(sortType == SortType.ASC ? '+' : '-');
    buf.append(attribute.getName());
    if (columnSuffix != null) {
      buf.append('#').append(columnSuffix);
    }
    return buf.toString();
  }

}
