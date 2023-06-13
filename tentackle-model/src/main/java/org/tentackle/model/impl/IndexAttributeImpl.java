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

package org.tentackle.model.impl;

import org.tentackle.model.Attribute;
import org.tentackle.model.Index;
import org.tentackle.model.IndexAttribute;

/**
 * Index part.
 *
 * @author harald
 */
public class IndexAttributeImpl implements IndexAttribute {

  private final Index index;

  private Attribute attribute;
  private String columnName;
  private String functionName;
  private boolean descending;


  /**
   * Creates an index attribute.
   * @param index the index
   */
  public IndexAttributeImpl(Index index) {
    this.index = index;
  }

  /**
   * Gets the index this attribute belongs to.
   *
   * @return the index
   */
  public Index getIndex() {
    return index;
  }


  @Override
  public Attribute getAttribute() {
    return attribute;
  }

  @Override
  public String getColumnName() {
    return columnName;
  }

  @Override
  public String getFunctionName() {
    return functionName;
  }

  @Override
  public boolean isDescending() {
    return descending;
  }


  public void setAttributeAndColumnName(Attribute attribute, String columnName) {
    this.attribute = attribute;
    this.columnName = columnName;
  }

  public void setFunctionName(String functionName) {
    this.functionName = functionName;
  }

  public void setDescending(boolean descending) {
    this.descending = descending;
  }

}
