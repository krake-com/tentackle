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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertSame;

/**
 * Tests the {@link AbstractScriptingLanguage}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractScriptingLanguageTest {

  /** a minimal language whose name can be configured. */
  private static class NamedLanguage extends AbstractScriptingLanguage {
    private final String name;

    NamedLanguage(String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String[] getAbbreviations() {
      return new String[] {name};
    }

    @Override
    public Script createScript(String code, boolean cached, boolean threadSafe,
                               Function<String, ScriptConverter> converterProvider) {
      return new RecordingScript(this, getEffectiveCode(code, converterProvider), cached, threadSafe);
    }
  }

  /** a different concrete class carrying the same name, to test the class check in equals. */
  private static class OtherLanguage extends NamedLanguage {
    OtherLanguage(String name) {
      super(name);
    }
  }


  @Test
  public void testCreateLocalVariableReferenceReturnsNameUnchanged() {
    assertEquals(new NamedLanguage("L").createLocalVariableReference("myVar"), "myVar");
  }

  @Test
  public void testToStringIsName() {
    assertEquals(new NamedLanguage("Groovy").toString(), "Groovy");
  }

  @Test
  public void testEqualsAndHashCode() {
    NamedLanguage a = new NamedLanguage("L");
    NamedLanguage b = new NamedLanguage("L");
    NamedLanguage c = new NamedLanguage("M");

    assertEquals(a, a);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, c);
  }

  @Test
  public void testEqualsIsFalseForNullAndForeignType() {
    NamedLanguage a = new NamedLanguage("L");
    assertNotEquals(a, null);
    assertNotEquals(a, "L");
  }

  @Test
  public void testEqualsRequiresSameConcreteClass() {
    // same name but different concrete class must not be equal
    NamedLanguage a = new NamedLanguage("L");
    OtherLanguage b = new OtherLanguage("L");
    assertNotEquals(a, b);
  }

  @Test
  public void testGetEffectiveCodeWithoutConverterProvider() {
    assertSame(new NamedLanguage("L").getEffectiveCode("code", null), "code");
  }

  @Test
  public void testGetEffectiveCodeWithNullConverter() {
    // the provider is asked but returns no converter -> code stays unchanged
    NamedLanguage language = new NamedLanguage("L");
    assertSame(language.getEffectiveCode("code", name -> null), "code");
  }

  @Test
  public void testGetEffectiveCodeAppliesConverter() {
    NamedLanguage language = new NamedLanguage("MyLang");
    AtomicReference<String> requestedLanguage = new AtomicReference<>();
    Function<String, ScriptConverter> provider = name -> {
      requestedLanguage.set(name);
      return (code, lang) -> code.toUpperCase();
    };
    assertEquals(language.getEffectiveCode("hello", provider), "HELLO");
    // the converter provider is keyed by the language name
    assertEquals(requestedLanguage.get(), "MyLang");
  }

  @Test
  public void testGetEffectiveCodePassesLanguageToConverter() {
    NamedLanguage language = new NamedLanguage("L");
    AtomicReference<ScriptingLanguage> received = new AtomicReference<>();
    Function<String, ScriptConverter> provider = name -> (code, lang) -> {
      received.set(lang);
      return code;
    };
    language.getEffectiveCode("code", provider);
    assertSame(received.get(), language);
  }
}
