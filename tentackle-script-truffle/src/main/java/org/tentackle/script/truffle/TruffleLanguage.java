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

import org.tentackle.script.AbstractScriptingLanguage;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;

import java.util.Locale;
import java.util.function.Function;


/**
 * A scripting language based on GraalVM Truffle.
 *
 * @author bei, harald
 */
public abstract class TruffleLanguage extends AbstractScriptingLanguage {

  private final Language language;      // the Graal scripting language

  /**
   * Creates a scripting language by name
   *
   * @param language the graal language
   */
  public TruffleLanguage(Language language) {
    this.language = language;
  }

  @Override
  public String getName() {
    return language.getName();
  }

  @Override
  public String[] getAbbreviations() {
    return new String[]{language.getId(), language.getId().toLowerCase(Locale.US),
                        language.getName(), language.getName().toLowerCase(Locale.US)};
  }

  /**
   * Gets the native language.
   *
   * @return the graalvm native language
   */
  public Language getGraalVmLanguage() {
    return language;
  }

  @Override
  public Script createScript(String code, boolean cached, boolean threadSafe, Function<String, ScriptConverter> converterProvider) {
    return new TruffleScript(this, getEffectiveCode(code, converterProvider), cached, threadSafe);
  }

}
