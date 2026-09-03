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

package org.tentackle.maven.plugin.sql;

import org.apache.maven.plugin.MojoExecutionException;

import org.testng.annotations.Test;

import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@code run} goal.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class RunSqlMojoTest extends AbstractSqlDbMojoTest {

  @Test
  public void executesTheScriptAgainstTheBackend() throws Exception {
    File script = SqlTestSupport.writeFile(getModelDir().getParentFile(), "script.sql",
        "CREATE TABLE greeting (message VARCHAR(50));\n" +
        "INSERT INTO greeting VALUES ('hello');\n");

    RunSqlMojo mojo = configure(new RunSqlMojo(), "executesTheScriptAgainstTheBackend");
    SqlTestSupport.setField(mojo, "script", script);

    run(mojo);

    try (java.sql.Connection con = java.sql.DriverManager.getConnection(URL);
         Statement stmt = con.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT message FROM greeting")) {
      assertTrue(rs.next());
      assertEquals(rs.getString(1), "hello");
    }
    assertTrue(getLog().getInfoMessages().stream().anyMatch(msg -> msg.contains("INSERT INTO greeting")),
               getLog().getInfoMessages().toString());
  }

  @Test
  public void failsOnASqlError() throws Exception {
    File script = SqlTestSupport.writeFile(getModelDir().getParentFile(), "script.sql",
        "CREATE TABLE greeting (message VARCHAR(50));\n" +
        "SELECT * FROM no_such_table;\n");

    RunSqlMojo mojo = configure(new RunSqlMojo(), "failsOnASqlError");
    SqlTestSupport.setField(mojo, "script", script);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, () -> run(mojo));
    assertTrue(ex.getMessage().contains("SQL script execution failed"), ex.getMessage());
  }

}
