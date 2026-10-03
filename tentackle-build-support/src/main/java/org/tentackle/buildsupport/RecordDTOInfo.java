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

import org.tentackle.common.Settings;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Holds information gathered from a record annotated with {@code @RecordDTO}.
 * <p>
 * The {@link RecordDTOAnalyzeHandler} creates an info file during the analyze phase, and
 * the {@code DTO} wurblet picks up this information to generate code.
 */
public class RecordDTOInfo {

  /**
   * File-format version.<br>
   * 1.1 escapes the whitespace within type strings, see {@link AnnotationProcessingHelper#escapeToken(String)}.
   */
  public static final String INFO_FILE_VERSION = "1.1";

  /**
   * Name of all record DTO info files.
   */
  public static final String INFO_FILE_NAME = "record.info";

  private static final String TYPE = "type";
  private static final String ARG = "arg[";


  /**
   * Reads info from a file.
   *
   * @param infoFile the file to read from
   * @return the record info, null if the file is empty or does not contain such info
   * @throws IOException if reading failed
   */
  public static RecordDTOInfo readInfo(File infoFile) throws IOException {
    RecordDTOInfo info;
    try (LineNumberReader reader = new LineNumberReader(
                                     new InputStreamReader(
                                       new FileInputStream(infoFile), Settings.getEncodingCharset()))) {
      info = readInfo(reader);
    }
    return info;
  }


  /**
   * Reads info from a line reader.
   *
   * @param reader the line reader
   * @return the record info, null if the file is empty or does not contain such info
   * @throws IOException if reading failed
   */
  public static RecordDTOInfo readInfo(LineNumberReader reader) throws IOException {
    RecordDTOInfo info = null;
    String line;
    while((line = reader.readLine()) != null) {
      if (!line.startsWith("#")) {    // skip comment lines
        StringTokenizer stok = new StringTokenizer(line, ":");
        if (stok.hasMoreTokens()) {
          String type = stok.nextToken();
          if (info == null) {
            // first line defines the info type
            info = new RecordDTOInfo();
          }
          if (stok.hasMoreTokens()) {
            String text = stok.nextToken();
            stok = new StringTokenizer(text);
            if (TYPE.equals(type)) {
              info.setRecordName(text.trim());
            }
            else if (type.startsWith(ARG)) {
              info.addParameter(new RecordDTOInfoParameter(info, stok));
            }
          }
        }
      }
    }
    return info;
  }



  // information gathered from apt run
  private String recordName;                                  // name of the record
  private List<RecordDTOInfoParameter> parameterList;         // record components


  /**
   * Creates an empty RecordDTOInfo.<br>
   * Used when reading from an info file.
   */
  public RecordDTOInfo() {
  }

  /**
   * Creates a RecordDTOInfo from a record element.<br>
   * Used during annotation processing.
   *
   * @param processingEnv the annotation processor's environment
   * @param recordElement the record type element
   */
  public RecordDTOInfo(ProcessingEnvironment processingEnv, TypeElement recordElement) {
    setRecordName(recordElement.toString());
    for (RecordComponentElement recordComponent : recordElement.getRecordComponents()) {
      addParameter(new RecordDTOInfoParameter(this, recordComponent));
    }
  }

  /**
   * Gets the name of the record.
   *
   * @return the full record name
   */
  public String getRecordName() {
    return recordName;
  }

  /**
   * Sets the name of the record.
   *
   * @param recordName the full record name
   */
  public void setRecordName(String recordName) {
    this.recordName = recordName;
  }

  /**
   * Gets the record components.
   *
   * @return the array of components, null if none
   */
  public RecordDTOInfoParameter[] getParameters() {
    return parameterList == null ? null : parameterList.toArray(new RecordDTOInfoParameter[0]);
  }

  /**
   * Adds a record component.
   *
   * @param parameter the component to add
   */
  public void addParameter(RecordDTOInfoParameter parameter) {
    if (parameterList == null) {
      parameterList = new ArrayList<>();
    }
    parameterList.add(parameter);
  }


  /**
   * Gets the declaration string.
   * <p>
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return getRecordName() + "(" + AnnotationProcessingHelper.objectArrayToString(getParameters(), ", ") + ")";
  }


  /**
   * Writes this object to an info file.
   *
   * @param writer the writer
   */
  public void write(PrintWriter writer) {
    writer.println("# " + getClass().getName() + " Version " + INFO_FILE_VERSION);
    writer.println("# " + this);
    writer.println(TYPE + ": " + recordName);
    if (parameterList != null) {
      int argc = 0;
      for (RecordDTOInfoParameter par: parameterList) {
        writer.print(ARG + argc++ + "]: ");
        par.write(writer);
        writer.println();
      }
    }
  }

}
