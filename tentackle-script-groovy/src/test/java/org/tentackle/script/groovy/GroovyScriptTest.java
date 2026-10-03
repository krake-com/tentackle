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

package org.tentackle.script.groovy;

import groovy.lang.GroovyClassLoader;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;

import java.util.Set;

/**
 * Tests for {@link GroovyScript}.
 */
public class GroovyScriptTest {

  private final GroovyLanguage language = new GroovyLanguage();
  private final GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader());


  @Test
  public void testValidateCompilesWithoutExecuting() {
    GroovyScript script = new GroovyScript(language, classLoader, "1 / 0", false, false);
    script.validate();     // must compile fine, division by zero is only a runtime error
    Assert.assertThrows(ScriptRuntimeException.class, () -> script.execute());
  }

  @Test
  public void testExecuteWrapsRuntimeExceptionAsScriptRuntimeException() {
    GroovyScript script = new GroovyScript(language, classLoader, "1 / 0", false, false);
    ScriptRuntimeException ex = Assert.expectThrows(ScriptRuntimeException.class, script::execute);
    Assert.assertTrue(ex.getCause() instanceof ArithmeticException, String.valueOf(ex.getCause()));
  }

  @Test
  public void testExecuteWithoutVariables() {
    GroovyScript script = new GroovyScript(language, classLoader, "21 * 2", false, false);
    int result = script.execute((Set<ScriptVariable>) null);
    Assert.assertEquals(result, 42);
  }

  @Test
  public void testExecuteWithVariables() {
    GroovyScript script = new GroovyScript(language, classLoader, "a + b", false, false);
    int result = script.execute(new ScriptVariable("a", 3), new ScriptVariable("b", 4));
    Assert.assertEquals(result, 7);
  }

  @Test
  public void testInvalidSyntaxThrowsScriptRuntimeExceptionOnValidate() {
    GroovyScript script = new GroovyScript(language, classLoader, "this is not } valid groovy (", false, false);
    ScriptRuntimeException ex = Assert.expectThrows(ScriptRuntimeException.class, script::validate);
    Assert.assertTrue(ex.getMessage().startsWith("compiling script failed"), ex.getMessage());
    Assert.assertNotNull(ex.getCause());
  }

  @Test
  public void testCompiledScriptIsMemoizedPerInstance() {
    GroovyScript script = new GroovyScript(language, classLoader, "unique_code_memoized_" + System.nanoTime(), false, false);
    CompiledGroovyScript first = script.getCompiledScript();
    CompiledGroovyScript second = script.getCompiledScript();
    Assert.assertSame(first, second);
  }

  @Test
  public void testCachedTrueSharesCompiledScriptAcrossInstances() {
    String code = "unique_code_cached_" + System.nanoTime();
    GroovyScript script1 = new GroovyScript(language, classLoader, code, true, false);
    GroovyScript script2 = new GroovyScript(language, classLoader, code, true, false);
    Assert.assertSame(script1.getCompiledScript(), script2.getCompiledScript());
  }

  @Test
  public void testCachedFalseCreatesDistinctCompiledScriptPerInstance() {
    String code = "unique_code_uncached_" + System.nanoTime();
    GroovyScript script1 = new GroovyScript(language, classLoader, code, false, false);
    GroovyScript script2 = new GroovyScript(language, classLoader, code, false, false);
    Assert.assertNotSame(script1.getCompiledScript(), script2.getCompiledScript());
  }

  @Test
  public void testToStringContainsLanguageCodeAndFlags() {
    GroovyScript script = new GroovyScript(language, classLoader, "1 + 1", true, true);
    String string = script.toString();
    Assert.assertTrue(string.contains("#!Groovy{"), string);
    Assert.assertTrue(string.contains("1 + 1"), string);
    Assert.assertTrue(string.contains("(cached)"), string);
    Assert.assertTrue(string.contains("(mt)"), string);
  }

  @Test
  public void testToStringWithoutCachingOrThreadSafetyOmitsMarkers() {
    GroovyScript script = new GroovyScript(language, classLoader, "1 + 1", false, false);
    String string = script.toString();
    Assert.assertFalse(string.contains("(cached)"), string);
    Assert.assertFalse(string.contains("(mt)"), string);
  }

}
