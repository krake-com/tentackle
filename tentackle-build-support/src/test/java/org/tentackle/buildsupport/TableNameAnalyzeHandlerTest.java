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

package org.tentackle.buildsupport;

import org.testng.annotations.Test;

import javax.tools.Diagnostic;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link TableNameAnalyzeHandler}.<br>
 * Uses a local stand-in for {@code @TableName}, because tentackle-session is not a dependency of this module.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class TableNameAnalyzeHandlerTest {

  private static final String TABLE = """
      package test.tn;
      @org.tentackle.common.Analyze("org.tentackle.buildsupport.TableNameAnalyzeHandler")
      public @interface Table {
        String value() default "";
        boolean mapSchema() default false;
        String prefix() default "";
      }
      """;

  @Test
  public void mapsSchemaAndAddsPrefix() throws Exception {
    AnalyzeCompilation result = AnalyzeCompilation.process(Map.of(
        "test.tn.Table", TABLE,
        "test.tn.Plain", """
            package test.tn;
            @Table("md.blah")
            public class Plain {}
            """,
        "test.tn.Mapped", """
            package test.tn;
            @Table(value = "md.blah", mapSchema = true, prefix = "x")
            public class Mapped {}
            """));

    List<String> lines = result.serviceLines("META-INF/mapped-services/test.tn.Table");
    assertEquals(lines.size(), 2);
    assertTrue(lines.contains("test.tn.Plain = \"md.blah\""), lines.toString());
    assertTrue(lines.contains("test.tn.Mapped = \"xmd_blah\""), lines.toString());
  }

  /**
   * The error must name the annotation, not javac's internal symbol class.
   */
  @Test
  public void missingValueNamesTheAnnotation() throws Exception {
    AnalyzeCompilation result = AnalyzeCompilation.process(Map.of(
        "test.tn.Table", TABLE,
        "test.tn.NoValue", """
            package test.tn;
            @Table
            public class NoValue {}
            """));

    List<String> errors = result.messages(Diagnostic.Kind.ERROR);
    assertEquals(errors.size(), 1, errors.toString());
    assertTrue(errors.getFirst().startsWith("no value method in @Table "), errors.getFirst());
  }

}
