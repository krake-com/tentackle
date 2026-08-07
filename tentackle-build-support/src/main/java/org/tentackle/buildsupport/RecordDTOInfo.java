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
 * Holds information gathered by the {@code @RecordDTO} annotation.
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
   * @return an RecordDTOInfo object, null if file is empty or does not contain an RecordDTOInfo-text
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
   * @param reader is the LineNumberReader
   * @return the record info, null if file is empty or does not contain such info
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
              info.setRecordName(text);
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
  private String recordName;                                  // name of the class
  private List<RecordDTOInfoParameter> parameterList;         // formal parameters


  /**
   * Creates an RecordDTOInfo for a given type.
   * Used when reading from infofile.
   */
  public RecordDTOInfo() {
  }

  /**
   * Creates an RecordDTOInfo from a method element.
   * Used during apt processing.
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
   * Gets the name of the class this analyze-info is part of.
   *
   * @return the classname
   */
  public String getRecordName() {
    return recordName;
  }

  /**
   * Sets the classname and the packagename from a given classname.
   *
   * @param recordName the full class name
   */
  public void setRecordName(String recordName) {
    this.recordName = recordName;
  }

  /**
   * Gets the formal parameters of the method.
   *
   * @return the array of parameters
   */
  public RecordDTOInfoParameter[] getParameters() {
    return parameterList == null ? null : parameterList.toArray(new RecordDTOInfoParameter[0]);
  }

  /**
   * Adds a formal parameter
   * @param parameter the formal parameter to add
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
   * @param writer is the PrintWriter object
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
