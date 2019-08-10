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

import org.tentackle.common.ServiceFactory;

import java.util.Collection;


interface ScriptFactoryHolder {
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
    return ScriptFactoryHolder.INSTANCE;
  }


  /**
   * Sets the default language.
   * <p>
   * Throws ScriptRuntimeException if no such language.
   *
   * @param abbreviation the abbreviation for the default language
   */
  void setDefaultLanguage(String abbreviation);


  /**
   * Sets the default language.
   *
   * @param language the language
   */
  void setDefaultLanguage(ScriptingLanguage language);


  /**
   * Gets the default language.
   *
   * @return the default language, null if none
   */
  ScriptingLanguage getDefaultLanguage();


  /**
   * Gets the scripting language.
   * <p>
   * Throws  ScriptRuntimeException if no such language.
   *
   * @param abbreviation one of the abbreviations for that language, null or empty string if default language
   * @return the language, never null
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
   * <p>
   * Throws {@link ScriptRuntimeException} if creating the script failed.
   *
   * @param abbreviation the scripting language, null or empty for default
   * @param code the scripting code
   * @param cached use caching if possible (script may be used more than once)
   * @param threadSafe true if script must be threadsafe
   * @return the script
   */
  Script createScript(String abbreviation, String code, boolean cached, boolean threadSafe);

  /**
   * Gets the script factory name.
   * <p>
   * The name must be unique!
   *
   * @return the name
   */
  String getName();

  /**
   * Check if a scripting language is available in this ScriptFactory.
   *
   * @param abbreviation one of the abbreviations for that language, null or empty string if default language
   * @return true if the scripting language is available.
   */
    boolean isLanguageAvailable(String abbreviation);

}
