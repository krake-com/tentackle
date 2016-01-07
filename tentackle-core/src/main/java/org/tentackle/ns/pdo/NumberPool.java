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


package org.tentackle.ns.pdo;

import org.tentackle.pdo.ClassId;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.TableName;


/**
 * @> $mapping
 *
 * # number pool
 * name := $classname
 * id := $classid
 * table := $tablename
 * alias := npool
 * integrity := none
 *
 * ## attributes
 * [root, remote, fulltracked]
 *
 * String(30)     name            name              the number pool name [key]
 * String(80)     realm           realm             pool realm, optional [MAPNULL]
 * String         description     description       short description
 * boolean        online          poolonline        true if online, else offline
 * long           lowWaterMark    lowmark           minimum number count before request to fill up from uplink, 0 if disable
 * long           requestSize     reqsize           number count to request from uplink, 0 if disable
 * String         uplink          uplink            uplink configuration (optional)
 *
 * ## indexes
 * unique index udk := name, realm
 *
 * ## validations
 * name: @NotNull
 *
 * uplink: @NotNull(condition="{object.lowWaterMark > 0 && object.requestSize > 0}")
 *
 * ## relations
 *
 * NumberRange:
 *  relation = composite list,
 *  delete = cascade
 *
 * @<
 */


/**
 * A number pool.
 * <p>
 *
 * @author harald
 */
@TableName(/**/"numpool"/**/) // @wurblet < Inject --string $tablename
@ClassId(/**/6/**/) // @wurblet < Inject $classid
public interface NumberPool extends PersistentDomainObject<NumberPool>, NumberPoolDomain, NumberPoolPersistence {

}
