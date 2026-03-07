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

package org.tentackle.common.impl;

import org.tentackle.common.Timestamp;

import java.io.Serial;
import java.util.Objects;

public final class TestSubClass extends TestClass {

  @Serial
  private static final long serialVersionUID = 1L;

  public TestClass parent;
  public Double aDouble;
  public Timestamp timestamp;

  public TestSubClass(TestClass parent, Double aDouble, Timestamp timestamp) {
    this.parent = parent;
    this.aDouble = aDouble;
    this.timestamp = timestamp;
  }

  public TestSubClass() {
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    TestSubClass that = (TestSubClass) o;
    return Objects.equals(aDouble, that.aDouble) && Objects.equals(timestamp, that.timestamp);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(aDouble);
    result = 31 * result + Objects.hashCode(timestamp);
    return result;
  }
}
