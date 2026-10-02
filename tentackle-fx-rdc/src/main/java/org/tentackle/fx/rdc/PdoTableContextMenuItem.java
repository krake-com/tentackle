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

package org.tentackle.fx.rdc;

import org.tentackle.pdo.PersistentDomainObject;

/**
 * A context menu item.<br>
 * Context menu items are used in popup menus for {@link PdoTableCell}s.
 * The implementations must extend {@code MenuItem} and must be annotated with {@link PdoTableContextMenuItemService} to be found.
 * <p>
 * The menu item must provide the following constructor:
 * <pre>
 *   SomeMenuItem(PdoTableCell cell)
 * </pre>
 * This is checked by an annotation processor.
 * <p>
 * Items with {@code isVisible() == false} will not be added to the context menu.
 *
 * @author harald
 * @param <T> the PDO type
 */
public interface PdoTableContextMenuItem<T extends PersistentDomainObject<T>> {

}
