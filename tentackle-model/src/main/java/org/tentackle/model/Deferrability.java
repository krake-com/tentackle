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
 * The deferrability of a unique index or foreign key constraint.<br>
 * Controlled by the {@code deferred}/{@code deferrable}/{@code immediate} keywords on an
 * index line, or by the model defaults' {@code deferred}/{@code deferrable}/{@code immediate}
 * option (see {@link ModelDefaults#getDeferrability()}).
 *
 * @author harald
 */
public enum Deferrability {

  /**
   * The constraint is not deferrable: it is always checked immediately.<br>
   * The default if nothing is specified, and the meaning of the explicit {@code immediate}
   * keyword.
   */
  NOT_DEFERRABLE,

  /**
   * The constraint is deferrable but checked immediately by default.<br>
   * A transaction may still defer it via {@code @Transaction(deferred=true)} /
   * {@code Session.setConstraintsDeferred(true)}. Selected by the {@code deferrable} keyword.
   */
  DEFERRABLE_IMMEDIATE,

  /**
   * The constraint is deferrable and checked at the end of the transaction by default.<br>
   * Selected by the {@code deferred} keyword.
   */
  DEFERRABLE_DEFERRED

}
