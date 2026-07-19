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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.function.Function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@link DefaultScriptFactory}.
 * <p>
 * The factory discovers {@link TestScriptingLanguage} through its regular {@code @Service}-based
 * {@code loadLanguages()} mechanism, so the language lookup, abbreviation normalization and script creation are
 * exercised end-to-end. Each test uses a fresh factory instance to keep the (mutable) default language isolated.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultScriptFactoryTest {

  private DefaultScriptFactory factory;

  @BeforeMethod
  public void setUp() {
    factory = new DefaultScriptFactory();
  }


  @Test
  public void testLanguageIsDiscovered() {
    assertTrue(factory.getLanguages().stream().anyMatch(l -> TestScriptingLanguage.NAME.equals(l.getName())),
               "test scripting language was not discovered");
  }

  @Test
  public void testGetLanguageByAbbreviation() {
    assertEquals(factory.getLanguage("ts").getName(), TestScriptingLanguage.NAME);
  }

  @Test
  public void testGetLanguageIsCaseInsensitive() {
    ScriptingLanguage lower = factory.getLanguage("ts");
    assertSame(factory.getLanguage("TS"), lower);
    assertSame(factory.getLanguage("Ts"), lower);
    // the second registered abbreviation refers to the same instance
    assertSame(factory.getLanguage("test"), lower);
    assertSame(factory.getLanguage("TEST"), lower);
  }

  @Test
  public void testGetUnknownLanguageThrows() {
    ScriptRuntimeException ex =
        expectThrows(ScriptRuntimeException.class, () -> factory.getLanguage("nosuchlang"));
    assertTrue(ex.getMessage().contains("nosuchlang"), ex.getMessage());
  }

  @Test
  public void testGetDefaultLanguageWhenNoneDefinedThrows() {
    assertThrows(ScriptRuntimeException.class, () -> factory.getLanguage(null));
  }

  @Test
  public void testIsLanguageAvailable() {
    assertTrue(factory.isLanguageAvailable("ts"));
    assertTrue(factory.isLanguageAvailable("TEST"));
    assertFalse(factory.isLanguageAvailable("nosuchlang"));
  }

  @Test
  public void testIsLanguageAvailableWithoutDefault() {
    // null/empty refers to the default language, which is undefined here
    assertFalse(factory.isLanguageAvailable(null));
    assertFalse(factory.isLanguageAvailable(""));
  }

  @Test
  public void testDefaultLanguageIsNullInitially() {
    assertNull(factory.getDefaultLanguage());
  }

  @Test
  public void testSetDefaultLanguageByInstance() {
    ScriptingLanguage language = factory.getLanguage("ts");
    factory.setDefaultLanguage(language);
    assertSame(factory.getDefaultLanguage(), language);
    // null and empty abbreviation now resolve to the default
    assertSame(factory.getLanguage(null), language);
    assertSame(factory.getLanguage(""), language);
    assertTrue(factory.isLanguageAvailable(null));
    assertTrue(factory.isLanguageAvailable(""));
  }

  @Test
  public void testSetDefaultLanguageByAbbreviation() {
    factory.setDefaultLanguage("ts");
    assertEquals(factory.getDefaultLanguage().getName(), TestScriptingLanguage.NAME);
  }

  @Test
  public void testSetDefaultLanguageByUnknownAbbreviationThrows() {
    assertThrows(ScriptRuntimeException.class, () -> factory.setDefaultLanguage("nosuchlang"));
  }

  @Test
  public void testCreateScript() {
    Script script = factory.createScript("ts", "a + b", true, false, null);
    assertTrue(script instanceof RecordingScript);
    assertEquals(script.getCode(), "a + b");
    assertEquals(script.getLanguage().getName(), TestScriptingLanguage.NAME);
    assertTrue(script.isCached());
    assertFalse(script.isThreadSafe());
  }

  @Test
  public void testCreateScriptUsingDefaultLanguage() {
    factory.setDefaultLanguage("ts");
    Script script = factory.createScript(null, "code", false, true, null);
    assertEquals(script.getLanguage().getName(), TestScriptingLanguage.NAME);
    assertTrue(script.isThreadSafe());
  }

  @Test
  public void testCreateScriptAppliesConverter() {
    Function<String, ScriptConverter> provider = name -> (code, language) -> code.toUpperCase();
    Script script = factory.createScript("ts", "hello", false, false, provider);
    assertEquals(script.getCode(), "HELLO");
  }

  @Test
  public void testCreateScriptWithNullCodeThrows() {
    assertThrows(NullPointerException.class, () -> factory.createScript("ts", null, false, false, null));
  }

  @Test
  public void testCreateScriptWithUnknownLanguageThrows() {
    assertThrows(ScriptRuntimeException.class,
                 () -> factory.createScript("nosuchlang", "code", false, false, null));
  }
}
