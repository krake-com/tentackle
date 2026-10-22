/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of thJSR223ScriptFactoryTeste License, or (at your option) any later version.
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

import org.tentackle.common.Service;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.script.DefaultScriptFactory;
import org.tentackle.script.ScriptFactory;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.Locale;

/**
 * Script factory supporting all JSR-223 engines.
 * <p>
 * Replaces the {@link DefaultScriptFactory}. Registers a {@link JSR223Language} for each engine
 * found by the {@link ScriptEngineManager} first, then the native {@code @Service} languages,
 * such as the ones of tentackle-script-groovy or tentackle-script-ruby.
 * Since the first registration of an abbreviation wins, the JSR-223 engines take precedence.
 *
 * @author bei, harald
 */
@Service(ScriptFactory.class)
public class JSR223ScriptFactory extends DefaultScriptFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(JSR223ScriptFactory.class);

  /**
   * Creates the script factory.
   */
  public JSR223ScriptFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  protected void loadLanguages() {
    // load all languages via ScriptEngineManager
    for (ScriptEngineFactory engineFactory : new ScriptEngineManager().getEngineFactories()) {
      if (engineFactory != null) {
        JSR223Language language = new JSR223Language(engineFactory);
        for (String abbreviation : language.getAbbreviations()) {
          if (abbreviation != null) {
            String abbrvLC = abbreviation.toLowerCase(Locale.ROOT);   // same as DefaultScriptFactory
            if (languages.putIfAbsent(abbrvLC, language) == null) {
              LOGGER.info("added scripting language {0} as {1}", language, abbrvLC);
            }
          }
        }
      }
    }

    super.loadLanguages();    // load the non-JSR languages (but don't override abbreviations)
  }

}
