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
import org.tentackle.log.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.HashSet;


/**
 * Default implementation of a script factory.
 *
 * @author harald
 */
@Service(ScriptFactory.class)
public class DefaultScriptFactory implements ScriptFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScriptFactory.class);


  /**
   * The map of abbreviations to supported scripting languages.
   */
  private Map<String,ScriptingLanguage> languages;

  /**
   * The default language.
   */
  private ScriptingLanguage defaultLanguage;


  @Override
  public void setDefaultLanguage(ScriptingLanguage language) {
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
  public synchronized Collection<ScriptingLanguage> getLanguages() {
    loadLanguages();
    return languages.values();
  }


  @Override
  public synchronized ScriptingLanguage getLanguage(String abbreviation) {

    loadLanguages();   // make sure that all languages are loaded

    ScriptingLanguage language;

    if (abbreviation != null && !abbreviation.isEmpty()) {
      abbreviation = abbreviation.toLowerCase();
      language = languages.get(abbreviation);
    }
    else  {
      language = getDefaultLanguage();
    }

    if (language == null) {
      throw new ScriptRuntimeException(abbreviation == null ?
              "default scripting language undefined" :
              ("unsupported scripting language '" + abbreviation + "'"));
    }

    return language;
  }


  @Override
  public Script createScript(String abbreviation, String code, boolean cached, boolean threadSafe) {
    ScriptingLanguage language = getLanguage(abbreviation);
    Script script = language.createScript(cached);
    script.setCode(code);
    script.setThreadSafe(threadSafe);
    LOGGER.fine("created scripting object {0}", script);
    return script;
  }


  /**
   * Loads the languages if not already loaded.
   */
  protected void loadLanguages() {
    if (languages == null) {

      languages = new HashMap<>();

      // collect languages from other instances of @Service(ScriptFactory.class)
      try {
        for (Class<ScriptFactory> clazz : ServiceFactory.getServiceFinder().findServiceProviders(ScriptFactory.class)) {
          if (!clazz.equals(this.getClass())) {
            ScriptFactory factory = clazz.getDeclaredConstructor().newInstance();
            for (ScriptingLanguage language : factory.getLanguages()) {
              HashSet dupSet = new HashSet<>();
              for (String abbrv : language.getAbbreviations()) {
                if (abbrv != null) {
                  if (!dupSet.contains(abbrv)) {
                    languages.put(abbrv, language);
                    LOGGER.info("DefaultScriptFactory added scripting language {0} as {1} from factory {2}", language, abbrv, clazz.getName());
                    dupSet.add(abbrv);
                  }
                }
              }
              String name = language.getName();
              if (name != null) {
                if (!dupSet.contains(name)) {
                  languages.put(name, language);
                  LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from factory {2}", language, name, clazz.getName());
                  dupSet.add(name);
                }
                String nameLC = name.toLowerCase();
                if (!nameLC.equals(name)) {
                  if (!dupSet.contains(nameLC)) {
                    languages.put(nameLC, language);
                    LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from factory {2}", language, nameLC, clazz.getName());
                    dupSet.add(nameLC);
                  }
                }
              }
            }
          }
        }
        if ( languages.size() == 1 ) {
          setDefaultLanguage(languages.keySet().toArray(new String[0])[0]);
        }
      }
      catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
              NoSuchMethodException | SecurityException | InvocationTargetException nfe) {
        throw new ScriptRuntimeException("error using ScriptFactory", nfe);
      }

      // load all languages via @Service
      // the abbreviations override previous ones in classpath
      // dto. for the default language
      // so it's easy to change the default or the meaning of an abbreviation

      try {
        for (Class<ScriptingLanguage> clazz: ServiceFactory.getServiceFinder().findServiceProviders(ScriptingLanguage.class)) {
          ScriptingLanguage language = clazz.getDeclaredConstructor().newInstance();
          for (String abbrv: language.getAbbreviations()) {
            HashSet dupSet = new HashSet<>();
            if (abbrv != null) {
              if (!dupSet.contains(abbrv)) {
                languages.put(abbrv, language);
                LOGGER.info("DefaultScriptFactory added scripting language {0} as {1} from service {2}", language, abbrv, clazz.getName());
                dupSet.add(abbrv);
              }
            }
            String name = language.getName();
            if ( name != null ) {
              if (!dupSet.contains(abbrv)) {
                languages.put(name, language);
                LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from service {2}", language, name, clazz.getName());
                dupSet.add(name);
              }
              String nameLC = name.toLowerCase();
              if ( ! nameLC.equals(name)) {
                if (!dupSet.contains(abbrv)) {
                  languages.put(nameLC, language);
                  LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from service {2}", language, nameLC, clazz.getName());
                  dupSet.add(nameLC);
                }
              }
            }
          }
        }
      }
      catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
          NoSuchMethodException | SecurityException | InvocationTargetException nfe) {
        throw new ScriptRuntimeException("supported scripting languages could not be determined", nfe);
      }
    }
  }

}
