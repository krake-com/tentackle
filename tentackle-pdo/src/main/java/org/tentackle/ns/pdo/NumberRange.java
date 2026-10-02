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

import org.tentackle.session.ClassId;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.Plural;
import org.tentackle.pdo.Singular;
import org.tentackle.session.TableName;


/*
 * @> $mapping
 *
 * # number range
 * name := $classname
 * id := $classid
 * table := $tablename
 * alias := nrange
 * integrity := none
 *
 * ## attributes
 * [remote, fulltracked]
 *
 * long           numberPoolId    poolid            ID of the number pool
 * long           begin           rbegin            begin of range
 * long           end             rend              end of range
 *
 * ## indexes
 *
 * ## validations
 *
 * ## relations
 * NumberPool:
 *
 * @<
 */


/**
 * A number range.
 *
 * @author harald
 */
@TableName(/*@*/"numrange"/*@*/) // @wurblet < Inject --string $tablename
@ClassId(/*@*/7/*@*/) // @wurblet < Inject $classid
@Singular("number range")
@Plural("number ranges")
public interface NumberRange extends PersistentDomainObject<NumberRange>, NumberRangeDomain, NumberRangePersistence {

}
