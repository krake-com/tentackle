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

package org.tentackle.model.impl;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.TimeZone;
import org.tentackle.common.StringHelper;
import org.tentackle.model.AccessScope;
import org.tentackle.model.Attribute;
import org.tentackle.model.DataType;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.Integrity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.TrackType;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Data Model Test.
 *
 * @author harald
 */
public class ModelTest {


  private static final String MODEL_FILENAME = "org/tentackle/model/model.txt";


  @Test
  public void testModel() {

    try {
      String model = StringHelper.readTextFromResource(MODEL_FILENAME);
      Entity entity = Model.getInstance().getEntityFactory().createEntity(model, null);

      Assert.assertEquals("the invoice header", entity.getOptions().getComment());
      Assert.assertEquals("Invoice", entity.getName());
      Assert.assertEquals("invoice", entity.getTableName());
      Assert.assertEquals(1, entity.getClassId());
      Assert.assertEquals(Integrity.RELATED, entity.getIntegrity());
      Assert.assertEquals(AccessScope.PUBLIC, entity.getOptions().getAccessScope());

      Assert.assertTrue(entity.getOptions().isBind());
      Assert.assertTrue(entity.getOptions().isMaxCol());
      Assert.assertTrue(entity.getOptions().isAutoSelect());
      Assert.assertTrue(entity.getOptions().getTrackType() == TrackType.TRACKED);
      Assert.assertTrue(entity.getOptions().isTokenLockProvided());
      Assert.assertTrue(entity.getOptions().isTableSerialProvided());

      Assert.assertFalse(entity.getOptions().getTrackType() == TrackType.ATTRACKED);
      Assert.assertFalse(entity.getOptions().isDerived());
      Assert.assertFalse(entity.getOptions().isFromSuper());
      Assert.assertFalse(entity.getOptions().getTrackType() == TrackType.FULLTRACKED);
      Assert.assertFalse(entity.getOptions().isLowerCase());
      Assert.assertFalse(entity.getOptions().isMapNull());
      Assert.assertFalse(entity.getOptions().isNoConstant());
      Assert.assertFalse(entity.getOptions().isNoDeclare());
      Assert.assertFalse(entity.getOptions().isNoMethod());
      Assert.assertFalse(entity.getOptions().isNoPrimaryKey());
      Assert.assertFalse(entity.getOptions().isReadOnly());
      Assert.assertFalse(entity.getOptions().isSetGet());
      Assert.assertFalse(entity.getOptions().isTrimRead());
      Assert.assertFalse(entity.getOptions().isTrimWrite());
      Assert.assertFalse(entity.getOptions().isUpperCase());
      Assert.assertFalse(entity.getOptions().isWriteOnly());

      Assert.assertEquals("SIZE,AUTOSELECT", entity.getOptions().getBindOptions());

      Assert.assertEquals(18, entity.getAttributes().size());
      Attribute attribute;
      attribute = entity.getAttributes().get(4);
      Assert.assertEquals(DataType.LONG_PRIMITIVE, attribute.getDataType());
      Assert.assertEquals("subsidiaryId", attribute.getJavaName());
      Assert.assertEquals("subsidiary_id", attribute.getColumnName());
      Assert.assertEquals("subsidiary ID", attribute.getOptions().getComment());
      Assert.assertTrue(attribute.getOptions().isContextId());

      attribute = entity.getAttributes().get(5);
      Assert.assertEquals(DataType.STRING, attribute.getDataType());
      Assert.assertEquals("invoiceNo", attribute.getJavaName());
      Assert.assertEquals("invoice_no", attribute.getColumnName());
      Assert.assertEquals("the unique invoice number", attribute.getOptions().getComment());
      Assert.assertEquals(Integer.valueOf(10), attribute.getSize());
      Assert.assertTrue(attribute.getOptions().isUpperCase());
      Assert.assertEquals("UC,MAXCOL=10", attribute.getOptions().getBindOptions());
      Assert.assertEquals(1, attribute.getOptions().getAnnotations().size());
      Assert.assertEquals("@NotNull(message=\"{ @('please_enter_the_invoice-number') }\")", attribute.getOptions().getAnnotations().get(0));

      attribute = entity.getAttributes().get(7);
      Assert.assertEquals(DataType.LONG, attribute.getDataType());
      Assert.assertNull(attribute.getSize());
      Assert.assertTrue(attribute.getOptions().isUpperCase());
      Assert.assertEquals("AUTOSELECT,UC", attribute.getOptions().getBindOptions());

      attribute = entity.getAttributes().get(8);
      Assert.assertEquals(DataType.STRING, attribute.getDataType());
      Assert.assertEquals("UC,MAXCOL=200", attribute.getOptions().getBindOptions());
      Assert.assertEquals("blah x", attribute.getOptions().getDefaultValue());

      attribute = entity.getAttributes().get(9);
      Assert.assertEquals(2, attribute.getOptions().getAnnotations().size());

      attribute = entity.getAttributes().get(11);
      Assert.assertEquals(new Timestamp(-TimeZone.getDefault().getDSTSavings()), attribute.getOptions().getDefaultValue());
      Assert.assertTrue(attribute.getOptions().isUTC());

      attribute = entity.getAttributes().get(12);
      Assert.assertEquals(DataType.DOUBLE, attribute.getDataType());
      Assert.assertEquals(Integer.valueOf(10), attribute.getSize());
      Assert.assertEquals(Integer.valueOf(2), attribute.getScale());
      Assert.assertEquals("AUTOSELECT,MAXCOL=10,SCALE=2", attribute.getOptions().getBindOptions());

      attribute = entity.getAttributes().get(13);
      Assert.assertEquals(DataType.APPLICATION, attribute.getDataType());
      Assert.assertEquals("MyType", attribute.getJavaType());

      attribute = entity.getAttributes().get(14);
      Assert.assertEquals(DataType.BINARY, attribute.getDataType());
      Assert.assertEquals("Binary<Blah>", attribute.getJavaType());
      Assert.assertEquals("some binary object", attribute.getOptions().getComment());

      attribute = entity.getAttributes().get(15);
      Assert.assertEquals(Boolean.TRUE, attribute.getOptions().getDefaultValue());

      Assert.assertEquals(3, entity.getIndexes().size());
      Index index = entity.getIndexes().get(0);
      Assert.assertTrue(index.isUnique());
      Assert.assertEquals("invoice_no", index.getName());
      index = entity.getIndexes().get(2);
      Assert.assertEquals("to show latest invoices", index.getComment());

      Assert.assertEquals(2, entity.getRelations().size());
      Relation relation;
      relation = entity.getRelations().get(0);
      Assert.assertEquals("the invoice lines", relation.getComment());
      Assert.assertEquals("invoiceLine", relation.getName());
      Assert.assertEquals(RelationType.LIST, relation.getRelationType());
      Assert.assertTrue(relation.isComposite());
      Assert.assertTrue(relation.isReferenced());
      Assert.assertEquals("setInvoice", relation.getLinkMethodName());
      Assert.assertEquals("position", relation.getLinkMethodIndex());
      Assert.assertEquals(SelectionType.LAZY, relation.getSelectionType());

      relation = entity.getRelations().get(1);
      Assert.assertEquals("customer", relation.getName());
      Assert.assertEquals(RelationType.OBJECT, relation.getRelationType());
      Assert.assertFalse(relation.isComposite());
      Assert.assertFalse(relation.isReferenced());
      Assert.assertNull(relation.getLinkMethodName());
      Assert.assertEquals(SelectionType.ALWAYS, relation.getSelectionType());
      Assert.assertTrue(relation.isSelectionCached());
      Assert.assertEquals(1, relation.getAnnotations().size());
      Assert.assertEquals("@NotNull(message=\"{ @('please_enter_the_customer') }\", condition=\"{object?.invoiceDate != null}\")",
                   relation.getAnnotations().get(0));
      Assert.assertEquals("the customer object", relation.getComment());
      attribute = relation.getAttribute();
      Assert.assertNotNull(attribute);
      Assert.assertSame(relation, attribute.getRelation());
      Assert.assertEquals("customerId", attribute.getJavaName());

    }
    catch (IOException | ModelException ex) {
      Assert.fail(ex.toString());
    }
  }
}
