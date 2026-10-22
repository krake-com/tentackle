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
/**
 * Edit handlers for table and treetable views.<br>
 * A {@link org.tentackle.fx.table.edit.TableEditHandler} is associated with the
 * {@link org.tentackle.fx.table.TableConfiguration} and decides what happens when a value is entered
 * in a cell: which cells are editable, whether the value is accepted, how the rows are modified, and which
 * cell is edited next. It works on a {@link org.tentackle.fx.table.edit.TableEditContext}, which hides
 * the differences between table and treetable views.
 */
package org.tentackle.fx.table.edit;
