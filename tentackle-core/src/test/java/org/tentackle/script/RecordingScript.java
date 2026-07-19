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

import java.util.Set;

/**
 * A concrete {@link AbstractScript} for the tests that records the invocations.
 * <p>
 * {@link #execute(Set)} simply returns the received variable set (or {@code null}), so tests can verify how
 * {@link AbstractScript#execute(ScriptVariable...)} maps the varargs onto the {@link Set} overload.
 */
public class RecordingScript extends AbstractScript {

  /** number of times {@link #validate()} was invoked. */
  int validateCount;

  /** the variable set passed to the last {@link #execute(Set)} invocation. */
  Set<ScriptVariable> lastVariables;

  /** whether {@link #execute(Set)} has been invoked at least once. */
  boolean executed;

  public RecordingScript(ScriptingLanguage language, String code, boolean cached, boolean threadSafe) {
    super(language, code, cached, threadSafe);
  }

  @Override
  public void validate() {
    validateCount++;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T execute(Set<ScriptVariable> variables) {
    executed = true;
    lastVariables = variables;
    return (T) variables;
  }
}
