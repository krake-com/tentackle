/*
 * Tentackle - https://tentackle.org
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

import org.tentackle.domain.AbstractDomainObject;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainObjectService;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.security.Permission;
import org.tentackle.security.SecurityException;
import org.tentackle.security.SecurityFactory;
import org.tentackle.security.pdo.Security;
import org.tentackle.security.pdo.SecurityDomain;
import org.tentackle.security.pdo.SecurityPersistence;
import org.tentackle.session.SessionUtilities;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Security domain implementation.
 *
 * @author harald
 */
@DomainObjectService(Security.class)
public class SecurityDomainImpl extends AbstractDomainObject<Security, SecurityDomainImpl> implements SecurityDomain {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * The permissions, lazily parsed from the persistence object.
   * <p>
   * Volatile, and that is essential here: security rules are loaded from the PDO cache
   * (see {@code DefaultSecurityManager}), so this delegate is shared by every thread evaluating a
   * permission, and the array is built by whichever request thread gets there first. Published by
   * a plain write, a racing thread could see the array reference before the writes of its elements
   * and trip over a null entry while checking the permissions.
   */
  @SuppressWarnings("stateful-domain-logic")
  private volatile Class<? extends Permission>[] permissions;


  public SecurityDomainImpl(Security pdo) {
    super(pdo);
  }

  public SecurityDomainImpl() {
    super();
  }

  @Override
  public Object getUniqueDomainKey() {
    return getPdo().getPersistenceDelegate().getId();
  }

  @Override
  public Security findByUniqueDomainKey(Object domainKey) {
    return getPdo().select((Long) domainKey);
  }

  @Override
  public boolean evaluate(DomainContext context, Permission permission) {
    SecurityPersistence po = po();
    return // check that the context applies
           (context == null || context.isWithinContext(po.getDomainContextId(), po.getDomainContextClassId())) &&
           // check that requested permission applies to this rule
           (po.isAllowed() ?
              permission.isAllowedBy(getPermissions()) :
              permission.isDeniedBy(getPermissions()));
  }

  @Override
  public String securableToString() {
    StringBuilder buf = new StringBuilder();
    if (po().getObjectClassId() != 0) {
      // is a PDO
      String className = SessionUtilities.getInstance().getClassName(po().getObjectClassId());
      PersistentDomainObject<?> securedPdo = Pdo.create(className, getDomainContext());
      if (po().getObjectId() != 0) {
        buf.append(securedPdo.getSingular()).append(' ');
        PersistentDomainObject<?> sobj = securedPdo.select(po().getObjectId());
        if (sobj == null) {
          buf.append("<?").append(po().getObjectId()).append("?>");
        }
        else {
          buf.append(sobj);
        }
      }
      else {
        buf.append(securedPdo.getPlural());
      }
    }
    else if (po().getObjectClassName() != null) {
      buf.append(ReflectionHelper.getClassBaseName(po().getObjectClassName()));
    }
    return buf.toString();
  }

  @Override
  public String granteeToString() {
    StringBuilder buf = new StringBuilder();
    PersistentDomainObject<?> grantee = po().getGrantee();
    if (grantee != null) {
      buf.append(grantee.getSingular()).append(' ').append(grantee);
    }
    return buf.toString();
  }

  @Override
  public String contextToString() {
    PersistentDomainObject<?> contextPdo = po().getDomainContextObject();
    return contextPdo == null ? null : contextPdo.toString();
  }

  /**
   * Gets the permissions from the persistence object.
   *
   * @return the permissions
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected Class<? extends Permission>[] getPermissions() {
    Class<? extends Permission>[] permissionClasses = permissions;    // read the volatile once
    if (permissionClasses == null) {
      String permissionNames = po().getPermissions();
      if (permissionNames == null) {
        permissionClasses = new Class[0];
      }
      else  {
        StringTokenizer stok = new StringTokenizer(permissionNames, ", ");
        List<Class<? extends Permission>> perms = new ArrayList<>();
        while (stok.hasMoreTokens()) {
          String permissionName = stok.nextToken();
          Class<? extends Permission> permission = SecurityFactory.getInstance().getPermissionInterface(permissionName);
          if (permission == null) {
            throw new SecurityException("unknown permission '" + permissionName + "'");
          }
          perms.add(permission);
        }
        permissionClasses = perms.toArray(new Class[0]);
      }
      // publish the fully built array; two threads racing here parse the same names to equal arrays
      permissions = permissionClasses;
    }
    return permissionClasses;
  }

  /**
   * Bypass the invocation handler.
   *
   * @return the persistence delegate
   */
  protected SecurityPersistence po() {
    return (SecurityPersistence) me().getPersistenceDelegate();
  }
}
