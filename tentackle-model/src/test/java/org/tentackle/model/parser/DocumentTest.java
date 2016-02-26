/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.model.parser;

import java.util.Iterator;
import java.util.List;
import org.tentackle.model.ModelException;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Document model parser test.
 *
 * @author harald
 */
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
"[bind, autoselect, tracked, editedby, tableserial, generated]\n" +
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
"MyType<int>      extra           extra             some application specific type\n" +
"\n" +
"# validations\n" +
"invoiceNo:    @NotNull(message=\"{ @('please_enter_the_invoice-number') }\")\n" +
"invoiceDate:  @NotNull(scope=AllScope.class, message=\"{ @('please_enter_the_invoice-date') }\", condition=\"{ object?.printed == null }\")\n" +
"total:        @NotNull(scope=PersistenceScope.class),\n" +
"              @Greater(value=\"0\", scope=PersistenceScope.class, condition=\"{ object.printed != null }\")\n" +
"   \n" +
"#relations\n" +
"InvoiceLine:\n" +
"     relation = composite referenced list,\n" +
"     link = setInvoice indexed,\n" +
"     select = lazy\n" +
"\n   " +
"Customer: select = cached,\n" +
"          @NotNull(message=\"{ @('please_enter_the_customer') }\", condition=\"{object?.invoiceDate != null}\")\n" +
"  \n  ";



  @Test
  public void parseModel() {

    Document document = new Document(MODEL_TEXT);

    try {
      List<Line> lines = document.getLines();
      Iterator<Line> iter = lines.iterator();
      Line line;

      // check all lines

      //  "# model for invoice headers\n" +
      line = iter.next();
      Assert.assertEquals(LineType.COMMENT, line.getLineType());
      Assert.assertEquals("model for invoice headers", ((CommentLine) line).getComment());

      //  "name = Invoice\n" +
      line = iter.next();
      Assert.assertEquals(LineType.CONFIGURATION, line.getLineType());
      Assert.assertEquals("name", ((ConfigurationLine) line).getKey());
      Assert.assertEquals("Invoice", ((ConfigurationLine) line).getValue());

      //  "table = $tablename\n" +
      line = iter.next();
      Assert.assertEquals(LineType.CONFIGURATION, line.getLineType());

      //  "integrity = related\n" +
      line = iter.next();
      Assert.assertEquals(LineType.CONFIGURATION, line.getLineType());

      //  "\n" +
      line = iter.next();
      Assert.assertEquals(LineType.EMPTY, line.getLineType());

      //  "# indexes\n" +
      line = iter.next();
      Assert.assertEquals(LineType.COMMENT, line.getLineType());

      //  "unique index ${tablename}_no = subsidiary_id, invoice_no\n" +
      line = iter.next();
      Assert.assertEquals(LineType.CONFIGURATION, line.getLineType());
      Assert.assertEquals("unique index ${tablename}_no", ((ConfigurationLine) line).getKey());
      Assert.assertEquals("subsidiary_id, invoice_no", ((ConfigurationLine) line).getValue());

      //  "index ${tablename}_customer = customer_id\n" +
      line = iter.next();
      Assert.assertEquals(LineType.CONFIGURATION, line.getLineType());

      //  "index ${tablename}_date = invoice_date\n" +
      line = iter.next();
      Assert.assertEquals(LineType.CONFIGURATION, line.getLineType());

      //  "\n" +
      line = iter.next();
      Assert.assertEquals(LineType.EMPTY, line.getLineType());

      //  "# attributes\n" +
      line = iter.next();
      Assert.assertEquals(LineType.COMMENT, line.getLineType());

      //  "[bind, autoselect, tracked, editedby, tableserial, generated]\n" +
      line = iter.next();
      Assert.assertEquals(LineType.GLOBAL_OPTION, line.getLineType());
      List<String> options = ((GlobalOptionLine) line).getOptions();
      Assert.assertEquals(6, options.size());
      Assert.assertEquals("generated", options.get(5));

      //  "\n" +
      line = iter.next();
      Assert.assertEquals(LineType.EMPTY, line.getLineType());

      //  "long             subsidiaryId    subsidiary_id     subsidiary ID [contextid]\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE, line.getLineType());
      AttributeLine al = (AttributeLine) line;
      Assert.assertEquals("long", al.getJavaType());
      Assert.assertEquals("subsidiaryId", al.getJavaName());
      Assert.assertEquals("subsidiary_id", al.getColumnName());
      Assert.assertEquals("subsidiary ID", al.getComment());
      Assert.assertEquals(1, al.getOptions().size());
      Assert.assertEquals("contextid", al.getOptions().get(0));

      //  "String(10)       invoiceNo       invoice_no        the unique invoice number [uc]\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE, line.getLineType());
      al = (AttributeLine) line;
      Assert.assertEquals(Integer.valueOf(10), al.getSize());

      //  "Date             invoiceDate     invoice_date      the invoice's date\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE, line.getLineType());

      //  "long             customerId      customer_id       object ID of the customer [uc]\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE, line.getLineType());

      //  "String(200)      address         address           the address (multiline) [default 'blah', @MyAnno]\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE, line.getLineType());
      al = (AttributeLine) line;
      Assert.assertEquals(2, al.getOptions().size());
      Assert.assertEquals("default 'blah'", al.getOptions().get(0));
      Assert.assertEquals("@MyAnno", al.getOptions().get(1));

      //  "DMoney           total           total             sum (without tax)\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE, line.getLineType());

      //  "DMoney           tax             tax               tax (vat)\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE, line.getLineType());

      //  "Timestamp        printed         printed           first printed\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE, line.getLineType());

      //  "Double (10,2 )     someDouble      some_double       digits with scale\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE, line.getLineType());
      al = (AttributeLine) line;
      Assert.assertEquals(Integer.valueOf(10), al.getSize());
      Assert.assertEquals(Integer.valueOf(2), al.getScale());

      //  "MyType<int>      extra           extra             some application specific type\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE, line.getLineType());
      al = (AttributeLine) line;
      Assert.assertEquals("MyType", al.getJavaType());
      Assert.assertEquals("int", al.getInnerName());

      //  "\n" +
      line = iter.next();
      Assert.assertEquals(LineType.EMPTY, line.getLineType());

      //  "# validations\n" +
      line = iter.next();
      Assert.assertEquals(LineType.COMMENT, line.getLineType());

      //  "invoiceNo:    @NotNull(message=\"{ @('please_enter_the_invoice-number') }\")\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE_OPTION, line.getLineType());

      //  "invoiceDate:  @NotNull(scope=AllScope.class, message=\"{ @('please_enter_the_invoice-date') }\", condition=\"{ object?.printed == null }\")\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE_OPTION, line.getLineType());

      //  "total:        @NotNull(scope=PersistenceScope.class),\n" +
      //  "              @Greater(value=\"0\", scope=PersistenceScope.class, condition=\"{ object.printed != null }\")\n" +
      line = iter.next();
      Assert.assertEquals(LineType.ATTRIBUTE_OPTION, line.getLineType());
      OptionLine aol = (OptionLine) line;
      Assert.assertEquals("total", aol.getName());
      Assert.assertEquals(2, aol.getOptions().size());
      Assert.assertEquals("@NotNull(scope=PersistenceScope.class)", aol.getOptions().get(0));
      Assert.assertEquals("@Greater(value=\"0\", scope=PersistenceScope.class, condition=\"{ object.printed != null }\")", aol.getOptions().get(1));

      //  "   \n" +
      line = iter.next();
      Assert.assertEquals(LineType.EMPTY, line.getLineType());

      //  "#relations\n" +
      line = iter.next();
      Assert.assertEquals(LineType.COMMENT, line.getLineType());

      //  "InvoiceLine:\n" +
      //  "     relation = composite referenced list,\n" +
      //  "     link = setInvoice indexed,\n" +
      //  "     select = lazy\n" +
      line = iter.next();
      Assert.assertEquals(LineType.RELATION, line.getLineType());
      RelationLine rl = (RelationLine) line;
      Assert.assertEquals("InvoiceLine", rl.getClassName());
      Assert.assertEquals(3, rl.getProperties().size());
      Assert.assertEquals("composite referenced list", rl.getProperties().get("relation"));

      //  "\n   " +
      line = iter.next();
      Assert.assertEquals(LineType.EMPTY, line.getLineType());

      //  "Customer: select = cached,\n" +
      //  "          @NotNull(message=\"{ @('please_enter_the_customer') }\", condition=\"{object?.invoiceDate != null}\")\n" +
      line = iter.next();
      Assert.assertEquals(LineType.RELATION, line.getLineType());
      rl = (RelationLine) line;
      Assert.assertEquals(2, rl.getProperties().size());
      Assert.assertEquals("@NotNull(message=\"{ @('please_enter_the_customer') }\", condition=\"{object?.invoiceDate != null}\")",
                   rl.getProperties().keySet().toArray()[1]);

      //  "  \n  ";
      line = iter.next();
      Assert.assertEquals(LineType.EMPTY, line.getLineType());

    }
    catch (ModelException me) {
      Assert.fail(me.toString());
    }

  }
}
