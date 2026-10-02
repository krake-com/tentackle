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

package org.tentackle.model;

/**
 * The referential integrity mode.
 *
 * @author harald
 */
public enum Integrity {

  /** No integrity checks at all. */
  NONE(false, false, false),

  /** Application-level integrity checks only. */
  SOFT(true, false, false),

  /** Application-level checks and foreign keys for non-composite relations. */
  RELATED(true, true, false),

  /** Application-level checks and foreign keys for composite relations. */
  COMPOSITE(true, false, true),

  /** Same as {@link #RELATED} but no checks on the application level. */
  RELATED_DBONLY(false, true, false),

  /** Same as {@link #COMPOSITE} but no checks on the application level. */
  COMPOSITE_DBONLY(false, false, true),

  /** Same as {@link #FULL} but no checks on the application level. */
  FULL_DBONLY(false, true, true),

  /** Application-level checks and foreign keys for all related and composite relations. */
  FULL(true, true, true);


  private final boolean checkedByApplication;           // true if integrity is checked on the application level
  private final boolean relatedCheckedByDatabase;       // true if integrity of related entities is checked by the database
  private final boolean compositesCheckedByDatabase;    // true if integrity of composite entities is checked by the database

  /**
   * Creates an integrity type.
   *
   * @param checkedByApplication true if integrity is checked on the application level
   * @param relatedCheckedByDatabase true if integrity of related entities is checked by the database
   * @param compositesCheckedByDatabase true if integrity of composite entities is checked by the database
   */
  Integrity(boolean checkedByApplication, boolean relatedCheckedByDatabase, boolean compositesCheckedByDatabase) {
    this.checkedByApplication = checkedByApplication;
    this.relatedCheckedByDatabase = relatedCheckedByDatabase;
    this.compositesCheckedByDatabase = compositesCheckedByDatabase;
  }

  /**
   * Returns whether integrity is checked on the application level.
   *
   * @return true if integrity is checked on the application level
   */
  public boolean isCheckedByApplication() {
    return checkedByApplication;
  }

  /**
   * Returns whether integrity is checked by the database for related entities.
   *
   * @return true if integrity is checked by the database
   */
  public boolean isRelatedCheckedByDatabase() {
    return relatedCheckedByDatabase;
  }

  /**
   * Returns whether integrity is checked by the database for composite entities.
   *
   * @return true if integrity is checked by the database
   */
  public boolean isCompositesCheckedByDatabase() {
    return compositesCheckedByDatabase;
  }

}
