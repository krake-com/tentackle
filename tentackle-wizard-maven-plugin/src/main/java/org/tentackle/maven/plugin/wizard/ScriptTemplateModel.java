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

import org.tentackle.buildsupport.codegen.TemplateModel;

import java.io.Serial;

/**
 * The FreeMarker model to weave a script fragment into a harness.<br>
 * Provides the following variables:
 * <ul>
 *   <li>{@code content}: the script fragment inserted into the harness</li>
 *   <li>{@code imports}: the import statements extracted from the fragment</li>
 *   <li>{@code language}: the scripting language name</li>
 *   <li>{@code harness}: the harness filename</li>
 * </ul>
 */
public class ScriptTemplateModel extends TemplateModel {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates the model.
   *
   * @param content the script fragment
   * @param imports the import statements extracted from the fragment, one per line
   * @param language the scripting language name
   * @param harness the harness filename
   */
  public ScriptTemplateModel(String content, String imports, String language, String harness) {
    putValue("content", content);
    putValue("imports", imports);
    putValue("language", language);
    putValue("harness", harness);
  }
}
