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
 * @author harald
 */
public class AttributeSorting {

  /** the sorted attribute. */
  private final Attribute attribute;

  /** the sorting type. */
  private final SortType sortType;


  /**
   * Creates a sorting info for an attribute.
   *
   * @param attribute the attribute
   * @param sortType the sort type
   */
  public AttributeSorting(Attribute attribute, SortType sortType) {
    this.attribute = attribute;
    this.sortType = sortType;
  }

  /**
   * Gets the sorted attribute.
   *
   * @return the attribute
   */
  public Attribute getAttribute() {
    return attribute;
  }

  /**
   * Gets the sort type.
   *
   * @return the sort type
   */
  public SortType getSortType() {
    return sortType;
  }

  @Override
  public String toString() {
    return (sortType == SortType.ASC ? "+" : "-") + attribute.getName();
  }

}
