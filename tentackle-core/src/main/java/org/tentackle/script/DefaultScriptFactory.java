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
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.HashSet;


/**
 * Default implementation of a script factory.
 *
 * @author harald
 */
@Service(ScriptFactory.class)
public class DefaultScriptFactory implements ScriptFactory {

  private static final Logger LOGGER = Logger.get(DefaultScriptFactory.class);


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
  public synchronized Set<ScriptingLanguage> getLanguages() {
    loadLanguages();
    return Set.copyOf(languages.values());
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
  HashSet dupSet = new HashSet<>();


  /**
   * Loads the languages if not already loaded.
   */
  protected void loadLanguages() {
    if (languages == null) {

      languages = new HashMap<>();
      String factoryName = "default";

      // load all languages via @Service
      // abbreviations do not override previous ones in classpath
      // those defined earlier have higher significance
      // languages explitly defined by a ScriptingLanguage Service
      // have higher significane than those defined by another ScriptFactory Service
      //
      try {
        for (Class<ScriptingLanguage> clazz: ServiceFactory.getServiceFinder().findServiceProviders(ScriptingLanguage.class)) {
          ScriptingLanguage language = clazz.getDeclaredConstructor().newInstance();
          for (String abbrv: language.getAbbreviations()) {
            if (abbrv != null) {
              if (!languages.containsKey(abbrv)) {
                languages.put(abbrv, language);
                LOGGER.info("DefaultScriptFactory added scripting language {0} as {1} from service {2}", language, abbrv, clazz.getName());
              }
              abbrv = factoryName + ":" + abbrv;
              if (!languages.containsKey(abbrv)) {
                languages.put(abbrv, language);
                LOGGER.info("DefaultScriptFactory added scripting language {0} as {1} from factory {2}", language, abbrv, clazz.getName());
              }
            }
            String name = language.getName();
            if ( name != null ) {
              if (!languages.containsKey(name)) {
                languages.put(name, language);
                LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from service {2}", language, name, clazz.getName());
              }
              String nameLC = name.toLowerCase();
              name = factoryName + ":" + name;
              if (!languages.containsKey(name)) {
                languages.put(name, language);
                LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from factory {2}", language, name, clazz.getName());
              }
              if ( ! nameLC.equals(name)) {
                if (!languages.containsKey(nameLC)) {
                  languages.put(nameLC, language);
                  LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from service {2}", language, nameLC, clazz.getName());
                }
                nameLC = factoryName + ":" + nameLC;
                if (!languages.containsKey(nameLC)) {
                  languages.put(nameLC, language);
                  LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from factory {2}", language, nameLC, clazz.getName());
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
      // collect languages from other instances of @Service(ScriptFactory.class)
      // abbreviations do not override previous ones in classpath
      // those defined earlier have higher significance
      try {
        for (Class<ScriptFactory> clazz : ServiceFactory.getServiceFinder().findServiceProviders(ScriptFactory.class)) {
          if (!clazz.equals(this.getClass())) {
            ScriptFactory factory = clazz.getDeclaredConstructor().newInstance();
            factoryName = factory.getName();
            for (ScriptingLanguage language : factory.getLanguages()) {
              for (String abbrv : language.getAbbreviations()) {
                if (abbrv != null) {
                  if (!languages.containsKey(abbrv)) {
                    languages.put(abbrv, language);
                    LOGGER.info("DefaultScriptFactory added scripting language {0} as {1} from factory {2}", language, abbrv, factory.getName());
                    abbrv = factoryName + ":" + abbrv;
                    if (!languages.containsKey(abbrv)) {
                      languages.put(abbrv, language);
                      LOGGER.info("DefaultScriptFactory added scripting language {0} as {1} from factory {2}", language, abbrv, factory.getName());
                    }                  }
                }
              }
              String name = language.getName();
              if (name != null) {
                if (!languages.containsKey(name)) {
                  languages.put(name, language);
                  LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from factory {2}", language, name, factory.getName());
                }
                String nameLC = name.toLowerCase();
                name = factoryName + ":" + name;
                if (!languages.containsKey(name)) {
                  languages.put(name, language);
                  LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from factory {2}", language, name, factory.getName());
                }
                if (!nameLC.equals(name)) {
                  if (!languages.containsKey(nameLC)) {
                    languages.put(nameLC, language);
                    LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from factory {2}", language, nameLC, factory.getName());
                  }
                  nameLC = factoryName + ":" + nameLC;
                  if (!languages.containsKey(nameLC)) {
                    languages.put(nameLC, language);
                    LOGGER.info("DefaultScriptFactory added scripting language {0} by name {1} from factory {2}", language, nameLC, factory.getName());
                  }
                }
              }
            }
          }
        }
      }
      catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException |
              NoSuchMethodException | SecurityException | InvocationTargetException nfe) {
        throw new ScriptRuntimeException("error using ScriptFactory", nfe);
      }

      if ( languages.size() == 1 ) {
        setDefaultLanguage(languages.keySet().toArray(new String[0])[0]);
      }
    }
  }

  @Override public String getName() {
    return "default";
  }
}
