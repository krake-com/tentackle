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

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.script.ScriptRuntimeException;

/**
 * Tests for {@link CompiledGroovyScript}.
 */
@SuppressWarnings("unchecked")
public class CompiledGroovyScriptTest {

  private final GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader());


  @Test
  public void testGetCodeAndScriptClass() {
    String code = "1 + 1";
    Class<groovy.lang.Script> scriptClass = classLoader.parseClass(code);
    CompiledGroovyScript compiled = new CompiledGroovyScript(code, scriptClass);
    Assert.assertEquals(compiled.getCode(), code);
    Assert.assertSame(compiled.getScriptClass(), scriptClass);
  }

  @Test
  public void testPlainScriptFallsBackToDefaultConstructorAndSetBinding() {
    String code = "a + b";
    Class<groovy.lang.Script> scriptClass = classLoader.parseClass(code);
    CompiledGroovyScript compiled = new CompiledGroovyScript(code, scriptClass);
    Binding binding = new Binding();
    binding.setVariable("a", 3);
    binding.setVariable("b", 4);
    Object result = compiled.createInstance(binding).run();
    Assert.assertEquals(result, 7);
  }

  @Test
  public void testScriptClassWithExplicitBindingConstructorTakesPrecedence() {
    String code =
        "class BoundScript extends Script {\n" +
        "  BoundScript(Binding binding) { super(binding) }\n" +
        "  def run() { return binding.getVariable('a') + binding.getVariable('b') }\n" +
        "}";
    Class<groovy.lang.Script> scriptClass = classLoader.parseClass(code);
    CompiledGroovyScript compiled = new CompiledGroovyScript(code, scriptClass);
    Binding binding = new Binding();
    binding.setVariable("a", 10);
    binding.setVariable("b", 5);
    Object result = compiled.createInstance(binding).run();
    Assert.assertEquals(result, 15);
  }

  @Test
  public void testScriptClassWithoutDefaultOrBindingConstructorThrows() {
    String code =
        "class BadScript extends Script {\n" +
        "  BadScript(String s) { }\n" +
        "  def run() { return 1 }\n" +
        "}";
    Class<groovy.lang.Script> scriptClass = classLoader.parseClass(code);
    ScriptRuntimeException ex = Assert.expectThrows(ScriptRuntimeException.class,
        () -> new CompiledGroovyScript(code, scriptClass));
    Assert.assertTrue(ex.getMessage().contains("neither default- nor binding-constructor found"), ex.getMessage());
  }

}
