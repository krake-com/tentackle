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

package org.tentackle.sql;

/**
 * Defines the methods to use for migration.
 *
 * @author harald
 */
public enum MigrationStrategy {

  /** use {@link Backend#sqlRenameColumn}. */
  NAME,

  /** use {@link Backend#sqlAlterColumnType}. */
  TYPE,

  /** emit the {@link Backend#sqlAlterColumnType} statement commented out, for manual review. */
  TYPE_WARNING,

  /** use {@link Backend#sqlRenameAndAlterColumnType}. */
  NAME_AND_TYPE,

  /** use {@link Backend#sqlAlterColumnComment}. */
  COMMENT,

  /** use {@link Backend#sqlAlterColumnNullConstraint}. */
  NULL,

  /** use {@link Backend#sqlAlterColumnDefault}. */
  DEFAULT

}
