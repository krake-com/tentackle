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

package org.tentackle.script;

import org.testng.annotations.Test;

import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link AbstractScript}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractScriptTest {

  private final ScriptingLanguage language = new TestScriptingLanguage();

  private RecordingScript script(String code, boolean cached, boolean threadSafe) {
    return new RecordingScript(language, code, cached, threadSafe);
  }


  @Test
  public void testGetters() {
    RecordingScript script = script("a + b", true, false);
    assertSame(script.getLanguage(), language);
    assertEquals(script.getCode(), "a + b");
    assertTrue(script.isCached());
    assertFalse(script.isThreadSafe());
  }

  @Test
  public void testNullLanguageThrows() {
    assertThrows(NullPointerException.class, () -> new RecordingScript(null, "code", false, false));
  }

  @Test
  public void testNullCodeThrows() {
    assertThrows(NullPointerException.class, () -> new RecordingScript(language, null, false, false));
  }

  @Test
  public void testExecuteVarargsWithoutArgumentsPassesNull() {
    RecordingScript script = script("code", false, false);
    Object result = script.execute();
    assertTrue(script.executed);
    assertNull(script.lastVariables);
    assertNull(result);
  }

  @Test
  public void testExecuteVarargsWithNullArrayPassesNull() {
    RecordingScript script = script("code", false, false);
    script.execute((ScriptVariable[]) null);
    assertNull(script.lastVariables);
  }

  @Test
  public void testExecuteVarargsWrapsVariablesInSet() {
    RecordingScript script = script("code", false, false);
    ScriptVariable a = new ScriptVariable("a", 1);
    ScriptVariable b = new ScriptVariable("b", 2);
    Set<ScriptVariable> result = script.execute(a, b);
    assertEquals(script.lastVariables, Set.of(a, b));
    assertEquals(result, Set.of(a, b));
  }

  @Test
  public void testExecuteVarargsWithDuplicateNamesThrows() {
    RecordingScript script = script("code", false, false);
    // Set.of rejects duplicates and ScriptVariable equality is name-based
    assertThrows(IllegalArgumentException.class,
                 () -> script.execute(new ScriptVariable("a", 1), new ScriptVariable("a", 2)));
  }

  @Test
  public void testToStringPlain() {
    assertEquals(script("a + b", false, false).toString(), "#!Test{ a + b }");
  }

  @Test
  public void testToStringCachedAndThreadSafe() {
    assertEquals(script("a + b", true, true).toString(), "#!Test{ a + b } (cached) (mt)");
  }

  @Test
  public void testToStringReplacesNewlines() {
    assertEquals(script("a\nb", false, false).toString(), "#!Test{ a b }");
  }

  @Test
  public void testToStringTruncatesLongCode() {
    String code = "x".repeat(100);
    String result = script(code, false, false).toString();
    assertTrue(result.startsWith("#!Test{ " + "x".repeat(64)), result);
    assertTrue(result.endsWith(" ... }"), result);
  }
}
