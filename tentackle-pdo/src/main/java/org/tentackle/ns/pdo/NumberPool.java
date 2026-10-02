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

package org.tentackle.ns.pdo;

import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.Plural;
import org.tentackle.pdo.Singular;
import org.tentackle.session.ClassId;
import org.tentackle.session.TableName;

/*
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
 * long           lowWaterMark    lowmark           minimum number count before request to fill up from uplink, 0 to disable
 * long           requestSize     reqsize           number count to request from uplink, 0 to disable
 * String         uplink          uplink            uplink configuration (optional)
 *
 * ## indexes
 * unique index udk := name, realm
 *
 * ## validations
 * name: @NotNull
 *
 * uplink: @NotNull(condition="$slave")
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
 *
 * @author harald
 */
@TableName(/*@*/"numpool"/*@*/) // @wurblet < Inject --string $tablename
@ClassId(/*@*/6/*@*/) // @wurblet < Inject $classid
@Singular("number pool")
@Plural("number pools")
public interface NumberPool extends PersistentDomainObject<NumberPool>, NumberPoolDomain, NumberPoolPersistence {

}
