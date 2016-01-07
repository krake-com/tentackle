/*
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

package org.tentackle.security;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.security.pdo.Security;


interface SecurityDialogFactory$Singleton {
  static SecurityDialogFactory INSTANCE = ServiceFactory.createService(SecurityDialogFactory.class, SecurityDialogFactory.class);
}



/**
 * A factory for {@link SecurityDialog}.
 *
 * @author harald
 */
@Service(SecurityDialogFactory.class)   // defaults to self
public class SecurityDialogFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static SecurityDialogFactory getInstance() {
    return SecurityDialogFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a dialog for editing security rule sets.<br>
   * The SecurityDialog should only
   * be invoked indirectly by the SecurityManager.
   *
   * @param context the domain context
   * @param clazz   the class to set security rules for
   * @param id      the object id in clazz (if clazz is a PersistentDomainObject) or null
   *                if all objects or clazz is not a PersistentDomainObject.
   */
  public SecurityDialog createDialog(DomainContext context, Class<?> clazz, long id) {
    if (isDialogAllowed(context)) {
      return new SecurityDialog(context, clazz, id);
    }
    throw new SecurityException("you're not allowed to use the security dialog");
  }

  /**
   * Returns whether user determined by given context is allowed to use the security dialog.
   *
   * @param context the domain context
   * @return true if allowed
   */
  public boolean isDialogAllowed(DomainContext context) {
    return Pdo.create(Security.class, context).isWriteAllowed();
  }

}
