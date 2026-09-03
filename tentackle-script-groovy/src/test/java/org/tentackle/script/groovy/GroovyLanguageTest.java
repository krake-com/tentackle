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

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptingLanguage;

import java.util.function.Function;

/**
 * Tests for {@link GroovyLanguage}.
 */
public class GroovyLanguageTest {

  @Test
  public void testName() {
    GroovyLanguage language = new GroovyLanguage();
    Assert.assertEquals(language.getName(), "Groovy");
    Assert.assertEquals(language.toString(), "Groovy");
  }

  @Test
  public void testAbbreviations() {
    GroovyLanguage language = new GroovyLanguage();
    Assert.assertEquals(language.getAbbreviations(), new String[] { "Groovy", "gv" });
  }

  @Test
  public void testResolvedViaScriptFactoryByNameAndAbbreviation() {
    ScriptingLanguage byName = ScriptFactory.getInstance().getLanguage("groovy");
    ScriptingLanguage byAbbreviation = ScriptFactory.getInstance().getLanguage("gv");
    Assert.assertSame(byName, byAbbreviation);
    Assert.assertEquals(byName.getName(), GroovyLanguage.NAME);
    Assert.assertTrue(ScriptFactory.getInstance().isLanguageAvailable("GV"));
    Assert.assertTrue(ScriptFactory.getInstance().isLanguageAvailable("Groovy"));
    Assert.assertFalse(ScriptFactory.getInstance().isLanguageAvailable("no-such-language"));
  }

  @Test
  public void testEqualsAndHashCode() {
    GroovyLanguage language1 = new GroovyLanguage();
    GroovyLanguage language2 = new GroovyLanguage();
    Assert.assertNotSame(language1, language2);
    Assert.assertEquals(language1, language2);
    Assert.assertEquals(language1.hashCode(), language2.hashCode());
    Assert.assertNotEquals(language1, null);
    Assert.assertNotEquals(language1, "Groovy");
  }

  @Test
  public void testCreateLocalVariableReferenceIsIdentity() {
    GroovyLanguage language = new GroovyLanguage();
    Assert.assertEquals(language.createLocalVariableReference("object"), "object");
  }

  @Test
  public void testCreateScriptAppliesConverterProvider() {
    GroovyLanguage language = new GroovyLanguage();
    Function<String, ScriptConverter> converterProvider =
        name -> GroovyLanguage.NAME.equals(name) ? (code, lang) -> code + " * 2" : null;
    Script script = language.createScript("21", false, false, converterProvider);
    Assert.assertEquals(script.getCode(), "21 * 2");
    int result = script.execute();
    Assert.assertEquals(result, 42);
  }

  @Test
  public void testCreateScriptWithoutConverterProviderKeepsCodeUnchanged() {
    GroovyLanguage language = new GroovyLanguage();
    Script script = language.createScript("21", false, false, null);
    Assert.assertEquals(script.getCode(), "21");
  }

  @Test
  public void testCreateScriptWithConverterProviderReturningNullKeepsCodeUnchanged() {
    GroovyLanguage language = new GroovyLanguage();
    Function<String, ScriptConverter> converterProvider = name -> null;
    Script script = language.createScript("21", false, false, converterProvider);
    Assert.assertEquals(script.getCode(), "21");
  }

}
