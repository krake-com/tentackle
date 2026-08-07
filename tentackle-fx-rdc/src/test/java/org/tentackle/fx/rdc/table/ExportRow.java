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

package org.tentackle.fx.rdc.table;

import org.tentackle.bind.Bindable;

/**
 * The row object exported by {@link TableUtilitiesCsvTest}.
 */
public class ExportRow {

  private String first;
  private String second;
  private String third;

  /**
   * Creates a row.
   *
   * @param first the first column's value, may be null
   * @param second the second column's value, may be null
   * @param third the third column's value, may be null
   */
  public ExportRow(String first, String second, String third) {
    this.first = first;
    this.second = second;
    this.third = third;
  }

  /**
   * Gets the first column's value.
   *
   * @return the value, may be null
   */
  @Bindable
  public String getFirst() {
    return first;
  }

  /**
   * Sets the first column's value.
   *
   * @param first the value
   */
  public void setFirst(String first) {
    this.first = first;
  }

  /**
   * Gets the second column's value.
   *
   * @return the value, may be null
   */
  @Bindable
  public String getSecond() {
    return second;
  }

  /**
   * Sets the second column's value.
   *
   * @param second the value
   */
  public void setSecond(String second) {
    this.second = second;
  }

  /**
   * Gets the third column's value.
   *
   * @return the value, may be null
   */
  @Bindable
  public String getThird() {
    return third;
  }

  /**
   * Sets the third column's value.
   *
   * @param third the value
   */
  public void setThird(String third) {
    this.third = third;
  }

}
