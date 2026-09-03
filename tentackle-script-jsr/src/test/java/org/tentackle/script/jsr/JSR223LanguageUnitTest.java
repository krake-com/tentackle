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

import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Direct unit tests for {@link JSR223Language}, decoupled from whatever concrete
 * script engines happen to be on the test classpath by faking the {@link ScriptEngineFactory}
 * metadata while delegating actual execution to the real Groovy engine.
 */
@SuppressWarnings("missing-explicit-ctor")
public class JSR223LanguageUnitTest {

  private static final ScriptEngine GROOVY_ENGINE = new ScriptEngineManager().getEngineByName("groovy");

  private static ScriptEngineFactory fakeFactory(String engineName, String languageName, String[] names, String threadingParameter) {
    return new ScriptEngineFactory() {
      @Override
      public String getEngineName() {
        return engineName;
      }

      @Override
      public String getEngineVersion() {
        return "1.0";
      }

      @Override
      public List<String> getExtensions() {
        return List.of();
      }

      @Override
      public List<String> getMimeTypes() {
        return List.of();
      }

      @Override
      public List<String> getNames() {
        return Arrays.asList(names);
      }

      @Override
      public String getLanguageName() {
        return languageName;
      }

      @Override
      public String getLanguageVersion() {
        return "1.0";
      }

      @Override
      public Object getParameter(String key) {
        return "THREADING".equals(key) ? threadingParameter : null;
      }

      @Override
      public String getMethodCallSyntax(String obj, String m, String... args) {
        return null;
      }

      @Override
      public String getOutputStatement(String toDisplay) {
        return null;
      }

      @Override
      public String getProgram(String... statements) {
        return null;
      }

      @Override
      public ScriptEngine getScriptEngine() {
        return GROOVY_ENGINE;
      }
    };
  }

  @Test
  public void testNameAndAbbreviationsDelegateToEngineFactory() {
    ScriptEngineFactory factory = fakeFactory("SomeEngine", "FakeLang", new String[] { "fakelang", "fl" }, null);
    JSR223Language language = new JSR223Language(factory);
    Assert.assertEquals(language.getName(), "FakeLang");
    Assert.assertEquals(language.getAbbreviations(), new String[] { "fakelang", "fl" });
    Assert.assertSame(language.getEngineFactory(), factory);
    Assert.assertNotNull(language.getEngine());
  }

  @Test
  public void testToStringReturnsName() {
    JSR223Language language = new JSR223Language(fakeFactory("SomeEngine", "FakeLang", new String[] { "fakelang" }, null));
    Assert.assertEquals(language.toString(), "FakeLang");
  }

  @Test
  public void testEqualsAndHashCodeAreBasedOnName() {
    JSR223Language language1 = new JSR223Language(fakeFactory("Engine1", "SameName", new String[] { "a" }, null));
    JSR223Language language2 = new JSR223Language(fakeFactory("Engine2", "SameName", new String[] { "b" }, null));
    JSR223Language language3 = new JSR223Language(fakeFactory("Engine3", "OtherName", new String[] { "c" }, null));
    Assert.assertEquals(language1, language2);
    Assert.assertEquals(language1.hashCode(), language2.hashCode());
    Assert.assertNotEquals(language1, language3);
    Assert.assertNotEquals(language1, null);
    Assert.assertNotEquals(language1, "SameName");
  }

  @Test
  public void testJRubyEngineIsNeverThreadSafeRegardlessOfThreadingParameterAndTranslatesVariables() {
    // claims THREAD_ISOLATED via the THREADING parameter, but JSR223Language distrusts it
    JSR223Language language = new JSR223Language(fakeFactory("JRuby", "ruby", new String[] { "ruby" }, "THREAD_ISOLATED"));
    Assert.assertFalse(language.isThreadSafe());
    Assert.assertEquals(language.createLocalVariableReference("object"), "@object");
  }

  @Test
  public void testNashornEngineIsThreadSafeEvenWithoutThreadingParameter() {
    JSR223Language language = new JSR223Language(fakeFactory("Oracle Nashorn", "ECMAScript", new String[] { "js" }, null));
    Assert.assertTrue(language.isThreadSafe());
    Assert.assertEquals(language.createLocalVariableReference("object"), "object");
  }

  @Test
  public void testGenericEngineThreadSafetyFollowsThreadingParameter() {
    JSR223Language threadSafe = new JSR223Language(fakeFactory("Generic", "Generic", new String[] { "generic" }, "MULTITHREADED"));
    Assert.assertTrue(threadSafe.isThreadSafe());

    JSR223Language notThreadSafe = new JSR223Language(fakeFactory("Generic", "Generic", new String[] { "generic" }, null));
    Assert.assertFalse(notThreadSafe.isThreadSafe());
  }

  @Test
  public void testGenericEngineKeepsVariableReferencesUnchanged() {
    JSR223Language language = new JSR223Language(fakeFactory("Generic", "Generic", new String[] { "generic" }, null));
    Assert.assertEquals(language.createLocalVariableReference("object"), "object");
  }

  @Test
  public void testCreateScriptAppliesConverterProviderAndExecutes() {
    JSR223Language language = new JSR223Language(fakeFactory("Generic", "Generic", new String[] { "generic" }, null));
    Function<String, ScriptConverter> converterProvider =
        name -> "Generic".equals(name) ? (code, lang) -> code + " * 2" : null;
    Script script = language.createScript("21", false, false, converterProvider);
    Assert.assertEquals(script.getCode(), "21 * 2");
    int result = script.execute();
    Assert.assertEquals(result, 42);
  }

  @Test
  public void testCreateScriptWithoutConverterProviderKeepsCodeUnchanged() {
    JSR223Language language = new JSR223Language(fakeFactory("Generic", "Generic", new String[] { "generic" }, null));
    Script script = language.createScript("21", false, false, null);
    Assert.assertEquals(script.getCode(), "21");
  }

}
