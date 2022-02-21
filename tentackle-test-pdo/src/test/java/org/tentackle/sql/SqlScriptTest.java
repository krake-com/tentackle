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

import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.Test;

import org.tentackle.dbms.Db;
import org.tentackle.dbms.StatementWrapper;
import org.tentackle.pdo.testng.AbstractPdoTest;

import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Physically runs a script against the test backend.
 */
public class SqlScriptTest extends AbstractPdoTest {

  private static final String SCRIPT = """
      -- blah already dropped, if exists
      CREATE TABLE blah (
          name VARCHAR(128),
          id INT8 NOT NULL PRIMARY KEY
      );
      
      COMMENT ON TABLE blah IS 'just a blah table';
      COMMENT ON COLUMN blah.name IS 'the blah name';
      COMMENT ON COLUMN blah.id IS 'object id';
      
      CREATE INDEX blah_name ON blah (name);
      
      INSERT INTO blah(name, id) VALUES('doedel1', 1);
      INSERT INTO blah(name, id) VALUES('didoedel2', 2);
      INSERT INTO blah(name, id) VALUES('didoedeldu3', 3);
      
      SELECT * FROM blah ORDER BY id;
      UPDATE blah SET id=id+1000;
      DELETE FROM blah WHERE id > 1001;
      
      DROP TABLE blah;    -- cleanup!
      
      """;

  private static final String OUTPUT = """
      CREATE TABLE blah (
          name VARCHAR(128),
          id INT8 NOT NULL PRIMARY KEY
      ) -> 0
      COMMENT ON TABLE blah IS 'just a blah table' -> 0
      COMMENT ON COLUMN blah.name IS 'the blah name' -> 0
      COMMENT ON COLUMN blah.id IS 'object id' -> 0
      CREATE INDEX blah_name ON blah (name) -> 0
      INSERT INTO blah(name, id) VALUES('doedel1', 1) -> 1
      INSERT INTO blah(name, id) VALUES('didoedel2', 2) -> 1
      INSERT INTO blah(name, id) VALUES('didoedeldu3', 3) -> 1
      SELECT * FROM blah ORDER BY id ->
       name        id
       doedel1     1\s
       didoedel2   2\s
       didoedeldu3 3\s
      UPDATE blah SET id=id+1000 -> 3
      DELETE FROM blah WHERE id > 1001 -> 2
      DROP TABLE blah -> 0
      """;



  @Test(groups = "local")
  public void runScript() {
    if (getSession() instanceof Db) {
      Db db = (Db) getSession();    // this is really low level...
      try (StatementWrapper statement = db.createStatement()) {
        // in case test failed in the middle before
        statement.executeUpdate("DROP TABLE IF EXISTS blah");
      }
      // we're within a transaction -> connection is attached
      ScriptRunner scriptRunner = db.getBackend().createScriptRunner(db.connection().getConnection());
      List<ScriptRunnerResult> results = scriptRunner.run(SCRIPT);
      assertEquals(results.size(), 12);
      int ndx = 1;
      StringBuilder buf = new StringBuilder();
      for (ScriptRunnerResult result : results) {
        Reporter.log(ndx++ + ": " + result + " <br>");
        buf.append(result).append('\n');
      }
      assertEquals(buf.toString(), OUTPUT);
    }
    else {
      throw new SkipException("session is not a Db instance");    // ... for whatever reasons
    }
  }

}
