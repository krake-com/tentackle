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
 */

package org.tentackle.sql.backends;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.sql.JoinType;

/**
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class PostgresTest {

  @Test
  public void testJoins() {
    StringBuilder select = new StringBuilder("SELECT vg.* FROM tx.vorgang vg WHERE 1=1 AND vg.id=123456");
    String joinSelect = "SELECT ou.*,mg.*,mr.* FROM md.orgunit ou LEFT JOIN md.magruppe mg ON ou.id=mg.id LEFT JOIN md.mitarbeiter mr ON ou.id=mr.id";
    String joinSelectIdAlias = "ou.id AS ou_id";
    String joinAlias = "_j1";
    String join = "vg.orgunitid=_j1.ou_id";
    Postgres pg = new Postgres();
    pg.sqlJoinSelects(JoinType.LEFT, true, select, joinSelect, joinSelectIdAlias, joinAlias, join);
    Assert.assertEquals(select.toString(),
            "SELECT vg.*,_j1.* FROM tx.vorgang vg LEFT JOIN (SELECT ou.*,mg.*,mr.*,ou.id AS ou_id FROM md.orgunit ou LEFT JOIN md.magruppe mg ON ou.id=mg.id LEFT JOIN md.mitarbeiter mr ON ou.id=mr.id) _j1 ON vg.orgunitid=_j1.ou_id WHERE 1=1 AND vg.id=123456");

    select = new StringBuilder("SELECT ou.*,mr.* FROM md.orgunit ou,md.mitarbeiter mr WHERE 1=1 AND ou.id=mr.id AND ou.id=555");
    joinSelect = "SELECT kt.* FROM md.kontakt kt WHERE kt.classid=112";
    join = "ou_id=kt.id";
    pg.sqlJoinSelects(JoinType.LEFT, true, select, joinSelect, null, joinAlias, join);
    Assert.assertEquals(select.toString(),
            "SELECT ou.*,mr.*,_j1.* FROM md.orgunit ou,md.mitarbeiter mr LEFT JOIN (SELECT kt.* FROM md.kontakt kt WHERE kt.classid=112) _j1 ON ou_id=kt.id WHERE 1=1 AND ou.id=mr.id AND ou.id=555");
  }

}
