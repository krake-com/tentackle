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

package org.tentackle.model.print;

import org.testng.annotations.Test;

import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.impl.IndexImpl;

import java.util.ArrayList;
import java.util.List;

import static org.tentackle.model.impl.ModelTest.MODEL_FILENAME;
import static org.testng.Assert.assertEquals;

/**
 *
 * @author harald
 */
public class EntityPrinterTest {

  private static final String EXPECTED =
"/*\n" +
" * @> $mapping\n" +
" * \n" +
" * # the invoice header\n" +
" * name := $classname\n" +
" * table := $tablename\n" +
" * id := $classid\n" +
" * integrity := $integrity\n" +
" * \n" +
" * ## attributes\n" +
" * [TABLESERIAL,TOKENLOCK,SIZE,BIND,TRACKED | -id +total]\n" +
" * long          subsidiaryId  subsidiary_id  subsidiary ID [CONTEXT,@MyAnno(\"blah\")]\n" +
" * String(10)    invoiceNo     invoice_no     the unique invoice number [UC]\n" +
" * Date          invoiceDate   invoice_date   the invoice's date\n" +
" * Long          customerId    customer_id    object ID of the customer [UC]\n" +
" * String(200)   address       address        the address (multiline) [UC,DEFAULT \"blah\\nx \"]\n" +
" * DMoney        total         total          sum (without tax)\n" +
" * DMoney        tax           tax            tax (vat)\n" +
" * Timestamp     printed       printed        first printed [UTC,DEFAULT \"1970-01-01 00:00:00\"]\n" +
" * Double(10,2)  someDouble    some_double    digits with scale\n" +
" * MyType<int>   extra         extra          some application specific type\n" +
" * Binary<Blah>  blah          blah           some binary object\n" +
" * Boolean       testBool      testbool       some boolean [DEFAULT true]\n" +
" * \n" +
" * ## indexes\n" +
" * unique index invoice_no := -subsidiary_id, +invoice_no\n" +
" * index invoice_customer := +customer_id\n" +
" * # to show latest invoices\n" +
" * index invoice_date := +invoice_date\n" +
" * \n" +
" * ## relations\n" +
" * # the invoice lines\n" +
" * InvoiceLine:\n" +
" *     relation = composite referenced list,\n" +
" *     link = setInvoice position,\n" +
" *     name = Lines,\n" +
" *     @NotNull\n" +
" * \n" +
" * # the customer object\n" +
" * Customer:\n" +
" *     select = cached,\n" +
" *     @NotNull(message=\"{ @('please_enter_the_customer') }\", condition=\"{object?.invoiceDate != null}\")\n" +
" * \n" +
" * ## annotations\n" +
" * invoiceNo:\n" +
" *     @NotNull(message=\"{ @('please_enter_the_invoice-number') }\")\n" +
" * \n" +
" * invoiceDate:\n" +
" *     @NotNull(scope=AllScope.class, message=\"{ @('please_enter_the_invoice-date') }\", condition=\"{ object?.printed == null }\")\n" +
" * \n" +
" * customerId:\n" +
" *     @NotNull\n" +
" * \n" +
" * total:\n" +
" *     @NotNull(scope=PersistenceScope.class),\n" +
" *     @Greater(value=\"0\", scope=PersistenceScope.class, condition=\"{ object.printed != null }\")\n" +
" * \n" +
" * @<\n" +
" */\n";

   @Test
   public void printEntity() throws Exception {
     String model = StringHelper.readTextFromResource(null, MODEL_FILENAME);
     Entity entity = Model.getInstance().getEntityFactory().createEntity(model, null);
     for (Index index : entity.getIndexes()) {   // due to delayed parsing
       ((IndexImpl) index).parse(entity);
     }
     ModelDefaults defaults = new ModelDefaults("AUTOSELECT,ATTRACKED,ROOT");
     List<String> optionAnnos = new ArrayList<>();
     optionAnnos.add("@MyAnno");
     PrintConfiguration configuration = new PrintConfiguration(true, true, defaults, optionAnnos, 2);
     String text = new EntityPrinter(entity, configuration).print();
     System.out.println(text);
     assertEquals(text, EXPECTED);
   }
}
