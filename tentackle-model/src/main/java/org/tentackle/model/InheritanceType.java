/*
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

package org.tentackle.model;

/**
 * The kind of inheritance.
 *
 * @author harald
 */
public enum InheritanceType {

  /**
   * There is no inheritance at all.<br>
   * This is the default.<br>
   * The concrete entities, i.e. the leafs of the inheritance hierarchy are of type {@code NONE}.
   */
  NONE(false, true, true),

  /**
   * OO inheritance only.<br>
   * The entity extends its super-entity only in plain old java style.
   * The mapping to the relational backend is unaffected.<br>
   * Allows sharing of common attributes and/or methods.<br>
   * The super-entity is abstract and cannot have instances.
   */
  PLAIN(true, false, false),

  /**
   * Single table.<br>
   * Entities of all subtypes are stored in a single table.<br>
   * The super-entity is abstract and cannot have instances.
   * The table automatically gets an extra column "classId" to
   * denote the concrete class.
   * Persistence operations such as selects are allowed in the super entity
   * and automatically create instances of the concrete type.<br>
   */
  SINGLE(true, false, true),

  /**
   * Multi table.<br>
   * Each entity in the inheritance hierarchy has its own table.<br>
   * The super-entity is abstract and cannot have instances.
   * The table of the superclass automatically gets an extra column "classId" to
   * denote the concrete class.
   * Persistence operations such as selects are allowed in the super entity
   * and automatically create instances of the concrete type.<br>
   * The tables are joined by their common id.
   */
  MULTI(true, true, false);




  /** true if this entity may have instances. */
  private final boolean abstractClass;

  /** true if this entity provides its own table. */
  private final boolean mappingToOwnTable;

  /** true if this entity maps to the table of the hierarchy root. */
  private final boolean mappingToSuperTable;


  /**
   * Creates an inheritance type.
   *
   * @param abstractClass true if this entity may have instances
   * @param mappingToOwnTable true if this entity provides its own table
   * @param mappingToSuperTable true if this entity maps to the table of the hierarchy root
   */
  InheritanceType(boolean abstractClass, boolean mappingToOwnTable, boolean mappingToSuperTable) {
    this.abstractClass = abstractClass;
    this.mappingToOwnTable = mappingToOwnTable;
    this.mappingToSuperTable = mappingToSuperTable;
  }


  /**
   * Returns whether entity may have instances.
   *
   * @return true if this entity may have instances
   */
  public boolean isAbstractClass() {
    return abstractClass;
  }

  /**
   * Returns whether entity provides its own table.
   *
   * @return true if this entity provides its own table
   */
  public boolean isMappingToOwnTable() {
    return mappingToOwnTable;
  }

  /**
   * Returns whether entity maps to the table of the hierarchy root.
   *
   * @return true if this entity maps to the table of the hierarchy root
   */
  public boolean isMappingToSuperTable() {
    return mappingToSuperTable;
  }

  /**
   * Returns whether entity does not map to any table.
   *
   * @return true if not mapping
   */
  public boolean isMappingToNoTable() {
    return !mappingToOwnTable && !mappingToSuperTable;
  }

}
