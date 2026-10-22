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

import org.tentackle.common.StringHelper;

import java.util.Objects;
import java.util.Set;

/**
 * Abstract implementation of a script.
 *
 * @author harald
 */
public abstract class AbstractScript implements Script {

  /** the language instance. */
  private final ScriptingLanguage language;

  /** main scripting code. */
  private final String code;

  /** true if execution requested for more than one thread. */
  private final boolean threadSafe;

  /** true to use caching if possible. */
  private final boolean cached;


  /**
   * Creates a script.
   *
   * @param language the language instance
   * @param code the scripting code
   * @param cached true to use caching, if possible
   * @param threadSafe true if thread-safety is required
   * @throws NullPointerException if language or code is null
   */
  public AbstractScript(ScriptingLanguage language, String code, boolean cached, boolean threadSafe) {
    this.language = Objects.requireNonNull(language, "language");
    this.code = Objects.requireNonNull(code, "code");
    this.cached = cached;
    this.threadSafe = threadSafe;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("#!").append(getLanguage()).append("{ ");
    String codeInfo = StringHelper.trim(getCode().replace('\n', ' '), 64);
    buf.append(codeInfo);
    if (codeInfo.length() < getCode().length()) {
      buf.append(" ...");
    }
    buf.append(" }");
    if (isCached()) {
      buf.append(" (cached)");
    }
    if (isThreadSafe()) {
      buf.append(" (mt)");
    }
    return buf.toString();
  }

  @Override
  public ScriptingLanguage getLanguage() {
    return language;
  }

  @Override
  public boolean isThreadSafe() {
    return threadSafe;
  }

  @Override
  public boolean isCached() {
    return cached;
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public <T> T execute(ScriptVariable... variables) {
    return execute(variables != null && variables.length > 0 ? Set.of(variables) : null);
  }

}
