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

import java.util.Collection;
import org.tentackle.common.ServiceFactory;


interface ScriptFactory$Singleton {
  ScriptFactory INSTANCE = ServiceFactory.createService(ScriptFactory.class, DefaultScriptFactory.class);
}


/**
 * A factory for scripts.
 *
 * @author harald
 */
public interface ScriptFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static ScriptFactory getInstance() {
    return ScriptFactory$Singleton.INSTANCE;
  }


  /**
   * Sets the default language.
   *
   * @param abbreviation the abbreviation for the default language
   * @throws ScriptRuntimeException if no such language
   */
  void setDefaultLanguage(String abbreviation);


  /**
   * Gets the default language.
   *
   * @return the the default language
   */
  ScriptingLanguage getDefaultLanguage();


  /**
   * Gets the scripting language.
   *
   * @param abbreviation one of the abbreviations for that language, null or empty string if default language
   * @return the language, never null
   * @throws ScriptRuntimeException if no such language
   */
  ScriptingLanguage getLanguage(String abbreviation);


  /**
   * Gets all scripting languages.
   *
   * @return the languages
   */
  Collection<ScriptingLanguage> getLanguages();


  /**
   * Creates a script object.
   * <p>
   * Implementations of the {@link ScriptFactory} must implement this method.
   *
   * @param abbreviation the scripting language, null or empty for default
   * @param code the scripting code
   * @param cached use caching if possible (script may be used more than once)
   * @param threadSafe true if script must be threadsafe
   * @return the script
   * @throws ScriptRuntimeException if creating the script failed
   */
  Script createScript(String abbreviation, String code, boolean cached, boolean threadSafe) throws ScriptRuntimeException;

}
