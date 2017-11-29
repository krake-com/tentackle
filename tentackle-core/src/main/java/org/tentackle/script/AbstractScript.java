/**
 * Tentackle - http://www.tentackle.org
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

/**
 * Abstract implementation of a script.
 *
 * @author harald
 */
public abstract class AbstractScript implements Script {

  /** the language instance. */
  private final ScriptingLanguage language;

  /** true if use caching if possible. */
  private final boolean cached;

  /** main scripting code. */
  private String code;

  /** code converter. */
  private ScriptConverter converter;

  /** true if execution works in parallel for more than one thread. */
  private volatile boolean threadSafe;


  /**
   * Creates a script.
   *
   * @param language the language instance
   * @param cached true if use caching, if possible
   */
  public AbstractScript(ScriptingLanguage language, boolean cached) {
    this.language = language;
    this.cached = cached;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("#!").append(getLanguage()).append("{ ");
    if (code != null) {
      String codeInfo = StringHelper.trim(code.replace('\n', ' '), 64);
      buf.append(codeInfo);
      if (codeInfo.length() < code.length()) {
        buf.append(" ...");
      }
    }
    else {
      buf.append("<null>");
    }
    buf.append(" }");
    return buf.toString();
  }

  @Override
  public ScriptingLanguage getLanguage() {
    return language;
  }

  @Override
  public void setThreadSafe(boolean threadSafe) {
    this.threadSafe = threadSafe;
  }

  @Override
  public boolean isThreadSafe() {
    return threadSafe;
  }

  /**
   * Returns whether script should be cached.
   *
   * @return true if cached
   */
  public boolean isCached() {
    return cached;
  }

  @Override
  public void setCode(String code) {
    if (code == null || code.isEmpty()) {
      throw new IllegalArgumentException("code must not be empty");
    }
    this.code = code;
    clearParsed();
  }

  @Override
  public String getCode() {
    return code;
  }


  @Override
  public String getEffectiveCode() {
    String effectiveCode = getCode();
    ScriptConverter cnvrtr = getConverter();
    if (cnvrtr != null) {
      effectiveCode = cnvrtr.convert(effectiveCode, getLanguage());
    }
    return effectiveCode;
  }

  @Override
  public void setConverter(ScriptConverter converter) {
    this.converter = converter;
  }

  @Override
  public ScriptConverter getConverter() {
    return converter;
  }

}
