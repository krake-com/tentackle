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

import org.tentackle.common.Service;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.SessionUtilities;

/**
 * Default implementation of the {@link SecurityDialogFactory}.
 *
 * @author harald
 */
@Service(SecurityDialogFactory.class)
public class DefaultSecurityDialogFactory implements SecurityDialogFactory {

  /**
   * {@inheritDoc}
   * <p>
   * This needs to be implemented by the application!
   */
  @Override
  public <T extends PersistentDomainObject<T>> T selectGrantee(Window owner, DomainContext context) {
    throw new RdcRuntimeException("grantee selection not implemented for this application");
  }

  @Override
  public boolean isDomainContextUsed() {
    return false;
  }

  @Override
  public <T extends PersistentDomainObject<T>> T selectDomainContextObject(Window owner, DomainContext context) {
    throw new RdcRuntimeException("context selection not implemented for this application");
  }

  @Override
  public void showDialog(PersistentDomainObject<?> pdo) {
    SecurityRulesView.show(null, pdo);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void showDialog(int classId, DomainContext context) {
    try {
      Class<PersistentDomainObject> cls = (Class<PersistentDomainObject>)
              Class.forName(SessionUtilities.getInstance().getClassName(classId));
      SecurityRulesView.show(null, Pdo.create(cls, context));
    }
    catch (ClassNotFoundException ex) {
      throw new RdcRuntimeException("cannot create security dialog for classId=" + classId, ex);
    }
  }

  @Override
  public void showDialog(Class<?> clazz) {
    SecurityRulesView.show(clazz, null);
  }

}
