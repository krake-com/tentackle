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


package org.tentackle.domain.security;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.tentackle.domain.AbstractDomainObject;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainObjectService;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.pdo.Security;
import org.tentackle.security.pdo.SecurityDomain;

/**
 * Security domain implementation.
 *
 * @author harald
 */
@DomainObjectService(Security.class)
public class SecurityDomainImpl extends AbstractDomainObject<Security, SecurityDomainImpl> implements SecurityDomain {

  private static final long serialVersionUID = 1L;

  @SuppressWarnings("stateful-domain-logic")
  private Class<? extends Permission>[] permissions;      // lazily initialized permissions


  public SecurityDomainImpl(Security pdo) {
    super(pdo);
  }

  public SecurityDomainImpl() {
    super();
  }

  @Override
  public Object getUniqueDomainKey() {
    return getPdo().getId();
  }

  @Override
  public Security findByUniqueDomainKey(Object domainKey) {
    return getPdo().select((Long) domainKey);
  }

  @Override
  public boolean evaluate(DomainContext context, Permission permission) {
    return // check that the context applies
           (context == null || context.isWithinContext(pdo().getDomainContextId(), pdo().getDomainContextClassId())) &&
           // check that requested permission applies to this rule
           (pdo().isAllowed() ?
              permission.isAllowedBy(getPermissions()) :
              permission.isDeniedBy(getPermissions()));
  }

  @Override
  public String securableToString() {
    StringBuilder buf = new StringBuilder();
    if (pdo().getObjectClassId() != 0) {
      // is a PDO
      String className = PdoUtilities.getInstance().getPdoClassName(pdo().getObjectClassId());
      PersistentDomainObject<?> securedPdo = Pdo.create(className, getDomainContext());
      if (pdo().getObjectId() != 0) {
        buf.append(securedPdo.getSingular()).append(' ');
        PersistentDomainObject<?> sobj = securedPdo.select(pdo().getObjectId());
        if (sobj == null) {
          buf.append("<?").append(pdo().getObjectId()).append("?>");
        }
        else {
          buf.append(sobj);
        }
      }
      else {
        buf.append(securedPdo.getPlural());
      }
    }
    else if (pdo().getObjectClassName() != null) {
      buf.append(ReflectionHelper.getClassBaseName(pdo().getObjectClassName()));
    }
    return buf.toString();
  }

  @Override
  public String granteeToString() {
    StringBuilder buf = new StringBuilder();
    PersistentDomainObject<?> grantee = pdo().getGrantee();
    if (grantee != null) {
      buf.append(grantee.getSingular()).append(' ').append(grantee);
    }
    return buf.toString();
  }

  @Override
  public String contextToString() {
    PersistentDomainObject<?> contextPdo = pdo().getDomainContextObject();
    return contextPdo == null ? null : contextPdo.toString();
  }

  /**
   * Gets the permissions from the persistence object.
   *
   * @return the permissions
   */
  @SuppressWarnings("unchecked")
  protected Class<? extends Permission>[] getPermissions() {
    if (permissions == null) {
      if (pdo().getPermissions() == null) {
        permissions = new Class[0];
      }
      else  {
        StringTokenizer stok = new StringTokenizer(pdo().getPermissions(), ", ");
        List<Class<? extends Permission>> perms = new ArrayList<>();
        while (stok.hasMoreTokens()) {
          String permissionName = stok.nextToken();
          Class<? extends Permission> permission = SecurityFactory.getInstance().getPermissionInterface(permissionName);
          if (permission == null) {
            throw new SecurityException("unknown permission '" + permissionName + "'");
          }
          perms.add(permission);
        }
        permissions = perms.toArray(new Class[perms.size()]);
      }
    }
    return permissions;
  }

}
