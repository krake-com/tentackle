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

package org.tentackle.buildsupport;

import javax.lang.model.element.RecordComponentElement;
import java.io.PrintWriter;
import java.util.StringTokenizer;

/**
 * Abstraction of a record component.
 */
public class RecordDTOInfoParameter {

  private final RecordDTOInfo info;     // the parent info
  private final String name;            // the formal record component name
  private final String type;            // the record component's type


  /**
   * Constructs a RecordDTOInfoParameter.
   *
   * @param info the parent info
   * @param recordComponent the record component
   */
  public RecordDTOInfoParameter(RecordDTOInfo info, RecordComponentElement recordComponent) {
    this.info = info;
    name = recordComponent.getSimpleName().toString();
    type = recordComponent.asType().toString();
  }

  /**
   * Constructs a RecordDTOInfoParameter.
   *
   * @param info the parent info
   * @param stok the tokenizer to read from
   */
  public RecordDTOInfoParameter(RecordDTOInfo info, StringTokenizer stok) {
    this.info = info;
    type = AnnotationProcessingHelper.unescapeToken(stok.nextToken());
    name = stok.nextToken();
  }

  /**
   * Gets the parent info.
   *
   * @return the parent
   */
  public RecordDTOInfo getInfo() {
    return info;
  }

  /**
   * Gets the formal parameter name
   * @return the parameter name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the parameter type
   * @return the parameter type
   */
  public String getType() {
    return type;
  }

  /**
   * Writes this parameter to the infofile.
   *
   * @param writer the writer bound to the infofile
   */
  public void write(PrintWriter writer) {
    // the type must be escaped: the info file is whitespace-separated, types are not whitespace-free
    writer.print(AnnotationProcessingHelper.escapeToken(type) + " " + name);
  }

  @Override
  public String toString() {
    return type + " " + name;
  }
}
