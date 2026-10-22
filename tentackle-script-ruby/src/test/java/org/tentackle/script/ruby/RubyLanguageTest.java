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

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptingLanguage;

import java.util.function.Function;

/**
 * Tests for {@link RubyLanguage}.
 */
public class RubyLanguageTest {

  @Test
  public void testName() {
    RubyLanguage language = new RubyLanguage();
    Assert.assertEquals(language.getName(), "Ruby");
    Assert.assertEquals(language.toString(), "Ruby");
  }

  @Test
  public void testAbbreviations() {
    RubyLanguage language = new RubyLanguage();
    Assert.assertEquals(language.getAbbreviations(), new String[] { "Ruby", "jruby", "rb", "ruby" });
  }

  @Test
  public void testResolvedViaScriptFactoryByNameAndAbbreviations() {
    ScriptingLanguage byName = ScriptFactory.getInstance().getLanguage("ruby");
    ScriptingLanguage byRb = ScriptFactory.getInstance().getLanguage("rb");
    ScriptingLanguage byJRuby = ScriptFactory.getInstance().getLanguage("JRuby");
    Assert.assertSame(byName, byRb);
    Assert.assertSame(byName, byJRuby);
    Assert.assertEquals(byName.getName(), RubyLanguage.NAME);
    Assert.assertTrue(ScriptFactory.getInstance().isLanguageAvailable("RB"));
    Assert.assertTrue(ScriptFactory.getInstance().isLanguageAvailable("Ruby"));
    Assert.assertFalse(ScriptFactory.getInstance().isLanguageAvailable("no-such-language"));
  }

  @Test
  public void testEqualsAndHashCode() {
    RubyLanguage language1 = new RubyLanguage();
    RubyLanguage language2 = new RubyLanguage();
    Assert.assertNotSame(language1, language2);
    Assert.assertEquals(language1, language2);
    Assert.assertEquals(language1.hashCode(), language2.hashCode());
    Assert.assertNotEquals(language1, null);
    Assert.assertNotEquals(language1, "Ruby");
  }

  @Test
  public void testCreateLocalVariableReferencePrependsAtSign() {
    RubyLanguage language = new RubyLanguage();
    Assert.assertEquals(language.createLocalVariableReference("object"), "@object");
  }

  @Test
  public void testCreateScriptAppliesConverterProvider() {
    RubyLanguage language = new RubyLanguage();
    Function<String, ScriptConverter> converterProvider =
        name -> RubyLanguage.NAME.equals(name) ? (code, lang) -> code + " * 2.0" : null;
    Script script = language.createScript("21.0", false, false, converterProvider);
    Assert.assertEquals(script.getCode(), "21.0 * 2.0");
    double result = script.execute();
    Assert.assertEquals(result, 42.0);
  }

  @Test
  public void testCreateScriptWithoutConverterProviderKeepsCodeUnchanged() {
    RubyLanguage language = new RubyLanguage();
    Script script = language.createScript("21.0", false, false, null);
    Assert.assertEquals(script.getCode(), "21.0");
  }

  @Test
  public void testCreateScriptWithConverterProviderReturningNullKeepsCodeUnchanged() {
    RubyLanguage language = new RubyLanguage();
    Function<String, ScriptConverter> converterProvider = name -> null;
    Script script = language.createScript("21.0", false, false, converterProvider);
    Assert.assertEquals(script.getCode(), "21.0");
  }

}
