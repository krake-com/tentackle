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

package org.tentackle.fx.rdc.security;

import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.SecurityResult;
import org.tentackle.security.pdo.Security;
import org.tentackle.security.permissions.EditPermission;
import org.tentackle.session.SessionUtilities;

import java.util.function.Consumer;


interface SecurityDialogFactoryHolder {
  SecurityDialogFactory INSTANCE = ServiceFactory.createService(SecurityDialogFactory.class, DefaultSecurityDialogFactory.class);
}


/**
 * A factory for security dialogs.<br>
 * Applications must replace the default implementation to provide the selection of grantees
 * (usually users and user groups) and, if used, of the domain context objects.
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
    return SecurityDialogFactoryHolder.INSTANCE;
  }


  /**
   * Returns whether the user determined by the given domain context is allowed to use the security dialog.<br>
   * The default implementation checks the edit permission for the {@link Security} class.
   *
   * @param context the domain context
   * @return true if allowed
   */
  default boolean isDialogAllowed(DomainContext context) {
    EditPermission permission = SecurityFactory.getInstance().getPermission(EditPermission.class);
    SecurityResult result = SecurityFactory.getInstance().getSecurityManager().evaluate(
            context, permission, SessionUtilities.getInstance().getClassId(Security.class.getName()), 0);
    return result.isAccepted();
  }

  /**
   * Selects a grantee.<br>
   * This is usually a search dialog for users and/or user groups.
   *
   * @param owner the owner window or view
   * @param context the domain context
   * @param grantee the grantee consumer, invoked with null if none selected
   */
  void selectGrantee(Object owner, DomainContext context, Consumer<PersistentDomainObject<?>> grantee);

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
   * @param owner the owner window or view
   * @param context the domain context
   * @param contextPdo the consumer of the context PDO, invoked with null if none selected
   */
  void selectDomainContextObject(Object owner, DomainContext context, Consumer<PersistentDomainObject<?>> contextPdo);

  /**
   * Shows a dialog to edit security rule sets for a PDO.
   *
   * @param owner the owner window or view
   * @param pdo the PDO
   */
  void showDialog(Object owner, PersistentDomainObject<?> pdo);

  /**
   * Shows a dialog to edit security rule sets for a PDO class.
   *
   * @param owner the owner window or view
   * @param classId the class ID
   * @param context the domain context
   */
  void showDialog(Object owner, int classId, DomainContext context);

  /**
   * Shows a dialog to edit security rule sets for a non-PDO class.
   *
   * @param owner the owner window or view
   * @param clazz the class
   */
  void showDialog(Object owner, Class<?> clazz);

}
