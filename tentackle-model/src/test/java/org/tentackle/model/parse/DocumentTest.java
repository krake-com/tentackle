/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.model.parse;

import org.testng.annotations.Test;

import org.tentackle.model.ModelException;

import java.util.Iterator;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Document model parser test.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DocumentTest {

  private static final String MODEL_TEXT =

"# model for invoice headers\n" +
"name := Invoice\n" +
"table := $tablename\n" +
"integrity := related\n" +
"\n" +
"# indexes\n" +
"unique index ${tablename}_no := subsidiary_id, invoice_no\n" +
"index ${tablename}_customer := customer_id\n" +
"index ${tablename}_date := invoice_date\n" +
"\n" +
"# attributes\n" +
"[bind, autoselect, tracked, tokenlock, tableserial, generated]\n" +
"\n" +
"long             subsidiaryId    subsidiary_id     subsidiary ID [contextid]\n" +
"String(10)       invoiceNo       invoice_no        the unique invoice number [uc]\n" +
"Date             invoiceDate     invoice_date      the invoice's date\n" +
"long             customerId      customer_id       object ID of the customer [uc]\n" +
"String(200)      address         address           the address (multiline) [default 'blah', @MyAnno]\n" +
"DMoney           total           total             sum (without tax)\n" +
"DMoney           tax             tax               tax (vat)\n" +
"Timestamp        printed         printed           first printed\n" +
"Double (10,2 )   someDouble      some_double       digits with scale\n" +
"MyType<int>      extra           extra             some application-specific type\n" +
"\n" +
"# validations\n" +
"invoiceNo:    @NotNull(message=\"{ @('please_enter_the_invoice-number') }\")\n" +
"invoiceDate:  @NotNull(scope=AllScope.class, message=\"{ @('please_enter_the_invoice-date') }\", condition=\"{ object?.printed == null }\")\n" +
"total:        @NotNull(scope=PersistenceScope.class),\n" +
"              @Greater(value=\"0\", scope=PersistenceScope.class, condition=\"{ object.printed != null }\")\n" +
"   \n" +
"#relations\n" +
"InvoiceLine:\n" +
"     relation = component referenced list,\n" +
"     link = setInvoice position,\n" +
"     method = Invoice,\n" +
"     args = invoiceNo[getInvoiceNo()],\n" +
"     select = lazy\n" +
"\n   " +
"Customer: select = cached,\n" +
"          @NotNull(message=\"{ @('please_enter_the_customer') }\", condition=\"{object?.invoiceDate != null}\")\n" +
"  \n  ";



  @Test
  public void parseModel() {
    try {
      Document document = new Document(MODEL_TEXT);
      List<Line> lines = document.getLines();
      Iterator<Line> iter = lines.iterator();
      Line line;

      // check all lines

      //  "# model for invoice headers\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.COMMENT);
      assertEquals(((CommentLine) line).getComment(), "model for invoice headers");

      //  "name = Invoice\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.CONFIGURATION);
      assertEquals(((ConfigurationLine) line).getKey(), "name");
      assertEquals(((ConfigurationLine) line).getValue(), "Invoice");

      //  "table = $tablename\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.CONFIGURATION);

      //  "integrity = related\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.CONFIGURATION);

      //  "\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.EMPTY);

      //  "# indexes\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.COMMENT);

      //  "unique index ${tablename}_no = subsidiary_id, invoice_no\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.CONFIGURATION);
      assertEquals(((ConfigurationLine) line).getKey(), "unique index ${tablename}_no");
      assertEquals(((ConfigurationLine) line).getValue(), "subsidiary_id, invoice_no");

      //  "index ${tablename}_customer = customer_id\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.CONFIGURATION);

      //  "index ${tablename}_date = invoice_date\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.CONFIGURATION);

      //  "\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.EMPTY);

      //  "# attributes\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.COMMENT);

      //  "[bind, autoselect, tracked, tokenlock, tableserial, generated]\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.GLOBAL_OPTION);
      List<String> options = ((GlobalOptionLine) line).getOptions();
      assertEquals(options.size(), 6);
      assertEquals(options.get(5), "generated");

      //  "\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.EMPTY);

      //  "long             subsidiaryId    subsidiary_id     subsidiary ID [contextid]\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE);
      AttributeLine al = (AttributeLine) line;
      assertEquals(al.getJavaType(), "long");
      assertEquals(al.getJavaName(), "subsidiaryId");
      assertEquals(al.getColumnName(), "subsidiary_id");
      assertEquals(al.getComment(), "subsidiary ID");
      assertEquals(al.getOptions().size(), 1);
      assertEquals(al.getOptions().get(0), "contextid");

      //  "String(10)       invoiceNo       invoice_no        the unique invoice number [uc]\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE);
      al = (AttributeLine) line;
      assertEquals(al.getSize(), Integer.valueOf(10));

      //  "Date             invoiceDate     invoice_date      the invoice's date\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE);

      //  "long             customerId      customer_id       object ID of the customer [uc]\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE);

      //  "String(200)      address         address           the address (multiline) [default 'blah', @MyAnno]\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE);
      al = (AttributeLine) line;
      assertEquals(al.getOptions().size(), 2);
      assertEquals(al.getOptions().get(0), "default 'blah'");
      assertEquals(al.getOptions().get(1), "@MyAnno");

      //  "DMoney           total           total             sum (without tax)\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE);

      //  "DMoney           tax             tax               tax (vat)\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE);

      //  "Timestamp        printed         printed           first printed\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE);

      //  "Double (10,2 )     someDouble      some_double       digits with scale\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE);
      al = (AttributeLine) line;
      assertEquals(al.getSize(), Integer.valueOf(10));
      assertEquals(al.getScale(), Integer.valueOf(2));

      //  "MyType<int>      extra           extra             some application-specific type\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE);
      al = (AttributeLine) line;
      assertEquals(al.getJavaType(), "MyType");
      assertEquals(al.getInnerType(), "int");

      //  "\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.EMPTY);

      //  "# validations\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.COMMENT);

      //  "invoiceNo:    @NotNull(message=\"{ @('please_enter_the_invoice-number') }\")\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE_OPTION);

      //  "invoiceDate:  @NotNull(scope=AllScope.class, message=\"{ @('please_enter_the_invoice-date') }\", condition=\"{ object?.printed == null }\")\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE_OPTION);

      //  "total:        @NotNull(scope=PersistenceScope.class),\n" +
      //  "              @Greater(value=\"0\", scope=PersistenceScope.class, condition=\"{ object.printed != null }\")\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.ATTRIBUTE_OPTION);
      OptionLine aol = (OptionLine) line;
      assertEquals(aol.getName(), "total");
      assertEquals(aol.getOptions().size(), 2);
      assertEquals(aol.getOptions().get(0), "@NotNull(scope=PersistenceScope.class)");
      assertEquals(aol.getOptions().get(1),
                   "@Greater(value=\"0\", scope=PersistenceScope.class, condition=\"{ object.printed != null }\")");

      //  "   \n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.EMPTY);

      //  "#relations\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.COMMENT);

      //  "InvoiceLine:\n" +
      //  "     relation = component referenced list,\n" +
      //  "     link = setInvoice indexed,\n" +
      //  "     select = lazy\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.RELATION);
      RelationLine rl = (RelationLine) line;
      assertEquals(rl.getClassName(), "InvoiceLine");
      assertEquals(rl.getProperties().size(), 5);
      assertEquals(rl.getProperties().get("relation"), "component referenced list");

      //  "\n   " +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.EMPTY);

      //  "Customer: select = cached,\n" +
      //  "          @NotNull(message=\"{ @('please_enter_the_customer') }\", condition=\"{object?.invoiceDate != null}\")\n" +
      line = iter.next();
      assertEquals(line.getLineType(), LineType.RELATION);
      rl = (RelationLine) line;
      assertEquals(rl.getProperties().size(), 2);
      assertEquals(rl.getProperties().keySet().toArray()[1],
                   "@NotNull(message=\"{ @('please_enter_the_customer') }\", condition=\"{object?.invoiceDate != null}\")");

      //  "  \n  ";
      line = iter.next();
      assertEquals(line.getLineType(), LineType.EMPTY);

    }
    catch (ModelException me) {
      fail(me.toString());
    }

  }


  /**
   * An unparsable line must be reported as a {@link ModelException}, even if the model source
   * provides no source info, which is the case for model sources loaded from a jar or the classpath.
   */
  @Test
  public void unparsableLineWithoutSourceInfo() {

    String modelText = "name := Invoice\n@Foo: bar\n";

    try {
      new Document(modelText).getLines();
      fail("unparsable line not detected");
    }
    catch (ModelException me) {
      assertTrue(me.getMessage().contains("cannot determine line type"), me.getMessage());
      assertFalse(me.getMessage().contains("null"), "no source info must not show up as 'null': " + me.getMessage());
    }

    // with source info the location is part of the message
    try {
      new Document("#@Invoice.java:0\n" + modelText).getLines();
      fail("unparsable line not detected");
    }
    catch (ModelException me) {
      // the model block starts at java source line 1, the offending line is the next one
      assertTrue(me.getMessage().contains("Invoice.java[2]"), me.getMessage());
    }
  }


  /**
   * The size and/or scale may be separated from the java type by whitespaces,
   * and may contain whitespaces itself.
   */
  @Test
  public void parseSizeAndScaleWithBlanks() throws ModelException {

    String[] types = { "Double(10,2)", "Double (10,2)", "Double (10,2 )", "Double ( 10,2 )",
                       "Double (10, 2)", "Double ( 10 , 2 )" };

    for (String type : types) {
      AttributeLine line = attributeLine(type + "  someDouble  some_double  digits with scale");
      assertEquals(line.getJavaType(), "Double", type);
      assertEquals(line.getSize(), Integer.valueOf(10), type);
      assertEquals(line.getScale(), Integer.valueOf(2), type);
      assertEquals(line.getJavaName(), "someDouble", type);
      assertEquals(line.getColumnName(), "some_double", type);
      assertEquals(line.getComment(), "digits with scale", type);
    }

    // size only, with and without a blank
    for (String type : new String[] { "String(20)", "String (20)", "String ( 20 )" }) {
      AttributeLine line = attributeLine(type + "  name  name  the name");
      assertEquals(line.getJavaType(), "String", type);
      assertEquals(line.getSize(), Integer.valueOf(20), type);
      assertNull(line.getScale(), type);
      assertEquals(line.getJavaName(), "name", type);
      assertEquals(line.getColumnName(), "name", type);
    }

    // an unterminated size group is a syntax error
    try {
      attributeLine("Double (10,2  someDouble  some_double");
      fail("unterminated size group not detected");
    }
    catch (ModelException me) {
      assertTrue(me.getMessage().contains("syntax error"), me.getMessage());
    }
  }


  /**
   * Parses a single attribute line.
   *
   * @param text the attribute line
   * @return the parsed line
   * @throws ModelException if parsing failed
   */
  private AttributeLine attributeLine(String text) throws ModelException {
    Document document = new Document("#@Invoice.java:0\nname := Invoice\n" + text + "\n");
    List<Line> lines = document.getLines();
    Line line = lines.get(lines.size() - 1);
    assertEquals(line.getLineType(), LineType.ATTRIBUTE, text);
    return (AttributeLine) line;
  }


  /**
   * Errors reported for a single line must not show a missing source info as 'null' either.
   */
  @Test
  public void lineErrorWithoutSourceInfo() {

    // an attribute line lacking the column name
    String modelText = "name := Invoice\nlong subsidiaryId\n";

    try {
      new Document(modelText).getLines();
      fail("mangled attribute line not detected");
    }
    catch (ModelException me) {
      assertTrue(me.getMessage().contains("syntax error"), me.getMessage());
      assertFalse(me.getMessage().contains("null"), "no source info must not show up as 'null': " + me.getMessage());
    }

    try {
      new Document("#@Invoice.java:0\n" + modelText).getLines();
      fail("mangled attribute line not detected");
    }
    catch (ModelException me) {
      assertTrue(me.getMessage().contains("Invoice.java[2]"), me.getMessage());
    }
  }
}
