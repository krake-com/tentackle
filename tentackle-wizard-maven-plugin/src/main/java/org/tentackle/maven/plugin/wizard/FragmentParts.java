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

/**
 * Extracts the import lines from a fragment.<br>
 * A line marks an import if it starts with an import marker (defaults to {@code ^}-sign).
 * Such lines are removed from the fragment and collected in the
 * order of appearance, stripped of the marker.
 */
public class FragmentParts {

  private static final String DEFAULT_IMPORT_MARKER = "^";

  private final String imports;
  private final String content;

  /**
   * Creates the parts.
   *
   * @param fragment the fragment
   * @param importMarker the import marker
   */
  public FragmentParts(String fragment, String importMarker) {
    StringBuilder imports = new StringBuilder();
    StringBuilder content = new StringBuilder();
    for (String line : fragment.split("\n", -1)) {
      if (line.startsWith(importMarker)) {
        if (!imports.isEmpty()) {
          imports.append('\n');
        }
        imports.append(line.substring(importMarker.length()));
      }
      else {
        if (!content.isEmpty()) {
          content.append('\n');
        }
        content.append(line);
      }
    }
    this.imports = imports.toString();
    this.content = content.toString();
  }

  /**
   * Creates the parts.
   * Uses the default import marker ({@code ^}-sign).
   *
   * @param fragment the fragment
   */
  public FragmentParts(String fragment) {
    this(fragment, DEFAULT_IMPORT_MARKER);
  }

  /**
   * Gets the import lines.
   * The lines are stripped of the import marker.
   *
   * @return the import lines, empty if none
   */
  public String getImports() {
    return imports;
  }

  /**
   * Gets the content lines.
   *
   * @return the content lines, empty if none
   */
  public String getContent() {
    return content;
  }

}
