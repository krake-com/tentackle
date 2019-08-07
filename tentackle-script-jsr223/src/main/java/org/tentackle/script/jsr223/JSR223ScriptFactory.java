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
package org.tentackle.script.jsr223;

import org.tentackle.common.Service;
import org.tentackle.log.Logger;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptingLanguage;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * implementation of a script factory using JSR223.
 *
 * @author bei
 */
@Service(ScriptFactory.class)
public class JSR223ScriptFactory implements ScriptFactory {

    private static final Logger LOGGER = Logger.get(JSR223ScriptFactory.class);

    /**
     * The map of abbreviations to supported scripting languages.
     */
    private Map<String, ScriptingLanguage> languages;

    /**
     * The default language.
     */
    private ScriptingLanguage defaultLanguage;


    @Override
    public void setDefaultLanguage(ScriptingLanguage language) {
        defaultLanguage = language;
        LOGGER.info("JSR22ScriptFactory default scripting language is {0}", defaultLanguage);
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
        } else {
            language = getDefaultLanguage();
        }

        if (language == null) {
            throw new ScriptRuntimeException(abbreviation == null ?
                    "default JSR223 scripting language undefined" :
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
            // load all languages via ScriptEngineManager
            // the abbreviations override previous ones in classpath
            // dto. for the default language
            // so it's easy to change the default or the meaning of an abbreviation
            ScriptEngineManager manager = new ScriptEngineManager();
            languages = new HashMap<>();
            List<ScriptEngineFactory> engineFactories = manager.getEngineFactories();
            if (engineFactories.isEmpty()) {
                LOGGER.info("No JSR223 scripting engines were found");
                return;
            }
            LOGGER.info("JSR223ScriptFactory adding " + engineFactories.size() +
                    " JSR223 scripting engines");
            for (ScriptEngineFactory engineFactory : engineFactories) {
                JSR223ScriptingLanguage language = new JSR223ScriptingLanguage(engineFactory);
                for (String abbrv : language.getAbbreviations()) {
                    String abbrvLC = abbrv.toLowerCase();
                    languages.put(abbrvLC, language);
                    LOGGER.info("JSR223ScriptFactory added scripting language {0} as {1}", language, abbrvLC);
                }
            }
        }
    }
}
