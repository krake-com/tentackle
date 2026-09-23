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

package org.tentackle.script.groovy;

import groovy.lang.GroovyClassLoader;

import org.tentackle.common.Service;
import org.tentackle.script.AbstractScriptingLanguage;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptConverter;
import org.tentackle.script.ScriptingLanguage;

import java.util.function.Function;

/**
 * The groovy scripting language.
 *
 * @author harald
 */
@Service(ScriptingLanguage.class)
public class GroovyLanguage extends AbstractScriptingLanguage {

  /** the primary language name. */
  public static final String NAME = "Groovy";

  private static final String[] abbreviations = new String[] { NAME, "gv" };

  /** the single groovy classloader. */
  private final GroovyClassLoader groovyClassLoader;

  /**
   * Creates the groovy language.
   */
  public GroovyLanguage() {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    groovyClassLoader = new GroovyClassLoader(contextClassLoader != null ? contextClassLoader : getClass().getClassLoader());
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
  public Script createScript(String code, boolean cached, boolean theadSafe, Function<String, ScriptConverter> converterProvider) {
    return new GroovyScript(this, groovyClassLoader, getEffectiveCode(code, converterProvider), cached, theadSafe);
  }

}
