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


package org.tentackle.security.pdo;

import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.Plural;
import org.tentackle.pdo.Singular;
import org.tentackle.security.DefaultSecurityManager;
import org.tentackle.session.ClassId;
import org.tentackle.session.TableName;


/*
 * @> $mapping
 *
 * # security ACLs
 * name := $classname
 * id := $classid
 * table := $tablename
 * alias := scrty
 * integrity := none
 *
 * ## attributes
 * [root, remote, bind, +priority]
 *
 * String(128)    objectClassName         objectclass       the protected classname, null if an entity
 * int            objectClassId           objectclassid     the protected object's class id, 0 if not an entity
 * long           objectId                objectid          the id of the protected object, 0 if all instances or not an entity
 * int            domainContextClassId    contextclassid    the class id of the DomainContext's context entity, 0 if all contexts
 * long           domainContextId         contextid         the id of DomainContext's context object, 0 if all instances
 * int            granteeClassId          granteeclassid    the class id of the entity the permissions are granted to, 0 if all classes
 * long           granteeId               granteeid         the id of the entity the permissions are granted to, 0 if all grantees
 * int            priority                secprio           the priority or evaluation order, 0 is highest or first
 * String(128)    permissions             permissions       the permissions as a comma-separated list
 * boolean        allowed                 allowed           the false if denied, true if allowed
 * String         message                 message           the user message
 *
 * ## indexes
 * index entity := objectid, objectclassid
 * index class := objectclass
 * index context := contextid, contextclassid
 * index grantee := granteeid, granteeclassid
 *
 * @<
 */


/**
 * A security rule.
 * <p>
 * This is the entity used by the default implementation {@link DefaultSecurityManager}.
 * Applications may use a completely different {@link org.tentackle.security.SecurityManager} not using {@link Security}-entities at all.
 * <p>
 * Any object, whether it is a {@link PersistentDomainObject} or any
 * other class (for example a dialog), can be secured by rules.<br>
 * Security rules grant or deny a permission to a grantee object or a grantee class.
 * The grantee is an entity like a user or a user group.<br>
 * Optionally, rules can be restricted to some context entities or context class.
 * Grantees and contexts must be of type {@link PersistentDomainObject}.<br>
 * Permissions are stored in the database as comma-separated strings and are converted
 * to instances of {@link org.tentackle.security.Permission} mapped by the {@link org.tentackle.security.SecurityFactory}.<br>
 * Security rules also have a priority, can explain the denial to the user
 * by means of a message text and can easily be extended by the application.
 *
 * @author harald
 */
@TableName(/*@*/"secrules"/*@*/) // @wurblet < Inject --string $tablename
@ClassId(/*@*/5/*@*/) // @wurblet < Inject $classid
@Singular("security rule")
@Plural("security rules")
@SecurityValidator
public interface Security extends PersistentDomainObject<Security>, SecurityDomain, SecurityPersistence {

}
