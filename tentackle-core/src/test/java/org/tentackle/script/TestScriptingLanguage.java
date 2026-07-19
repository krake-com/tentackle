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

import java.util.function.Function;

/**
 * A {@link ScriptingLanguage} used by the {@code org.tentackle.script} tests.
 * <p>
 * It is registered via {@code @Service} so that the {@link DefaultScriptFactory} discovers it through its regular
 * {@code loadLanguages()} mechanism. The abbreviations deliberately mix an upper-case entry and a {@code null} entry
 * to exercise the abbreviation normalization and the null-skipping in {@code loadLanguages()}.
 * <p>
 * The created {@link Script} is a {@link RecordingScript} whose effective code carries the result of the optional
 * {@link ScriptConverter}, so tests can verify that the conversion took place.
 */
@Service(ScriptingLanguage.class)
public class TestScriptingLanguage extends AbstractScriptingLanguage {

  /** the canonical name. */
  public static final String NAME = "Test";

  /** the primary abbreviation. */
  public static final String ABBREVIATION = "ts";

  public TestScriptingLanguage() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String[] getAbbreviations() {
    return new String[] {"TS", "test", null};
  }

  @Override
  public Script createScript(String code, boolean cached, boolean threadSafe,
                             Function<String, ScriptConverter> converterProvider) {
    String effectiveCode = getEffectiveCode(code, converterProvider);
    return new RecordingScript(this, effectiveCode, cached, threadSafe);
  }
}
