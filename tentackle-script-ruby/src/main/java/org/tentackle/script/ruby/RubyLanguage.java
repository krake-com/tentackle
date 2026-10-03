/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.script.ruby;

import org.jruby.embed.ScriptingContainer;

import org.tentackle.common.Service;
import org.tentackle.script.AbstractScriptingLanguage;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;
import org.tentackle.script.ScriptingLanguage;

import java.util.function.Function;

/**
 * The Ruby scripting language based on the native JRuby embed API.
 * <p>
 * Registered under the abbreviations {@code Ruby}, {@code jruby}, {@code rb} and {@code ruby} (case-insensitive).
 * All scripts are run by a single {@link ScriptingContainer} owned by this language.
 * Variables are referenced as Ruby instance variables, i.e. {@code object} becomes {@code @object}.
 *
 * @author harald
 */
@Service(ScriptingLanguage.class)
public class RubyLanguage extends AbstractScriptingLanguage {

  /** the primary language name. */
  public static final String NAME = "Ruby";

  private static final String[] abbreviations = new String[] { NAME, "jruby", "rb", "ruby" };

  /** the single ruby container. */
  private final ScriptingContainer container = new ScriptingContainer();

  /**
   * Creates the Ruby language.
   */
  public RubyLanguage() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String[] getAbbreviations() {
    return abbreviations;
  }

  @Override
  public Script createScript(String code, boolean cached, boolean threadSafe, Function<String, ScriptConverter> converterProvider) {
    return new RubyScript(this, container, getEffectiveCode(code, converterProvider), cached, threadSafe);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Ruby variables are mapped to instance variables, i.e. the name is prefixed with {@code @}.
   */
  @Override
  public String createLocalVariableReference(String name) {
    return "@" + name;
  }

}
