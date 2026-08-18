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

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 *
 * @author harald
 */
public class RemoteMethodInfoTest {

  public RemoteMethodInfoTest() {
  }

  @Test
  public void testCleanTypeString() {
    try {
      RemoteMethodInfo info = new RemoteMethodInfo("method");
      assertEquals(info.cleanTypeString("doedel java.util.Map<java.lang.String, org.tentackle.Session>"),
                                        "doedel Map<String, Session>");
      assertEquals(info.cleanTypeString("java.util.Map"),
                                        "Map");
      assertEquals(info.cleanTypeString("doedel java."),
                                        "doedel java.");
      assertEquals(info.cleanTypeString("doedel java. a"),
                                        "doedel java. a");
      assertEquals(info.cleanTypeString("a"),
                                        "a");
      assertEquals(info.cleanTypeString(""),
                                        "");
    }
    catch (IOException ex) {
      fail("creation of RemoteMethodInfo failed", ex);
    }
  }


  /**
   * Wildcard bounds are the everyday case of a type javac renders with blanks in it.<br>
   * Unescaped, the type would be cut after {@code "<?"} and all subsequent fields of the line
   * would shift by two, which silently turns every flag of the parameter into garbage.
   */
  @Test
  public void writesParameterTypesContainingBlanks() throws Exception {
    RemoteMethodInfo info = methodInfo();
    info.addParameter(new RemoteMethodInfoParameter(info, "list", "java.util.List<? extends com.example.Invoice>"));
    info.addParameter(new RemoteMethodInfoParameter(info, "max", "int"));

    RemoteMethodInfoParameter[] params = writeAndRead(info).getParameters();

    assertEquals(params.length, 2);
    assertEquals(params[0].getName(), "list");
    assertEquals(params[0].getType(), "java.util.List<? extends com.example.Invoice>");
    assertEquals(params[1].getName(), "max");
    assertEquals(params[1].getType(), "int");
  }


  /**
   * The return type and the generic return type are written into the same line as the flags
   * describing it, so they need escaping as well.
   */
  @Test
  public void writesReturnTypesContainingBlanks() throws Exception {
    RemoteMethodInfo info = methodInfo();
    info.setReturnType("java.util.List<? super com.example.Invoice>");
    info.setGenericReturnType("<? super com.example.Invoice>");
    info.setSuperClassName("com.example.AbstractInvoice<? extends com.example.Invoice>");
    info.setReturningDbObjectCollection(true);
    info.setReturningPdoCollection(true);

    RemoteMethodInfo readBack = writeAndRead(info);

    assertEquals(readBack.getReturnType(), "java.util.List<? super com.example.Invoice>");
    assertEquals(readBack.getGenericReturnType(), "<? super com.example.Invoice>");
    assertEquals(readBack.getSuperClassName(), "com.example.AbstractInvoice<? extends com.example.Invoice>");
    assertTrue(readBack.isReturningDbObjectCollection());
    assertTrue(readBack.isReturningPdoCollection());
    assertFalse(readBack.isReturningDbObject());
    assertFalse(readBack.isReturningCursor());
  }


  /**
   * The flags belonging to a blank-carrying parameter must survive the round trip, not shift.
   */
  @Test
  public void keepsTheParameterFlagsAlignedWithTheirType() throws Exception {
    RemoteMethodInfo info = RemoteMethodInfo.readInfo(reader(
        "method: findIt",
        "class: com.example.InvoicePersistenceImpl",
        "returns: long . false false false false false false",
        "modifiers: public",
        "varargs: false",
        "arg[0]: session org.tentackle.session.Session true false false false",
        "arg[1]: invoices java.util.List<?\\sextends\\scom.example.Invoice> false false true true final"));

    RemoteMethodInfoParameter[] params = writeAndRead(info).getParameters();

    assertTrue(params[0].isInstanceOfSession());
    assertEquals(params[1].getType(), "java.util.List<? extends com.example.Invoice>");
    assertFalse(params[1].isInstanceOfSession());
    assertFalse(params[1].isInstanceOfContext());
    assertTrue(params[1].isInstanceOfDbObject());
    assertTrue(params[1].isInstanceOfPdo());
    assertTrue(params[1].isModifierSet(Modifier.FINAL));
  }


  /**
   * Info files written before the escaping was introduced contain no backslash at all and must
   * still be read correctly - {@code target/analyze} survives a build without {@code clean}.
   */
  @Test
  public void readsInfoFilesWrittenWithoutEscaping() throws Exception {
    RemoteMethodInfo info = RemoteMethodInfo.readInfo(reader(
        "# org.tentackle.buildsupport.RemoteMethodInfo Version 2.0",
        "method: selectTxIds",
        "class: org.tentackle.dbms.ModificationLog",
        "superclass: org.tentackle.dbms.AbstractDbObject<org.tentackle.dbms.ModificationLog>",
        "returns: java.util.List<java.lang.Long> <java.lang.Long> false false false false false false",
        "modifiers: public",
        "varargs: false",
        "arg[0]: fromId long false false false false"));

    assertEquals(info.getMethodName(), "selectTxIds");
    assertEquals(info.getSuperClassName(), "org.tentackle.dbms.AbstractDbObject<org.tentackle.dbms.ModificationLog>");
    assertEquals(info.getReturnType(), "java.util.List<java.lang.Long>");
    assertEquals(info.getGenericReturnType(), "<java.lang.Long>");
    assertEquals(info.getParameters()[0].getType(), "long");
  }


  /**
   * Creates a minimal method info the way the analyze phase would.
   */
  private static RemoteMethodInfo methodInfo() throws IOException {
    RemoteMethodInfo info = new RemoteMethodInfo(RemoteMethodInfo.TYPE_METHOD);
    info.setMethodName("findIt");
    info.setClassName("com.example.InvoicePersistenceImpl");
    info.setReturnType("long");
    info.setGenericReturnType("");
    info.setModifiers(new Modifier[] { Modifier.PUBLIC });
    return info;
  }

  /**
   * Writes the info to an info file and reads it back, i.e. what the analyze phase and the
   * wurblets do.
   */
  private static RemoteMethodInfo writeAndRead(RemoteMethodInfo info) throws IOException {
    StringWriter text = new StringWriter();
    try (PrintWriter writer = new PrintWriter(text)) {
      info.write(writer);
    }
    return RemoteMethodInfo.readInfo(new LineNumberReader(new StringReader(text.toString())));
  }

  private static LineNumberReader reader(String... lines) {
    return new LineNumberReader(new StringReader(String.join("\n", lines)));
  }

}
