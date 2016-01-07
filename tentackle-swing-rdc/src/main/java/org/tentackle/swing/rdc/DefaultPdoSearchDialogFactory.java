/**
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

package org.tentackle.swing.rdc;

import java.awt.Component;
import org.tentackle.common.Service;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.FormUtilities;

/**
 * Default factory for {@link PdoSearchDialog}s.
 *
 * @author harald
 */
@Service(PdoSearchDialogFactory.class)
public class DefaultPdoSearchDialogFactory implements PdoSearchDialogFactory {

  @Override
  public <T extends PersistentDomainObject<T>> PdoSearchDialog<T> createPdoSearchDialog (
                                  Component comp,
                                  PdoSearch<T> searchPlugin,
                                  SelectionFilter selectionFilter,
                                  boolean allowCreate,
                                  boolean modal)  {

    return new PdoSearchDialog<>(FormUtilities.getInstance().getParentWindow(comp),
                                 searchPlugin, selectionFilter, allowCreate, modal);
  }

  @Override
  public <T extends PersistentDomainObject<T>> PdoSearchDialog<T> createPdoSearchDialog (
                                  Component comp,
                                  DomainContext context,
                                  Class<T> searchClass,
                                  SelectionFilter selectionFilter,
                                  boolean allowCreate,
                                  boolean modal) {

    return new PdoSearchDialog<>(FormUtilities.getInstance().getParentWindow(comp),
                                 context, searchClass, selectionFilter, allowCreate, modal);
  }


  @Override
  public <T extends PersistentDomainObject<T>> PdoSearchDialog<T> createPdoSearchDialog (
                                DomainContext context,
                                Class<T> searchClass,
                                SelectionFilter selectionFilter,
                                boolean allowCreate,
                                boolean modal) {
    return new PdoSearchDialog<>(context, searchClass, selectionFilter, allowCreate, modal);
  }

}
