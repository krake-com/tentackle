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

package org.tentackle.script.truffle;

import org.graalvm.polyglot.Language;

import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;

import java.util.function.Function;


/**
 * R language based on GraalVM Truffle.
 *
 * @author harald
 */
@TruffleLanguageService("R")
public class RTruffleLanguage extends TruffleLanguage {

  /**
   * Creates a FastR language.
   *
   * @param language the graal language
   */
  public RTruffleLanguage(Language language) {
    super(language);
  }

  @Override
  public String[] getAbbreviations() {
    return new String[]{"r", "R", "fastr", "FastR", "FASTR"};
  }

  @Override
  public Script createScript(String code, boolean cached, boolean threadSafe, Function<String, ScriptConverter> converterProvider) {
    return new TruffleScript(this, getEffectiveCode(code, converterProvider), cached, threadSafe);
  }

}
