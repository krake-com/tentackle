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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Default implementation of a script factory.
 * <p>
 * The scripting languages are loaded lazily via {@link ServiceFactory}. If more than one language
 * claims the same abbreviation, the first one found wins.
 *
 * @author harald
 */
@Service(ScriptFactory.class)
public class DefaultScriptFactory implements ScriptFactory {

  private static final Logger LOGGER = Logger.get(DefaultScriptFactory.class);


  /**
   * The map of abbreviations to supported scripting languages.
   */
  protected Map<String,ScriptingLanguage> languages;

  /**
   * The default language.
   */
  protected ScriptingLanguage defaultLanguage;


  /**
   * Creates the script factory.
   */
  public DefaultScriptFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public synchronized void setDefaultLanguage(ScriptingLanguage language) {
    defaultLanguage = language;
    LOGGER.info("default scripting language is {0}", defaultLanguage);
  }

  @Override
  public synchronized void setDefaultLanguage(String abbreviation) {
    setDefaultLanguage(getLanguage(abbreviation));
  }

  @Override
  public synchronized ScriptingLanguage getDefaultLanguage() {
    return defaultLanguage;
  }

  @Override
  public synchronized Set<ScriptingLanguage> getLanguages() {
    return Set.copyOf(getLanguagesMap().values());
  }

  @Override
  public ScriptingLanguage getLanguage(String abbreviation) {
    ScriptingLanguage language = getLanguageImpl(abbreviation);
    if (language == null) {
      throw new ScriptRuntimeException(abbreviation == null ?
                                       "default scripting language undefined" :
                                       ("unsupported scripting language '" + abbreviation + "'"));
    }
    return language;
  }

  @Override
  public boolean isLanguageAvailable(String abbreviation) {
    return getLanguageImpl(abbreviation) != null;
  }

  @Override
  public Script createScript(String abbreviation, String code, boolean cached, boolean threadSafe, Function<String, ScriptConverter> converterProvider) {
    ScriptingLanguage language = getLanguage(abbreviation);
    Script script = language.createScript(Objects.requireNonNull(code, "code"), cached, threadSafe, converterProvider);
    LOGGER.fine("created scripting object {0}", script);
    return script;
  }



  /**
   * Gets the scripting language.
   *
   * @param abbreviation one of the abbreviations for that language, null or empty string if default language
   * @return the language, null if no such language or no default language if abbreviation was null
   */
  protected synchronized ScriptingLanguage getLanguageImpl(String abbreviation) {
    ScriptingLanguage language;

    if (abbreviation != null && !abbreviation.isEmpty()) {
      abbreviation = abbreviation.toLowerCase(Locale.ROOT);
      language = getLanguagesMap().get(abbreviation);
    }
    else  {
      language = getDefaultLanguage();
    }

    return language;
  }

  /**
   * Gets the languages map.<br>
   * Maps the lowercase abbreviation to the language.<br>
   * Loads the languages if not yet done.<br>
   * Invoked from within a synchronized block.
   *
   * @return the language map, never null
   */
  protected Map<String, ScriptingLanguage> getLanguagesMap() {
    if (languages == null) {
      languages = new HashMap<>();
      loadLanguages();
    }
    return languages;
  }

  /**
   * Loads the languages.<br>
   * Invoked from within a synchronized block.
   *
   * @throws ScriptRuntimeException if a language could not be instantiated
   */
  protected void loadLanguages() {
    // load all languages via @Service
    // the abbreviations don't override previous ones in classpath
    // dto. for the default language
    // so it's easy to change the default or the meaning of an abbreviation
    try {
      for (Class<ScriptingLanguage> clazz: ServiceFactory.getServiceFinder().findServiceProviders(ScriptingLanguage.class)) {
        ScriptingLanguage language = clazz.getDeclaredConstructor().newInstance();
        for (String abbreviation: language.getAbbreviations()) {
          if (abbreviation != null) {
            String abbrvLC = abbreviation.toLowerCase(Locale.ROOT);
            if (languages.putIfAbsent(abbrvLC, language) == null) {
              LOGGER.info("added scripting language {0} as {1}", language, abbrvLC);
            }
          }
        }
      }
    }
    catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
        NoSuchMethodException | InvocationTargetException nfe) {
      throw new ScriptRuntimeException("supported scripting languages could not be determined", nfe);
    }
  }

}
