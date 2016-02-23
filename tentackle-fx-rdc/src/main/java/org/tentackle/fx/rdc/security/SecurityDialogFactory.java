/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.fx.rdc.security;

import javafx.stage.Window;
import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.SecurityResult;
import org.tentackle.security.pdo.Security;
import org.tentackle.security.permissions.EditPermission;

interface SecurityDialogFactory$Singleton {
  SecurityDialogFactory INSTANCE = ServiceFactory.createService(SecurityDialogFactory.class);
}

/**
 * A factory for security dialogs.
 *
 * @author harald
 */
public interface SecurityDialogFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static SecurityDialogFactory getInstance() {
    return SecurityDialogFactory$Singleton.INSTANCE;
  }


  /**
   * Returns whether user determined by given domain context is allowed to use the security dialog.
   *
   * @param context the domain context
   * @return true if allowed
   */
  default boolean isDialogAllowed(DomainContext context) {
    EditPermission permission = SecurityFactory.getInstance().getPermission(EditPermission.class);
    SecurityResult result = SecurityFactory.getInstance().getSecurityManager().evaluate(
            context, permission, PdoUtilities.getInstance().getClassId(Security.class.getName()), 0);
    return result.isAccepted();
  }

  /**
   * Selects a grantee.<br>
   * This is usually a search dialog for users and/or usergroups.
   *
   * @param <T> the PDO type
   * @param owner the owner window
   * @param context the domain context
   * @return the grantee, null if none selected
   */
  <T extends PersistentDomainObject<T>> T selectGrantee(Window owner, DomainContext context);

  /**
   * Returns whether the domain context is used in security rules.<br>
   * If used, {@link #selectDomainContextObject} must be overridden by the application.
   *
   * @return true if used, false by default
   */
  boolean isDomainContextUsed();

  /**
   * Selects a domain context PDO.<br>
   * This is usually a search dialog for PDOs describing a domain context.
   *
   * @param <T> the PDO type
   * @param owner the owner window
   * @param context the domain context
   * @return the context PDO, null if none selected
   */
  <T extends PersistentDomainObject<T>> T selectDomainContextObject(Window owner, DomainContext context);

  /**
   * Shows a dialog to edit security rule sets for a PDO.<br>
   *
   * @param pdo the pdo
   */
  void showDialog(PersistentDomainObject<?> pdo);

  /**
   * Shows a dialog to edit security rule sets for a PDO-class.<br>
   *
   * @param classId the class-ID
   * @param context the domain context
   */
  void showDialog(int classId, DomainContext context);

  /**
   * Shows a dialog to edit security rule sets for a non-PDO-class.<br>
   *
   * @param clazz the class
   */
  void showDialog(Class<?> clazz);

}
