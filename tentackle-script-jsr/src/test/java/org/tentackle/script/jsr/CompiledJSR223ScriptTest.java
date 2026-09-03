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

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * Tests for {@link CompiledJSR223Script}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class CompiledJSR223ScriptTest {

  private final ScriptEngine engine = new ScriptEngineManager().getEngineByName("groovy");

  @Test
  public void testGetters() throws Exception {
    String code = "1 + 1";
    CompiledScript compiledScript = ((Compilable) engine).compile(code);
    CompiledJSR223Script compiled = new CompiledJSR223Script(code, compiledScript);
    Assert.assertEquals(compiled.getCode(), code);
    Assert.assertSame(compiled.getCompiledScript(), compiledScript);
  }

  @Test
  public void testCompiledScriptEvaluates() throws Exception {
    String code = "21 * 2";
    CompiledScript compiledScript = ((Compilable) engine).compile(code);
    CompiledJSR223Script compiled = new CompiledJSR223Script(code, compiledScript);
    Object result = compiled.getCompiledScript().eval();
    Assert.assertEquals(result, 42);
  }

}
