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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Language;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.script.DefaultScriptFactory;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptRuntimeException;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;


interface TruffleContextHolder {
  Context CONTEXT = Context.newBuilder().allowAllAccess(true).build();
}


/**
 * Implementation of a script factory using Truffle.
 *
 * @author bei, harald
 */
@Service(ScriptFactory.class)
public class TruffleScriptFactory extends DefaultScriptFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(TruffleScriptFactory.class);

  private final Map<String, Class<TruffleLanguage>> supportedLanguages;

  @SuppressWarnings("unchecked")
  public TruffleScriptFactory() {
    supportedLanguages = new HashMap<>();
    for (Map.Entry<String,String> entry:
        ServiceFactory.getServiceFinder().createNameMap(TruffleLanguageService.class.getName()).entrySet()) {
      try {
        String language = StringHelper.stripEnclosingDoubleQuotes(entry.getKey());
        Class<TruffleLanguage> clazz = (Class<TruffleLanguage>) Class.forName(entry.getValue());
        supportedLanguages.put(language, clazz);
      }
      catch (ClassNotFoundException ex) {
        throw new ScriptRuntimeException("truffle language configuration failed", ex);
      }
    }
  }

  /**
   * Gets the truffle context.
   *
   * @return the truffle context
   */
  public static Context getContext() {
    return TruffleContextHolder.CONTEXT;
  }


  /**
   * Loads the languages if not already loaded.
   */
  protected void loadLanguages() {
    for (Language graalVmLanguage : getContext().getEngine().getLanguages().values()) {
      Class<TruffleLanguage> extendedClass = supportedLanguages.get(graalVmLanguage.getName());
      if (extendedClass != null) {
        try {
          TruffleLanguage language = extendedClass.getConstructor(Language.class).newInstance(graalVmLanguage);
          for (String abbrv : language.getAbbreviations()) {
            if (languages.putIfAbsent(abbrv, language) == null) {
              LOGGER.info("added scripting language {0} as {1}", language, abbrv);
            }
          }
        }
        catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
          throw new ScriptRuntimeException("cannot load truffle language " + graalVmLanguage.getName());
        }
      }
    }

    super.loadLanguages();    // load the non-JSR languages (but don't override abbreviations)
  }

}
