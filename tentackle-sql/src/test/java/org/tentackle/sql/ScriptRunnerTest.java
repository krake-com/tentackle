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

package org.tentackle.sql;

import org.testng.annotations.Test;

import org.tentackle.sql.backends.MySql;
import org.tentackle.sql.backends.Oracle;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Testing the text processing of the script runner.<br>
 * For a JDBC-related test see SqlScriptTest in tentackle-test-pdo.
 */
public class ScriptRunnerTest {

  private static final String SCRIPT_1 = """
      
      INSERT INTO blah(a, b, c) VALUES ('A', 'B', 100);   -- some comment
      
      -- other comment with ; semicolon
      INSERT INTO blah(d, e, "fo;o") VALUES ('D', 'Ernie''s', 200);
      
      -- blank lines
      
      
      
      """;

  private static final String SCRIPT_2 = """
      
      INSERT INTO blah(a, b, c) VALUES ('A', 'B', 100);   /* some comment ;
      
         other comment */
      INSERT INTO blah(d, e, "fo;o") VALUES ('D', 'Ernie''s', 200);
      
      /* blank lines
      with
          /* embedded garbage 'kjhsdfkd 
          
          "kljsdfskld
      
      */
      """;

  private static final String SCRIPT_3 = """
      --'"
      INSERT INTO blah(a, b, c) VALUES ('A', 'B', 100);   /* some comment
      ; '
      with posix-escaped end of " comment!
          \\*/
         other comment */
      INSERT INTO blah(d, e, "fo;o") VALUES ('D', 'Ernie''s', 200);
      
      -- doedel
      
      """;

  private static final String SCRIPT_4 = """
      -- some real SQL...
      
      CREATE TABLE secrules ( -- security ACLs
          objectclass VARCHAR(128), -- the protected classname, null if an entity
          objectclassid INT4 NOT NULL, -- the protected object's class id, 0 if not an entity
          objectid INT8 NOT NULL, -- the id of the protected object, 0 if all instances or not an entity
          contextclassid INT4 NOT NULL, -- the class id of the DomainContext's context entity, 0 if all contexts
          contextid INT8 NOT NULL, -- the id of DomainContext's context object, 0 if all instances
          granteeclassid INT4 NOT NULL, -- the class id of the entity the permissions are granted to, null if all classes
          granteeid INT8 NOT NULL, -- the id of the entity the permissions are granted to, 0 if all grantees
          secprio INT4 NOT NULL, -- the priority or evaluation order, 0 is highest or first
          permissions VARCHAR(128), -- the permissions as a comma-separated list
          allowed BOOL NOT NULL, -- the false if denied, true if allowed
          message TEXT, -- the user message
          id INT8 NOT NULL PRIMARY KEY, -- object id
          serial INT8 NOT NULL DEFAULT 1 -- object serial
      );
      
      COMMENT ON TABLE secrules IS 'security ACLs';
      COMMENT ON COLUMN secrules.objectclass IS 'the protected classname, null if an entity';
      COMMENT ON COLUMN secrules.objectclassid IS 'the protected object''s class id, 0 if not an entity';
      COMMENT ON COLUMN secrules.objectid IS 'the id of the protected object, 0 if all instances or not an entity';
      COMMENT ON COLUMN secrules.contextclassid IS 'the class id of the DomainContext''s context entity, 0 if all contexts';
      COMMENT ON COLUMN secrules.contextid IS 'the id of DomainContext''s context object, 0 if all instances';
      COMMENT ON COLUMN secrules.granteeclassid IS 'the class id of the entity the permissions are granted to, null if all classes';
      COMMENT ON COLUMN secrules.granteeid IS 'the id of the entity the permissions are granted to, 0 if all grantees';
      COMMENT ON COLUMN secrules.secprio IS 'the priority or evaluation order, 0 is highest or first';
      COMMENT ON COLUMN secrules.permissions IS 'the permissions as a comma-separated list';
      COMMENT ON COLUMN secrules.allowed IS 'the false if denied, true if allowed';
      COMMENT ON COLUMN secrules.message IS 'the user message';
      COMMENT ON COLUMN secrules.id IS 'object id';
      COMMENT ON COLUMN secrules.serial IS 'object serial';
      
      CREATE INDEX secrules_entity ON secrules (objectid, objectclassid);
      CREATE INDEX secrules_class ON secrules (objectclass);
      CREATE INDEX secrules_context ON secrules (contextid, contextclassid);
      CREATE INDEX secrules_grantee ON secrules (granteeid, granteeclassid);
      
      -- ?
      """;

  @Test
  public void testScriptSplitting() {
    // MySql supports posix escapes
    DefaultScriptRunner scriptRunner = new DefaultScriptRunner(new MySql(), null);   // connection not used by this test
    checkResults123(split(scriptRunner, SCRIPT_1));
    checkResults123(split(scriptRunner, SCRIPT_2));
    checkResults123(split(scriptRunner, SCRIPT_3));
    // same w/o posix escapes should produce garbage
    List<String> sqlList = split(new DefaultScriptRunner(new Oracle(), null), SCRIPT_3);
    assertEquals(sqlList.get(1), "other comment */\nINSERT INTO blah(d, e, \"fo;o\") VALUES ('D', 'Ernie''s', 200)");

    sqlList = split(scriptRunner, SCRIPT_4);
    assertEquals(sqlList.size(), 19);
    assertEquals(sqlList.get(0), "CREATE TABLE secrules ( \n" +
                                 "    objectclass VARCHAR(128), \n" +
                                 "    objectclassid INT4 NOT NULL, \n" +
                                 "    objectid INT8 NOT NULL, \n" +
                                 "    contextclassid INT4 NOT NULL, \n" +
                                 "    contextid INT8 NOT NULL, \n" +
                                 "    granteeclassid INT4 NOT NULL, \n" +
                                 "    granteeid INT8 NOT NULL, \n" +
                                 "    secprio INT4 NOT NULL, \n" +
                                 "    permissions VARCHAR(128), \n" +
                                 "    allowed BOOL NOT NULL, \n" +
                                 "    message TEXT, \n" +
                                 "    id INT8 NOT NULL PRIMARY KEY, \n" +
                                 "    serial INT8 NOT NULL DEFAULT 1 \n" +
                                 ")");
    assertEquals(sqlList.get(3), "COMMENT ON COLUMN secrules.objectclassid IS 'the protected object''s class id, 0 if not an entity'");
    assertEquals(sqlList.get(18), "CREATE INDEX secrules_grantee ON secrules (granteeid, granteeclassid)");
  }

  private void checkResults123(List<String> sqlList) {
    assertEquals(sqlList.size(), 2);
    assertEquals(sqlList.get(0), "INSERT INTO blah(a, b, c) VALUES ('A', 'B', 100)");
    assertEquals(sqlList.get(1), "INSERT INTO blah(d, e, \"fo;o\") VALUES ('D', 'Ernie''s', 200)");
  }

  private List<String> split(DefaultScriptRunner scriptRunner, String script) {
    List<String> sqls = new ArrayList<>();
    DefaultScriptRunner.SQLCode sqlCode;
    int offset = 0;
    while ((sqlCode = scriptRunner.determineNextSqlCode(script, offset)) != null) {
      if (!sqlCode.sql().isBlank()) {
        sqls.add(sqlCode.sql());
      }
      offset = sqlCode.nextOffset();
    }
    return sqls;
  }

}
