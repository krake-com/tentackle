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
import java.util.Locale;

import static org.testng.Assert.assertEquals;

/**
 * Physically runs a script against the test backend.
 * <p>
 * Aborting a running script is covered by {@link PostgresScriptRunnerTest}, which needs a real
 * server to sleep on.
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
      INSERT INTO blah(name, id) VALUES('didoedel2', 20);
      INSERT INTO blah(name, id) VALUES('didoedeldu3', 300);
      
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
      INSERT INTO blah(name, id) VALUES('didoedel2', 20) -> 1
      INSERT INTO blah(name, id) VALUES('didoedeldu3', 300) -> 1
      SELECT * FROM blah ORDER BY id ->
      name        id\s
      doedel1       1
      didoedel2    20
      didoedeldu3 300
      UPDATE blah SET id=id+1000 -> 3
      DELETE FROM blah WHERE id > 1001 -> 2
      DROP TABLE blah -> 0
      """;


  public SqlScriptTest() {
    super(TransactionType.METHOD);
  }

  @Test(groups = "local")
  public void runScript() {
    Reporter.log("SqlScriptTest.runScript <br>", true);

    if (getSession() instanceof Db db) {
      try (StatementWrapper statement = db.createStatement()) {
        // in case the test failed in the middle before
        statement.executeUpdate("DROP TABLE IF EXISTS blah");
      }
      List<ScriptRunnerResult> results = db.runScript("sqlScriptTest", SCRIPT);
      assertEquals(results.size(), 12);
      int ndx = 1;
      StringBuilder buf = new StringBuilder();
      for (ScriptRunnerResult result : results) {
        Reporter.log(ndx++ + ": " + result + " <br>", true);
        buf.append(result).append('\n');
      }
      // The column headers of the SELECT come back in the case the backend folds unquoted
      // identifiers to: upper case for H2, lower case for PostgreSQL. That says nothing about
      // the script runner, so compare without regard to case.
      assertEquals(buf.toString().toLowerCase(Locale.ROOT), OUTPUT.toLowerCase(Locale.ROOT));
    }
    else {
      throw new SkipException("session is not a Db instance");    // ... for whatever reason
    }
  }

}
