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

package org.tentackle.maven.plugin.wizard;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests the extraction of {@code @}-marked import lines from a script fragment.
 */
public class ScriptMojoTest {

  @Test
  public void testExtractImports() {
    ScriptMojo.FragmentParts parts = ScriptMojo.extractImports(
        """
        @import org.tentackle.misc.*
        def today = LocalDate.now()
          @import java.time.LocalDate
        println today""");
    assertEquals(parts.imports(), "import org.tentackle.misc.*\nimport java.time.LocalDate");
    assertEquals(parts.content(), "def today = LocalDate.now()\nprintln today");
  }

  @Test
  public void testNoImports() {
    ScriptMojo.FragmentParts parts = ScriptMojo.extractImports("println 'hello'\nprintln 'world'\n");
    assertEquals(parts.imports(), "");
    assertEquals(parts.content(), "println 'hello'\nprintln 'world'\n");
  }

  @Test
  public void testMarkerIsLanguageAgnostic() {
    ScriptMojo.FragmentParts parts = ScriptMojo.extractImports(
        """
        @require 'json'
        puts JSON.generate({ answer: 42 })""");
    assertEquals(parts.imports(), "require 'json'");
    assertEquals(parts.content(), "puts JSON.generate({ answer: 42 })");
  }

  @Test
  public void testEmptyFragment() {
    ScriptMojo.FragmentParts parts = ScriptMojo.extractImports("");
    assertEquals(parts.imports(), "");
    assertEquals(parts.content(), "");
  }
}
