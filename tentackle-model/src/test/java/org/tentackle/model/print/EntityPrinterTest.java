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
import org.tentackle.model.Relation;
import org.tentackle.model.impl.IndexImpl;
import org.tentackle.model.parse.Document;

import java.util.ArrayList;
import java.util.List;

import static org.tentackle.model.impl.ModelTest.MODEL_FILENAME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Entity printer test.
 */
@SuppressWarnings("missing-explicit-ctor")
public class EntityPrinterTest {

  private static final String EXPECTED =
    """
      /*
       * @{
       * tablename = invoice
       * mapping = $model/$tablename.map
       * classid = 1
       * @}
       */
      
      /*
       * @> $mapping
       *\s
       * # the invoice header
       * name := $classname
       * table := $tablename
       * id := $classid
       * integrity := $integrity
       *\s
       * ## attributes
       * [TABLESERIAL,TOKENLOCK,SIZE,BIND,TRACKED,#CUSTOM | -id +total]
       * long          subsidiaryId  subsidiary_id  subsidiary ID [CONTEXT,@MyAnno("blah")]
       * String(10)    invoiceNo     invoice_no     the unique invoice number [UC]
       * Date          invoiceDate   invoice_date   the invoice's date
       * Long          customerId    customer_id    object ID of the customer [UC,#blah="wow!"]
       * String(200)   address       address        the address (multiline) [UC,DEFAULT "blah\\nx "]
       * DMoney        total         total          sum (without tax) [#WARM]
       * DMoney        tax           tax            tax (vat)
       * Timestamp     printed       printed        first printed [UTC,DEFAULT "1970-01-01 00:00:00"]
       * Double(10,2)  someDouble    some_double    digits with scale
       * MyType<int>   extra         extra          some application-specific type
       * Binary<Blah>  blah          blah           some binary object
       * Boolean       testBool      testbool       some boolean [DEFAULT true]
       *\s
       * ## indexes
       * unique index invoice_no := -subsidiary_id, +invoice_no
       * index invoice_customer := +customer_id
       * # to show latest invoices
       * index invoice_date := +invoice_date
       *\s
       * ## relations
       * # the invoice lines
       * InvoiceLine:
       *     relation = component referenced list,
       *     link = setInvoice position,
       *     method = Invoice,
       *     args = invoiceNo[me().getInvoiceNo()],
       *     name = Lines,
       *     @NotNull
       *\s
       * # the customer object
       * Customer:
       *     select = cached,
       *     @NotNull(message="{ @('please_enter_the_customer') }", condition="{object?.invoiceDate != null}"),
       *     #COOL,
       *     #HAPPY
       *\s
       * ## annotations
       * invoiceNo:
       *     @NotNull(message="{ @('please_enter_the_invoice-number') }")
       *\s
       * invoiceDate:
       *     @NotNull(scope=AllScope.class, message="{ @('please_enter_the_invoice-date') }", condition="{ object?.printed == null }")
       *\s
       * customerId:
       *     @NotNull
       *\s
       * total:
       *     @NotNull(scope=PersistenceScope.class),
       *     @Greater(value="0", scope=PersistenceScope.class, condition="{ object.printed != null }")
       *\s
       * @<
       */
      """;

   @Test
   public void printEntity() throws Exception {
     Document model = new Document(StringHelper.readTextFromResource(null, MODEL_FILENAME));
     Entity entity = Model.getInstance().getEntityFactory().createEntity(model, null);
     for (Index index : entity.getIndexes()) {   // due to delayed parsing
       ((IndexImpl) index).parse(entity);
     }
     ModelDefaults defaults = model.getModelDefaults() == null ?
                                new ModelDefaults("AUTOSELECT,ATTRACKED,ROOT") : model.getModelDefaults();
     List<String> optionAnnos = new ArrayList<>();
     optionAnnos.add("@MyAnno");
     PrintConfiguration configuration = new PrintConfiguration(false, true, true,
                                                               defaults, optionAnnos, 2);
     String text = new EntityPrinter(entity, configuration).print();
     System.out.println(text);
     assertEquals(text, EXPECTED);
   }

