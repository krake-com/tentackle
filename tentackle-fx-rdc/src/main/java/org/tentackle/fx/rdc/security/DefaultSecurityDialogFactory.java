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

import org.tentackle.common.Service;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.SessionUtilities;

import java.util.function.Consumer;

/**
 * Default implementation of the {@link SecurityDialogFactory}.<br>
 * Shows the {@link SecurityRulesView}, but provides neither a grantee nor a domain context selection.
 * Applications must override {@link #selectGrantee}.
 *
 * @author harald
 */
@Service(SecurityDialogFactory.class)
public class DefaultSecurityDialogFactory implements SecurityDialogFactory {

  /**
   * Creates the security dialog factory.
   */
  public DefaultSecurityDialogFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * {@inheritDoc}
   * <p>
   * This needs to be implemented by the application!
   * The default implementation throws an {@link RdcRuntimeException}.
   */
  @Override
  public void selectGrantee(Object owner, DomainContext context, Consumer<PersistentDomainObject<?>> grantee) {
    throw new RdcRuntimeException("grantee selection not implemented for this application");
  }

  @Override
  public boolean isDomainContextUsed() {
    return false;
  }

  @Override
  public void selectDomainContextObject(Object owner, DomainContext context, Consumer<PersistentDomainObject<?>> contextPdo) {
    throw new RdcRuntimeException("context selection not implemented for this application");
  }

  @Override
  public void showDialog(Object owner, PersistentDomainObject<?> pdo) {
    SecurityRulesView.show(owner, null, pdo);
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void showDialog(Object owner, int classId, DomainContext context) {
    try {
      Class<PersistentDomainObject> cls = (Class<PersistentDomainObject>)
              Class.forName(SessionUtilities.getInstance().getClassName(classId));
      SecurityRulesView.show(owner, null, Pdo.create(cls, context));
    }
    catch (ClassNotFoundException ex) {
      throw new RdcRuntimeException("cannot create security dialog for classId=" + classId, ex);
    }
  }

  @Override
  public void showDialog(Object owner, Class<?> clazz) {
    SecurityRulesView.show(owner, clazz, null);
  }

}
