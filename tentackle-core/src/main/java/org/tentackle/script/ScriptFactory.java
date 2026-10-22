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

import java.util.Set;
import java.util.function.Function;


interface ScriptFactoryHolder {
  ScriptFactory INSTANCE = ServiceFactory.createService(ScriptFactory.class, DefaultScriptFactory.class);
}


/**
 * A factory for scripts.
 * <p>
 * The scripting languages are provided by modules such as tentackle-script-groovy and are
 * referenced by their abbreviations. There is no default language unless set via
 * {@link #setDefaultLanguage(String)} (the application sets it from its {@code scripting} property,
 * or if there is exactly one language available).
 * <p>
 * Replaceable via &#64;{@link org.tentackle.common.Service}.
 * The default implementation is {@link DefaultScriptFactory}.
 *
 * @author harald
 */
public interface ScriptFactory {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  static ScriptFactory getInstance() {
    return ScriptFactoryHolder.INSTANCE;
  }


  /**
   * Sets the default language.
   *
   * @param abbreviation the abbreviation for the default language
   * @throws ScriptRuntimeException if no such language
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
   * Gets the scripting language.<br>
   * The abbreviation is case-insensitive.
   *
   * @param abbreviation one of the abbreviations for that language, null or empty string if default language
   * @return the language, never null
   * @throws ScriptRuntimeException if no such language or no default language
   */
  ScriptingLanguage getLanguage(String abbreviation);


  /**
   * Check if a scripting language is available.
   *
   * @param abbreviation one of the abbreviations for that language, null or empty string if default language
   * @return true if the scripting language is available
   */
  boolean isLanguageAvailable(String abbreviation);


  /**
   * Gets all scripting languages.
   *
   * @return the languages
   */
  Set<ScriptingLanguage> getLanguages();


  /**
   * Creates a script object.
   * <p>
   * Scripts will be cached to optimize memory consumption and performance, if <code>cached=true</code>. The same source code
   * will refer to the same compiled evaluation unit. The implementation may still return a new {@link Script} instance
   * if caching not supported by the language or the caching refers to class instances instead of objects.
   * Caching should not be used for scripts that are executed only once.
   * <p>
   * If there are chances that scripts are invoked from different threads at a time, <em>all</em> scripts must be executed
   * in a thread-safe manner. Although some languages are thread-safe by design, others may need some extra synchronization.
   * If in doubt, set <code>threadSafe</code> to <code>true</code>.
   * <p>
   * An optional function provides a {@link ScriptConverter} for the given scripting language to translate the
   * code before being compiled.
   * <p>
   * Notice that the script will be compiled when first used. To compile it without execution, invoke {@link Script#validate()}.
   *
   * @param abbreviation the scripting language abbreviation, null or empty for default language
   * @param code the scripting code
   * @param cached the same code should refer to the same compiled script object
   * @param threadSafe true if script may be used by more than one thread in parallel
   * @param converterProvider the optional script converter provider
   * @return the script, never null
   * @throws ScriptRuntimeException if creating the script failed, for example due to an unknown language
   */
  Script createScript(String abbreviation, String code, boolean cached, boolean threadSafe, Function<String, ScriptConverter> converterProvider);

}
