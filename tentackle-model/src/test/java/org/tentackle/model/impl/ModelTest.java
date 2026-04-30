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

package org.tentackle.model.impl;

import org.testng.annotations.Test;

import org.tentackle.common.Binary;
import org.tentackle.common.StringHelper;
import org.tentackle.common.Timestamp;
import org.tentackle.model.AccessScope;
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeSorting;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.Integrity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.SortType;
import org.tentackle.model.TrackType;
import org.tentackle.model.parse.Document;
import org.tentackle.sql.DataTypeFactory;

import java.io.IOException;
import java.util.TimeZone;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Data Model Test.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ModelTest {

  /** resource file holding the test model. */
  public static final String MODEL_FILENAME = "org/tentackle/model/model.txt";


  @Test
  public void testModel() {

    try {
      Document model = new Document(StringHelper.readTextFromResource(null, MODEL_FILENAME));
      Entity entity = Model.getInstance().getEntityFactory().createEntity(model, null);
      for (Index index : entity.getIndexes()) {   // due to delayed parsing
        ((IndexImpl) index).parse(entity);
      }

      assertEquals(entity.getOptions().getComment(), "the invoice header");
      assertEquals(entity.getName(), "Invoice");
      assertEquals(entity.getTableName(), "invoice");
      assertEquals(entity.getClassId(), 1);
      assertEquals(entity.getIntegrity(), Integrity.RELATED);
      assertEquals(entity.getOptions().getAccessScope(), AccessScope.PUBLIC);
      assertEquals(entity.getOptions().getStereotypes().size(),1);
      assertEquals(entity.getOptions().getStereotypes().get(0),"CUSTOM");

      assertTrue(entity.getOptions().isBindable());
      assertTrue(entity.getOptions().isMaxCol());
      assertTrue(entity.getOptions().isAutoSelect());
      assertEquals(entity.getOptions().getTrackType(), TrackType.TRACKED);
      assertTrue(entity.getOptions().isTokenLockProvided());
      assertTrue(entity.getOptions().isTableSerialProvided());

      assertNotEquals(entity.getOptions().getTrackType(), TrackType.ATTRACKED);
      assertFalse(entity.getOptions().isMute());
      assertFalse(entity.getOptions().isFromSuper());
      assertNotEquals(entity.getOptions().getTrackType(), TrackType.FULLTRACKED);
      assertFalse(entity.getOptions().isLowerCase());
      assertFalse(entity.getOptions().isMapNull());
      assertFalse(entity.getOptions().isNoConstant());
      assertFalse(entity.getOptions().isNoDeclare());
      assertFalse(entity.getOptions().isNoMethod());
      assertFalse(entity.getOptions().isNoPrimaryKey());
      assertFalse(entity.getOptions().isReadOnly());
      assertFalse(entity.getOptions().isTrimRead());
      assertFalse(entity.getOptions().isTrimWrite());
      assertFalse(entity.getOptions().isUpperCase());
      assertFalse(entity.getOptions().isWriteOnly());

      assertEquals(entity.getOptions().getBindOptions(), "SIZE,AUTOSELECT");

      assertEquals(entity.getSorting().size(), 2);
      AttributeSorting sorting = entity.getSorting().get(0);
      assertEquals(sorting.attribute().getName(), "id");
      assertEquals(sorting.sortType(), SortType.DESC);

      assertEquals(18, entity.getAttributes().size());
      Attribute attribute;
      attribute = entity.getAttributes().get(4);
      assertEquals(attribute.getDataType(), DataTypeFactory.getInstance().get(Long.TYPE));
      assertEquals(attribute.getName(), "subsidiaryId");
      assertEquals(attribute.getColumnName(), "subsidiary_id");
      assertEquals(attribute.getOptions().getComment(), "subsidiary ID");
      assertTrue(attribute.getOptions().isContextId());

      attribute = entity.getAttributes().get(5);
      assertEquals(attribute.getDataType(), DataTypeFactory.getInstance().get(String.class));
      assertEquals(attribute.getName(), "invoiceNo");
      assertEquals(attribute.getColumnName(), "invoice_no");
      assertEquals(attribute.getOptions().getComment(), "the unique invoice number");
      assertEquals(attribute.getSize(), Integer.valueOf(10));
      assertTrue(attribute.getOptions().isUpperCase());
      assertEquals(attribute.getOptions().getBindOptions(), "UC,MAXCOLS=10");
      assertEquals(attribute.getOptions().getAnnotations().size(), 1);
      assertEquals(attribute.getOptions().getAnnotations().get(0),
                   "@NotNull(message=\"{ @('please_enter_the_invoice-number') }\")");

      attribute = entity.getAttributes().get(7);
      assertEquals(attribute.getDataType(), DataTypeFactory.getInstance().get(Long.class));
      assertEquals(attribute.getColumnName(), "customer_id");
      assertNull(attribute.getSize());
      assertTrue(attribute.getOptions().isUpperCase());
      assertEquals(attribute.getOptions().getBindOptions(), "AUTOSELECT,UC");
      assertEquals(attribute.getOptions().getAnnotations().size(), 1);
      assertEquals(attribute.getOptions().getAnnotations().get(0), "@NotNull");
      assertEquals(attribute.getOptions().getStereotypes().size(),1);
      assertEquals(attribute.getOptions().getStereotypes().get(0),"blah=\"wow!\"");

      attribute = entity.getAttributes().get(8);
      assertEquals(attribute.getDataType(), DataTypeFactory.getInstance().get(String.class));
      assertEquals(attribute.getOptions().getBindOptions(), "UC,MAXCOLS=200");
      assertEquals(attribute.getOptions().getDefaultValue(), "blah\nx ");

      attribute = entity.getAttributes().get(9);
      assertEquals(attribute.getOptions().getAnnotations().size(), 2);

      attribute = entity.getAttributes().get(11);
      assertEquals(attribute.getOptions().getDefaultValue(), new Timestamp(-TimeZone.getDefault().getDSTSavings()));
      assertTrue(attribute.getOptions().isUTC());

      attribute = entity.getAttributes().get(12);
      assertEquals(attribute.getDataType(), DataTypeFactory.getInstance().get(Double.class));
      assertEquals(attribute.getSize(), Integer.valueOf(10));
      assertEquals(attribute.getScale(), Integer.valueOf(2));
      assertEquals(attribute.getOptions().getBindOptions(), "AUTOSELECT,MAXCOLS=10,SCALE=2");

      attribute = entity.getAttributes().get(13);
      assertEquals(attribute.getDataType(), DataTypeFactory.getInstance().getConvertibleType());
      assertEquals(attribute.getJavaType(), "MyType");

      attribute = entity.getAttributes().get(14);
      assertEquals(attribute.getDataType(), DataTypeFactory.getInstance().get(Binary.class));
      assertEquals(attribute.getJavaType(), "Binary<Blah>");
      assertEquals(attribute.getOptions().getComment(), "some binary object");

      attribute = entity.getAttributes().get(15);
      assertEquals(attribute.getOptions().getDefaultValue(), Boolean.TRUE);

      assertEquals(entity.getIndexes().size(), 3);
      Index index = entity.getIndexes().get(0);
      assertTrue(index.isUnique());
      assertEquals(index.getName(), "invoice_no");
      index = entity.getIndexes().get(2);
      assertEquals(index.getComment(), "to show latest invoices");

      assertEquals(entity.getRelations().size(), 2);
      Relation relation;
      relation = entity.getRelations().get(0);
      assertEquals(relation.getComment(), "the invoice lines");
      assertEquals(relation.getName(), "Lines");
      assertEquals(relation.getRelationType(), RelationType.LIST);
      assertTrue(relation.isComposite());
      assertTrue(relation.isReferenced());
      assertEquals(relation.getLinkMethodName(), "setInvoice");
      assertEquals(relation.getLinkMethodIndex(), "position");
      assertEquals(relation.getMethodName(), "Invoice");
      assertNotNull(relation.getMethodArgs());
      assertEquals(relation.getMethodArgs().size(), 2);
      assertEquals(relation.getMethodArgs().get(0).getMethodArgument(), "getId()");
      assertNull(relation.getMethodArgs().get(0).getForeignAttribute());
      assertNotNull(relation.getMethodArgs().get(0).getAttribute());
      assertEquals(relation.getMethodArgs().get(0).getAttribute().getName(), "id");
      assertEquals(relation.getMethodArgs().get(1).getMethodArgument(), "me().getInvoiceNo()");
      assertNull(relation.getMethodArgs().get(1).getForeignAttribute());
      assertNull(relation.getMethodArgs().get(1).getAttribute());
      assertEquals(relation.getMethodArgs().get(1).getForeignAttributeName(), "invoiceNo");
      assertEquals(relation.getSelectionType(), SelectionType.LAZY);
      assertEquals(relation.getAnnotations().size(), 1);
      assertEquals(relation.getAnnotations().get(0), "@NotNull");

      relation = entity.getRelations().get(1);
      assertEquals(relation.getName(), "Customer");
      assertEquals(relation.getRelationType(), RelationType.OBJECT);
      assertFalse(relation.isComposite());
      assertFalse(relation.isReferenced());
      assertNull(relation.getLinkMethodName());
      assertEquals(relation.getSelectionType(), SelectionType.ALWAYS);
      assertTrue(relation.isSelectionCached());
      assertEquals(relation.getAnnotations().size(), 1);
      assertEquals(relation.getAnnotations().get(0),
                   "@NotNull(message=\"{ @('please_enter_the_customer') }\", condition=\"{object?.invoiceDate != null}\")");
      assertEquals(relation.getComment(), "the customer object");
      attribute = relation.getAttribute();
      assertNotNull(attribute);
      assertSame(attribute.getRelation(), relation);
      assertEquals(attribute.getName(), "customerId");
      assertEquals(relation.getStereotypes().size(),2);
      assertEquals(relation.getStereotypes().get(0),"COOL");
      assertEquals(relation.getStereotypes().get(1),"HAPPY");
    }
    catch (IOException | ModelException ex) {
      fail(ex.toString());
    }
  }
}
