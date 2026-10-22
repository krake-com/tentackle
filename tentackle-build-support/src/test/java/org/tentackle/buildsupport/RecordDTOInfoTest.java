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

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.StringTokenizer;

import static org.testng.Assert.assertEquals;

/**
 * Tests for the {@code record.info} file format written by {@link RecordDTOAnalyzeHandler} and
 * read back by the DTO wurblet.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class RecordDTOInfoTest {

  /**
   * A record component typed with a wildcard bound is rendered by javac with blanks in it.<br>
   * Unescaped, the type and the name would be read from the wrong tokens.
   */
  @Test
  public void writesComponentTypesContainingBlanks() throws Exception {
    RecordDTOInfo info = new RecordDTOInfo();
    info.setRecordName("com.example.InvoiceDTO");
    info.addParameter(parameter(info, "java.util.List<?\\sextends\\scom.example.Item> items"));
    info.addParameter(parameter(info, "long id"));

    RecordDTOInfo readBack = writeAndRead(info);
    assertEquals(readBack.getRecordName(), "com.example.InvoiceDTO");
    RecordDTOInfoParameter[] parameters = readBack.getParameters();

    assertEquals(parameters.length, 2);
    assertEquals(parameters[0].getType(), "java.util.List<? extends com.example.Item>");
    assertEquals(parameters[0].getName(), "items");
    assertEquals(parameters[1].getType(), "long");
    assertEquals(parameters[1].getName(), "id");
  }


  /**
   * Info files written before the escaping was introduced contain no backslash at all and must
   * still be read correctly.
   */
  @Test
  public void readsInfoFilesWrittenWithoutEscaping() throws Exception {
    RecordDTOInfo info = RecordDTOInfo.readInfo(new LineNumberReader(new StringReader(
        "# org.tentackle.buildsupport.RecordDTOInfo Version 1.0\n" +
        "type: com.example.InvoiceDTO\n" +
        "arg[0]: java.util.List<com.example.Item> items\n")));

    assertEquals(info.getRecordName(), "com.example.InvoiceDTO");
    assertEquals(info.getParameters().length, 1);
    assertEquals(info.getParameters()[0].getType(), "java.util.List<com.example.Item>");
    assertEquals(info.getParameters()[0].getName(), "items");
  }


  /**
   * Creates a record component from its (escaped) info-file representation.
   */
  private static RecordDTOInfoParameter parameter(RecordDTOInfo info, String infoFileText) {
    return new RecordDTOInfoParameter(info, new StringTokenizer(infoFileText));
  }

  /**
   * Writes the info to an info file and reads it back, i.e. what the analyze phase and the
   * DTO wurblet do.
   */
  private static RecordDTOInfo writeAndRead(RecordDTOInfo info) throws IOException {
    StringWriter text = new StringWriter();
    try (PrintWriter writer = new PrintWriter(text)) {
      info.write(writer);
    }
    return RecordDTOInfo.readInfo(new LineNumberReader(new StringReader(text.toString())));
  }

}
