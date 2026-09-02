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

import org.tentackle.sql.Backend;

/**
 * Foreign key for referential integrity.
 * <p>
 * Because Tentackle references PDOs only by id there is only one attribute per foreign key.
 *
 * @author harald
 */
public interface ForeignKey {

  /**
   * Gets the referencing entity.
   *
   * @return the referencing entity
   */
  Entity getReferencingEntity();

  /**
   * Gets the referencing attribute.
   *
   * @return the referencing attribute
   */
  Attribute getReferencingAttribute();

  /**
   * Gets the referenced entity.
   *
   * @return the referenced entity
   */
  Entity getReferencedEntity();

  /**
   * Gets the referenced attribute.
   *
   * @return the referenced attribute
   */
  Attribute getReferencedAttribute();

  /**
   * Returns whether the reference is via a composite relation.
   *
   * @return true if composite
   */
  boolean isComposite();

  /**
   * Gets the deferrability of this foreign key constraint.<br>
   * Foreign keys are not user-declared in the model, so this is derived purely from the
   * {@code deferred}/{@code deferrable} model defaults option, never from a per-foreign-key
   * override.
   *
   * @return the deferrability, {@link Deferrability#NOT_DEFERRABLE} if none
   */
  Deferrability getDeferrability();

  /**
   * Creates the foreign key creation sql code.
   *
   * @param backend the backend to create sql code for
   * @return the SQL code
   */
  String sqlCreateForeignKey(Backend backend);

  /**
   * Gets the entity that provides the table for the referencing entity.
   *
   * @return the entity, never null
   */
  Entity getReferencingTableProvidingEntity();

  /**
   * Gets the entity that provides the table for the referenced entity.
   *
   * @return the entity, never null
   */
  Entity getReferencedTableProvidingEntity();

}
