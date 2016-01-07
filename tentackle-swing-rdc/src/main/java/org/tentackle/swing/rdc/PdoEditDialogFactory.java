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

import java.awt.Window;
import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.PersistentDomainObject;


interface PdoEditDialogFactory$Singleton {
  static PdoEditDialogFactory INSTANCE = ServiceFactory.createService(
              PdoEditDialogFactory.class, DefaultPdoEditDialogFactory.class);
}


/**
 * A factory for {@link PdoEditDialog}s.
 *
 * @author harald
 */
public interface PdoEditDialogFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static PdoEditDialogFactory getInstance() {
    return PdoEditDialogFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a PDO edit dialog.
   *
   * @param <T> the PDO's class
   * @param owner the owner window, null if no owner
   * @param pdo is the database object
   * @param modal is true if modal, else false
   * @return the edit dialog
   */
  <T extends PersistentDomainObject<T>> PdoEditDialog<T> createPdoEditDialog(Window owner, T pdo, boolean modal);

}
