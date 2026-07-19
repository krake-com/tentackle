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

import org.testng.Reporter;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link ScriptFactory} singleton wiring.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ScriptFactoryTest {

  @Test
  public void testGetInstanceIsSingleton() {
    ScriptFactory factory = ScriptFactory.getInstance();
    assertNotNull(factory);
    assertSame(ScriptFactory.getInstance(), factory);
  }

  @Test
  public void testDefaultImplementationIsResolved() {
    // no other ScriptFactory is registered on the core test classpath
    assertTrue(ScriptFactory.getInstance() instanceof DefaultScriptFactory);
  }

  @Test
  public void testRegisteredLanguageIsAvailableViaSingleton() {
    ScriptFactory factory = ScriptFactory.getInstance();
    assertTrue(factory.isLanguageAvailable(TestScriptingLanguage.ABBREVIATION));
    assertNotNull(factory.getLanguage(TestScriptingLanguage.ABBREVIATION));
  }

  @Test
  public void testGetLanguages() {
    ScriptFactory factory = ScriptFactory.getInstance();
    assertTrue(factory.getLanguages().stream()
                   .anyMatch(l -> TestScriptingLanguage.NAME.equals(l.getName())),
               "test scripting language not found via singleton");
    for (ScriptingLanguage language : factory.getLanguages()) {
      for (String abbrv : language.getAbbreviations()) {
        Reporter.log("found scripting language " + language + " as " + abbrv + "<br>");
      }
    }
  }
}
