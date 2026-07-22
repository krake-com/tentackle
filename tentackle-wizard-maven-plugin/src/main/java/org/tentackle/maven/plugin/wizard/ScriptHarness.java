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

package org.tentackle.maven.plugin.wizard;

import org.apache.maven.plugin.MojoFailureException;

import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptingLanguage;

import java.util.Objects;

/**
 * A script harness.<br>
 * A harness is a freemarker template that wraps a script fragment.
 * Its first line is a she-bang selecting the scripting language, for example
 * {@code #!groovy}. The she-bang line is stripped and does not land in the
 * generated script. The remaining lines are the freemarker template that the
 * fragment is woven into (via the {@code content} variable).
 */
public class ScriptHarness {

  private final String name;
  private final String content;
  private final ScriptingLanguage language;

  /**
   * Creates a harness.
   *
   * @param name the harness filename (without directory)
   * @param content the harness content, including the she-bang line
   * @throws MojoFailureException if the she-bang is missing or the language is unknown
   */
  public ScriptHarness(String name, String content) throws MojoFailureException {
    this.name = Objects.requireNonNull(name, "missing name");
    Content harnessContent = determineContent(Objects.requireNonNull(content, "missing content"));
    this.content = harnessContent.content();
    this.language = harnessContent.language();
  }

  /**
   * Gets the harness name.
   *
   * @return the filename without directory
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the harness content with the she-bang line stripped.
   *
   * @return the freemarker template body
   */
  public String getContent() {
    return content;
  }

  /**
   * Gets the scripting language selected by the she-bang.
   *
   * @return the language
   */
  public ScriptingLanguage getLanguage() {
    return language;
  }

  @Override
  public String toString() {
    return name;
  }


  private record Content(String content, ScriptingLanguage language) {
  }

  private Content determineContent(String content) throws MojoFailureException {
    if (!content.startsWith("#!")) {
      throw new MojoFailureException("missing she-bang intro -> cannot determine the scripting language");
    }
    int eol = content.indexOf('\n');
    String firstLine = eol >= 0 ? content.substring(2, eol) : content.substring(2);
    String languageName = firstLine.strip();   // strip() also removes a trailing \r from CRLF files
    try {
      ScriptingLanguage scriptingLanguage = ScriptFactory.getInstance().getLanguage(languageName);
      String body = eol >= 0 ? content.substring(eol + 1) : "";
      return new Content(body, scriptingLanguage);
    }
    catch (ScriptRuntimeException sx) {
      throw new MojoFailureException("unknown scripting language: " + languageName);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ScriptHarness that = (ScriptHarness) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
