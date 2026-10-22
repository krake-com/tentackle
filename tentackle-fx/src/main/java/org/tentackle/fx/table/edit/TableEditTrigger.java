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
 *
 */

package org.tentackle.fx.table.edit;

/**
 * The reason why a value entered in a table cell was committed.
 *
 * @author harald
 */
public enum TableEditTrigger {

  /** committed with {@code ENTER} or {@code TAB}. */
  NEXT,

  /** committed with {@code Shift-ENTER} or {@code Shift-TAB}. */
  PREVIOUS,

  /** committed because the editor lost the focus. */
  FOCUS_LOST

}
