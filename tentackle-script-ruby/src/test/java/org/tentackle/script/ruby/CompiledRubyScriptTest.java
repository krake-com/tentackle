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

import org.jruby.embed.EmbedEvalUnit;
import org.jruby.embed.ScriptingContainer;
import org.jruby.runtime.builtin.IRubyObject;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for {@link CompiledRubyScript}.
 */
public class CompiledRubyScriptTest {

  private final ScriptingContainer container = new ScriptingContainer();

  @Test
  public void testGetters() {
    String code = "1 + 1";
    EmbedEvalUnit unit = container.parse(code);
    CompiledRubyScript compiled = new CompiledRubyScript(container, code, unit);
    Assert.assertSame(compiled.getContainer(), container);
    Assert.assertEquals(compiled.getCode(), code);
    Assert.assertSame(compiled.getRubyScript(), unit);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRunEvaluatesCode() {
    String code = "1 == 1";
    EmbedEvalUnit unit = container.parse(code);
    CompiledRubyScript compiled = new CompiledRubyScript(container, code, unit);
    IRubyObject result = compiled.getRubyScript().run();
    Assert.assertTrue((Boolean) result.toJava(result.getJavaClass()));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRunResolvesVariablesBoundOnContainer() {
    container.put("@a", 3);
    container.put("@b", 4);
    String code = "@a + @b";
    EmbedEvalUnit unit = container.parse(code);
    CompiledRubyScript compiled = new CompiledRubyScript(container, code, unit);
    IRubyObject result = compiled.getRubyScript().run();
    Assert.assertEquals(result.toJava(result.getJavaClass()), 7L);
  }

}
