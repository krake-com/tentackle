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

import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptingLanguage;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.Locale;
import java.util.Map;

/**
 * Tests for {@link JSR223ScriptFactory}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class JSR223ScriptFactoryTest {

  private static class TestFactory extends JSR223ScriptFactory {
    synchronized Map<String, ScriptingLanguage> languagesMap() {
      return getLanguagesMap();
    }
  }

  @Test
  public void testIsTheScriptFactory() {
    Assert.assertTrue(ScriptFactory.getInstance() instanceof JSR223ScriptFactory,
                      ScriptFactory.getInstance().getClass().getName());
  }

  @Test
  public void testAbbreviationsAreRegisteredLowerCase() {
    Map<String, ScriptingLanguage> map = new TestFactory().languagesMap();
    Assert.assertFalse(map.isEmpty());
    for (String abbreviation : map.keySet()) {
      Assert.assertEquals(abbreviation, abbreviation.toLowerCase(Locale.ROOT));
    }
  }

  @Test
  public void testAllEngineNamesAreAvailableCaseInsensitive() {
    TestFactory factory = new TestFactory();
    for (ScriptEngineFactory engineFactory : new ScriptEngineManager().getEngineFactories()) {
      for (String name : engineFactory.getNames()) {
        Assert.assertTrue(factory.isLanguageAvailable(name), name);
        Assert.assertTrue(factory.isLanguageAvailable(name.toUpperCase(Locale.ROOT)), name);
      }
    }
  }

  @Test
  public void testJsrEnginesTakePrecedenceOverNativeLanguages() {
    ScriptingLanguage groovy = new TestFactory().getLanguage("groovy");
    Assert.assertTrue(groovy instanceof JSR223Language, groovy.getClass().getName());
  }

}
