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

package org.tentackle.script.ruby;

import org.jruby.embed.ScriptingContainer;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;

import java.util.Set;

/**
 * Tests for {@link RubyScript}.
 */
public class RubyScriptTest {

  private final RubyLanguage language = new RubyLanguage();
  private final ScriptingContainer container = new ScriptingContainer();

  @Test
  public void testValidateCompilesWithoutExecuting() {
    RubyScript script = new RubyScript(language, container, "1 / 0", false, false);
    script.validate();     // must compile fine, division by zero is only a runtime error
    Assert.assertThrows(ScriptRuntimeException.class, () -> script.execute());
  }

  @Test
  public void testExecuteWrapsRuntimeExceptionAsScriptRuntimeException() {
    RubyScript script = new RubyScript(language, container, "1 / 0", false, false);
    ScriptRuntimeException ex = Assert.expectThrows(ScriptRuntimeException.class, script::execute);
    Assert.assertNotNull(ex.getCause());
  }

  @Test
  public void testExecuteWithoutVariables() {
    RubyScript script = new RubyScript(language, container, "21.0 * 2.0", false, false);
    double result = script.execute((Set<ScriptVariable>) null);
    Assert.assertEquals(result, 42.0);
  }

  @Test
  public void testExecuteWithVariables() {
    RubyScript script = new RubyScript(language, container, "@a + @b", false, false);
    double result = script.execute(new ScriptVariable("a", 3.0), new ScriptVariable("b", 4.0));
    Assert.assertEquals(result, 7.0);
  }

  @Test
  public void testVariablesAreResetAfterExecution() {
    RubyScript bind = new RubyScript(language, container, "@leaked * 2", false, false);
    long result = bind.execute(new ScriptVariable("leaked", 21));
    Assert.assertEquals(result, 42L);
    RubyScript probe = new RubyScript(language, container, "@leaked", false, false);
    Assert.assertNull(probe.execute());
  }

  @Test
  public void testVariablesAreResetAfterFailedExecution() {
    RubyScript bind = new RubyScript(language, container, "@leakedOnError / 0", false, false);
    Assert.assertThrows(ScriptRuntimeException.class, () -> bind.execute(new ScriptVariable("leakedOnError", 1)));
    RubyScript probe = new RubyScript(language, container, "@leakedOnError", false, false);
    Assert.assertNull(probe.execute());
  }

  @Test
  public void testInvalidSyntaxThrowsScriptRuntimeExceptionOnValidate() {
    RubyScript script = new RubyScript(language, container, "def foo(", false, false);
    ScriptRuntimeException ex = Assert.expectThrows(ScriptRuntimeException.class, script::validate);
    Assert.assertTrue(ex.getMessage().startsWith("compiling script failed"), ex.getMessage());
    Assert.assertNotNull(ex.getCause());
  }

  @Test
  public void testCompiledScriptIsMemoizedPerInstance() {
    RubyScript script = new RubyScript(language, container, "1 + 1 # unique_memoized_" + System.nanoTime(), false, false);
    CompiledRubyScript first = script.getCompiledScript();
    CompiledRubyScript second = script.getCompiledScript();
    Assert.assertSame(first, second);
  }

  @Test
  public void testCachedTrueSharesCompiledScriptAcrossInstances() {
    String code = "1 + 1 # unique_cached_" + System.nanoTime();
    RubyScript script1 = new RubyScript(language, container, code, true, false);
    RubyScript script2 = new RubyScript(language, container, code, true, false);
    Assert.assertSame(script1.getCompiledScript(), script2.getCompiledScript());
  }

  @Test
  public void testCachedFalseCreatesDistinctCompiledScriptPerInstance() {
    String code = "1 + 1 # unique_uncached_" + System.nanoTime();
    RubyScript script1 = new RubyScript(language, container, code, false, false);
    RubyScript script2 = new RubyScript(language, container, code, false, false);
    Assert.assertNotSame(script1.getCompiledScript(), script2.getCompiledScript());
  }

  @Test
  public void testToStringContainsLanguageCodeAndFlags() {
    RubyScript script = new RubyScript(language, container, "1 + 1", true, true);
    String string = script.toString();
    Assert.assertTrue(string.contains("#!Ruby{"), string);
    Assert.assertTrue(string.contains("1 + 1"), string);
    Assert.assertTrue(string.contains("(cached)"), string);
    Assert.assertTrue(string.contains("(mt)"), string);
  }

  @Test
  public void testToStringWithoutCachingOrThreadSafetyOmitsMarkers() {
    RubyScript script = new RubyScript(language, container, "1 + 1", false, false);
    String string = script.toString();
    Assert.assertFalse(string.contains("(cached)"), string);
    Assert.assertFalse(string.contains("(mt)"), string);
  }

}
