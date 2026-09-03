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

package org.tentackle.script.jsr;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptVariable;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.Set;

/**
 * Tests for {@link JSR223Script}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class JSR223ScriptTest {

  private static ScriptEngineFactory groovyFactory() {
    return new ScriptEngineManager().getEngineFactories().stream()
        .filter(f -> f.getNames().contains("groovy"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no groovy JSR223 engine on test classpath"));
  }

  private final JSR223Language language = new JSR223Language(groovyFactory());

  @Test
  public void testValidateCompilesWithoutExecuting() {
    JSR223Script script = new JSR223Script(language, "1 / 0", false, false);
    script.validate();     // must compile fine, division by zero is only a runtime error
    Assert.assertThrows(ScriptRuntimeException.class, () -> script.execute());
  }

  @Test
  public void testExecuteWrapsRuntimeExceptionAsScriptRuntimeException() {
    JSR223Script script = new JSR223Script(language, "1 / 0", false, false);
    ScriptRuntimeException ex = Assert.expectThrows(ScriptRuntimeException.class, script::execute);
    Assert.assertNotNull(ex.getCause());
  }

  @Test
  public void testExecuteWithoutVariables() {
    // 'd' suffix forces a Double literal - a bare decimal literal is a BigDecimal in Groovy
    JSR223Script script = new JSR223Script(language, "21.0d * 2.0d", false, false);
    double result = script.execute((Set<ScriptVariable>) null);
    Assert.assertEquals(result, 42.0);
  }

  @Test
  public void testExecuteWithVariables() {
    JSR223Script script = new JSR223Script(language, "a + b", false, false);
    double result = script.execute(new ScriptVariable("a", 3.0), new ScriptVariable("b", 4.0));
    Assert.assertEquals(result, 7.0);
  }

  @Test
  public void testInvalidSyntaxThrowsScriptRuntimeExceptionOnValidate() {
    JSR223Script script = new JSR223Script(language, "def foo(", false, false);
    ScriptRuntimeException ex = Assert.expectThrows(ScriptRuntimeException.class, script::validate);
    Assert.assertTrue(ex.getMessage().startsWith("compiling script failed"), ex.getMessage());
    Assert.assertNotNull(ex.getCause());
  }

  @Test
  public void testCompiledScriptIsMemoizedPerInstance() {
    JSR223Script script = new JSR223Script(language, "1 + 1 // unique_memoized_" + System.nanoTime(), false, false);
    CompiledJSR223Script first = script.getCompiledScript();
    CompiledJSR223Script second = script.getCompiledScript();
    Assert.assertSame(first, second);
  }

  @Test
  public void testCachedTrueSharesCompiledScriptAcrossInstances() {
    String code = "1 + 1 // unique_cached_" + System.nanoTime();
    JSR223Script script1 = new JSR223Script(language, code, true, false);
    JSR223Script script2 = new JSR223Script(language, code, true, false);
    Assert.assertSame(script1.getCompiledScript(), script2.getCompiledScript());
  }

  @Test
  public void testCachedFalseCreatesDistinctCompiledScriptPerInstance() {
    String code = "1 + 1 // unique_uncached_" + System.nanoTime();
    JSR223Script script1 = new JSR223Script(language, code, false, false);
    JSR223Script script2 = new JSR223Script(language, code, false, false);
    Assert.assertNotSame(script1.getCompiledScript(), script2.getCompiledScript());
  }

  @Test
  public void testToStringContainsLanguageCodeAndFlags() {
    JSR223Script script = new JSR223Script(language, "1 + 1", true, true);
    String string = script.toString();
    Assert.assertTrue(string.contains("1 + 1"), string);
    Assert.assertTrue(string.contains("(cached)"), string);
    Assert.assertTrue(string.contains("(mt)"), string);
  }

  @Test
  public void testToStringWithoutCachingOrThreadSafetyOmitsMarkers() {
    JSR223Script script = new JSR223Script(language, "1 + 1", false, false);
    String string = script.toString();
    Assert.assertFalse(string.contains("(cached)"), string);
    Assert.assertFalse(string.contains("(mt)"), string);
  }

}
