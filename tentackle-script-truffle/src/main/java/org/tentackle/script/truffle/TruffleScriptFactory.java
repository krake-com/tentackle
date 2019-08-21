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
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.script.DefaultScriptFactory;
import org.tentackle.script.ScriptFactory;

import java.util.Locale;


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

  /**
   * Gets the truffle context.
   *
   * @return the truffle context
   */
  public static Context getContext() {
    return TruffleContextHolder.CONTEXT;
  }


  private static final String[] ABBREVIATIONS_JAVASCRIPT = {"javascript", "JavaScript", "js", "trufflejs", "ECMAScript", "ecmascript"};
  private static final String[] ABBREVIATIONS_RUBY = {"ruby", "truffleruby", "Ruby", "truffleRuby"};
  private static final String[] ABBREVIATIONS_R = {"r", "R", "fastr", "FASTR"};


  protected String[] getAbbreviations(Language language) {
    switch (language.getName()) {
      case "JavaScript":
        return ABBREVIATIONS_JAVASCRIPT;

      case "Ruby":
        return ABBREVIATIONS_RUBY;

      case "R":
        return ABBREVIATIONS_R;

      default:
        return new String[]{language.getId(), language.getId().toLowerCase(Locale.US),
                            language.getName(), language.getName().toLowerCase(Locale.US)};
    }
  }

  /**
   * Loads the languages if not already loaded.
   */
  protected void loadLanguages() {
    for (Language truffleLanguage : getContext().getEngine().getLanguages().values()) {
      TruffleScriptingLanguage language = new TruffleScriptingLanguage(truffleLanguage, getAbbreviations(truffleLanguage));
      for (String abbrv : language.getAbbreviations()) {
        if (languages.putIfAbsent(abbrv, language) == null) {
          LOGGER.info("added scripting language {0} as {1}", language, abbrv);
        }
      }
    }

    super.loadLanguages();    // load the non-JSR languages (but don't override abbreviations)
  }

}