   /**
    * A model source without a source info line (as loaded from a jar or the classpath) must be
    * printable as model input as well: the {@code #@}-header is simply omitted.
    */
   @Test
   public void printEntityWithoutSourceInfo() throws Exception {
     String modelSource = """
                          name := Simple
                          id := 1
                          table := simple
                          integrity := none

                          []
                          String(10)  code  code  the code
                          """;
     Document model = new Document(modelSource);
     assertNull(model.getSourceInfo());
     Entity entity = Model.getInstance().getEntityFactory().createEntity(model, null);

     PrintConfiguration configuration = new PrintConfiguration(false, false, false, null, new ArrayList<>(), 2);
     String text = new EntityPrinter(entity, configuration).print();
     assertFalse(text.contains(Document.ORIGIN_INFO_LEAD), "there is no source info to print:\n" + text);

     // the printed model must still be readable, and again without source info
     Document reparsed = new Document(text);
     assertNull(reparsed.getSourceInfo());
     assertEquals(Model.getInstance().getEntityFactory().createEntity(reparsed, null).getName(), "Simple");
   }

   /**
    * Relation properties following {@code method} or {@code count} must be separated by a comma,
    * otherwise the printed model cannot be parsed again.
    */
   @Test
   public void printRelationPropertiesAfterMethod() throws Exception {
     String modelSource = """
                          name := Simple
                          id := 1
                          table := simple
                          integrity := none

                          []
                          String(10)  code      code       the code
                          long        clientId  client_id  the client

                          Customer: method = Client, name = Client
                          """;
     Entity entity = Model.getInstance().getEntityFactory().createEntity(new Document(modelSource), null);
     PrintConfiguration configuration = new PrintConfiguration(false, false, false, null, new ArrayList<>(), 2);
     String text = new EntityPrinter(entity, configuration).print();
     assertTrue(text.contains("method = Client,\n"), "missing comma after method:\n" + text);

     Entity reparsed = Model.getInstance().getEntityFactory().createEntity(new Document(text), null);
     Relation relation = reparsed.getRelations().getFirst();
     assertEquals(relation.getMethodName(), "Client");
     assertEquals(relation.getName(), "Client");
   }

   /**
    * The default column prefix of an embedding relation must not be printed.
    */
   @Test
   public void printEmbeddingRelationWithDefaultPrefix() throws Exception {
     String modelSource = """
                          name := Simple
                          id := 1
                          table := simple
                          integrity := none

                          []
                          String(10)  code  code  the code

                          Address: select = embedded
                          """;
     Entity entity = Model.getInstance().getEntityFactory().createEntity(new Document(modelSource), null);
     assertEquals(entity.getRelations().getFirst().getColumnPrefix(), "address_");
     PrintConfiguration configuration = new PrintConfiguration(false, false, false, null, new ArrayList<>(), 2);
     String text = new EntityPrinter(entity, configuration).print();
     assertFalse(text.contains("prefix"), "default prefix must not be printed:\n" + text);
   }

   /**
    * An option annotation ending with {@code |} matches the annotation without parameters only.
    */
   @Test
   public void optionAnnotationWithoutParameters() {
     PrintConfiguration configuration = new PrintConfiguration(false, false, false, null,
                                                               List.of("@NotNull|", "@NotZero"), 2);
     assertTrue(configuration.isOptionAnnotation("@NotNull"));
     assertFalse(configuration.isOptionAnnotation("@NotNull(scope=PersistenceScope.class)"));
     assertFalse(configuration.isOptionAnnotation("@NotNullable"));
     assertTrue(configuration.isOptionAnnotation("@NotZero"));
     assertTrue(configuration.isOptionAnnotation("@NotZero(scope=PersistenceScope.class)"));
     assertFalse(configuration.isOptionAnnotation("@NotZeroOrNull"));
     assertFalse(configuration.isOptionAnnotation("@Greater(\"0\")"));
   }
}
