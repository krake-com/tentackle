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

package org.tentackle.sql.datatypes;

import org.tentackle.common.Service;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;

/**
 * Datatype for the primitive type <code>char</code>.
 */
@Service(DataType.class)
public final class CharacterPrimitiveType extends CharacterType {

  /**
   * Creates the datatype for the primitive type <code>char</code>.
   */
  public CharacterPrimitiveType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getJavaType() {
    return "char";
  }

  @Override
  public boolean isPrimitive() {
    return true;
  }

  @Override
  public DataType<?> toNonPrimitive() {
    return DataTypeFactory.getInstance().get("Character");
  }

  /**
   * {@inheritDoc}
   * <p>
   * Primitives are never null.
   *
   * @return false
   */
  @Override
  public boolean isMapNullSupported() {
    return false;
  }

}
