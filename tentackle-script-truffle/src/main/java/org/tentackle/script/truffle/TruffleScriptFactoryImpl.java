/*:
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of thTruffleScriptFactoryTeste License, or (at your option) any later version.
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
package org.tentackle.script.truffle;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptingLanguage;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Source;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.HashSet;

/**
 * implementation of a script factory using Truffle.
 *
 * @author bei
 */
@Service(ScriptFactory.class)
public class TruffleScriptFactoryImpl implements ScriptFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TruffleScriptFactoryImpl.class);

    interface TruffleContextHolder {
        Context CONTEXT = Context.newBuilder().allowAllAccess(true).build();
    }

    public static Context getContext() {
        return TruffleContextHolder.CONTEXT;
    }


    /**
     * The map of abbreviations to supported scripting languages.
     */
    private Map<String, ScriptingLanguage> languages;

    /**
     * The default language.
     */
    private ScriptingLanguage defaultLanguage;

    public TruffleScriptFactoryImpl() {
        super();
    }

    @Override
    public void setDefaultLanguage(ScriptingLanguage language) {
        defaultLanguage = language;
        LOGGER.info("TruffleScriptFactory default scripting language is {0}", defaultLanguage);
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
        } else {
            language = getDefaultLanguage();
        }

        if (language == null) {
            throw new ScriptRuntimeException(abbreviation == null ?
                    "default Truffle scripting language undefined" :
                    ("unsupported Truffle scripting language '" + abbreviation + "'"));
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
            // load all languages via Context
            // abbreviations do not override previous ones in classpath
            // those defined earlier have higher significance
            languages = new HashMap<>();

            Map<String, Language> truffleLangs = getContext().getEngine().getLanguages();
            if (truffleLangs.isEmpty()) {
                LOGGER.info("No Truffle language were found");
                return;
            }
            LOGGER.info("TruffleScriptFactory adding " + truffleLangs.size() +
                    " Truffle scripting languages");

            for (String lang : truffleLangs.keySet()) {
                TruffleScriptingLanguage language = new TruffleScriptingLanguage(lang, true);
                for (String abbrv : language.getAbbreviations()) {
                    if ( abbrv != null ) {
                        if ( ! languages.containsKey(abbrv) ) {
                            languages.put(abbrv, language);
                            LOGGER.info("TruffleScriptFactory added scripting language {0} as {1}", language, abbrv);
                        }
                    }
                }
                String name = language.getName();
                if ( name != null ) {
                    if ( ! languages.containsKey(name) ) {
                        languages.put(name, language);
                        LOGGER.info("TruffleScriptFactory added scripting language {0} by name {1}", language, name);
                    }
                    String nameLC = name.toLowerCase();
                    if ( ! nameLC.equals(name)) {
                        if ( ! languages.containsKey(nameLC) ) {
                            languages.put(nameLC, language);
                            LOGGER.info("TruffleScriptFactory added scripting language {0} by name {1}", language, nameLC);
                        }
                    }
                }
            }
        }
    }

    @Override public String getName() {
        return "truffle";
    }

    @Override public boolean isLanguageAvailable(String abbreviation) {

        loadLanguages();   // make sure that all languages are loaded

        ScriptingLanguage language;

        if (abbreviation != null && !abbreviation.isEmpty()) {
            abbreviation = abbreviation.toLowerCase();
            language = languages.get(abbreviation);
        }
        else  {
            language = getDefaultLanguage();
        }

        return language != null;
    }
}
