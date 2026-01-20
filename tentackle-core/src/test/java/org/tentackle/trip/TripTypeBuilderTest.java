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

package org.tentackle.trip;

import org.testng.annotations.Test;

import org.tentackle.trip.builder.ArrayTypeBuilder;
import org.tentackle.trip.impl.TestClass;
import org.tentackle.trip.impl.TestEnum;
import org.tentackle.trip.impl.TestRecord;
import org.tentackle.trip.impl.TestSubClass;
import org.tentackle.trip.types.ArrayTripType;
import org.tentackle.trip.types.EnumTripType;
import org.tentackle.trip.types.LongArrayTripType;
import org.tentackle.trip.types.LongPrimitiveArrayTripType;
import org.tentackle.trip.types.ObjectTripType;
import org.tentackle.trip.types.RecordTripType;
import org.tentackle.trip.types.TimestampTripType;

import java.math.BigDecimal;
import java.sql.Timestamp;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("missing-explicit-ctor")
public class TripTypeBuilderTest {

  @Test
  public void builderTest() {
    // explicitly request the type builder for Long[], must not return LongArrayTripType!
    TripTypeBuilder<Long[]> arrayTypeBuilder = TripTypeBuilderFactory.getInstance().getBuilder(Long[].class);
    assertTrue(arrayTypeBuilder instanceof ArrayTypeBuilder);
    TripType<Long[]> objectTripType = arrayTypeBuilder.build(Long[].class);
    assertTrue(objectTripType instanceof ArrayTripType);
    // this is different! (and the default way to determine the trip type)
    TripType<Long[]> longArrayTripType = TripFactory.getInstance().getType(Long[].class);
    assertTrue(longArrayTripType instanceof LongArrayTripType);

    // there is no type builder for primitive arrays, since all primitives already
    // provide their own trip type for arrays.
    TripType<long[]> longPrimitiveArrayTripType = TripFactory.getInstance().getType(long[].class);
    assertTrue(longPrimitiveArrayTripType instanceof LongPrimitiveArrayTripType);

    // there is no TimestampArrayTripType, but TimestampTripType is available.
    TripType<Timestamp[]> tsArrayType = TripFactory.getInstance().getType(Timestamp[].class);
    assertTrue(tsArrayType instanceof ArrayTripType);
    TripType<Timestamp> tsType = TripFactory.getInstance().getType(Timestamp.class);
    assertTrue(tsType instanceof TimestampTripType);
  }

  @Test
  public void testObject() {
    // fallback to ObjectTripType
    TripType<TestClass> anyType = TripFactory.getInstance().getType(TestSubClass.class);
    assertTrue(anyType instanceof ObjectTripType);
    assertEquals(anyType.getCategory(), TripTypeCategory.CLASS);
    assertTrue(anyType.isFinal());
    assertEquals(anyType.getComponents().length, 11);
    TripComponent<TestClass, ?> parentField = anyType.getComponents()[0];
    assertEquals(parentField.getName(), "parent");
    assertEquals(parentField.getType().getObjectClass(), TestClass.class);
    assertFalse(parentField.getType().isFinal());
    assertNull(parentField.getDefaultValue());
    TripComponent<TestClass, ?> doubleField = anyType.getComponents()[1];
    assertEquals(doubleField.getName(), "aDouble");
    assertEquals(doubleField.getType().getObjectClass(), Double.class);
    assertTrue(doubleField.getType().isFinal());
    assertNull(doubleField.getDefaultValue());
    TripComponent<TestClass, ?> timestampField = anyType.getComponents()[2];
    assertEquals(timestampField.getName(), "timestamp");
    assertEquals(timestampField.getType().getObjectClass(), Timestamp.class);
    assertTrue(timestampField.getType().isFinal());
    assertNull(timestampField.getDefaultValue());
    TripComponent<TestClass, ?> nameField = anyType.getComponents()[3];
    assertEquals(nameField.getName(), "name");
    assertEquals(nameField.getType().getObjectClass(), String.class);
    assertTrue(nameField.getType().isFinal());
    assertNull(nameField.getDefaultValue());
    TripComponent<TestClass, ?> ageField = anyType.getComponents()[4];
    assertEquals(ageField.getName(), "age");
    assertEquals(ageField.getType().getObjectClass(), Integer.TYPE);
    assertTrue(ageField.getType().isFinal());
    assertEquals(ageField.getDefaultValue(), 0);
    assertEquals(ageField.getType().getCategory(), TripTypeCategory.PRIMITIVE);
    TripComponent<TestClass, ?> maleField = anyType.getComponents()[5];
    assertEquals(maleField.getName(), "male");
    assertEquals(maleField.getType().getObjectClass(), Boolean.class);
    assertTrue(maleField.getType().isFinal());
    assertEquals(maleField.getDefaultValue(), Boolean.TRUE);
    TripComponent<TestClass, ?> childField = anyType.getComponents()[6];
    assertEquals(childField.getName(), "child");
    assertEquals(childField.getType().getObjectClass(), TestClass.class);
    assertFalse(childField.getType().isFinal());
    assertNull(childField.getDefaultValue());
    TripComponent<TestClass, ?> moneyField = anyType.getComponents()[7];
    assertEquals(moneyField.getName(), "money");
    assertEquals(moneyField.getType().getObjectClass(), BigDecimal.class);
    assertTrue(moneyField.getType().isFinal());
    assertNull(moneyField.getDefaultValue());
    TripComponent<TestClass, ?> colorField = anyType.getComponents()[8];
    assertEquals(colorField.getName(), "color");
    assertEquals(colorField.getType().getObjectClass(), TestEnum.class);
    assertTrue(colorField.getType().isFinal());
    assertNull(colorField.getDefaultValue());
    TripComponent<TestClass, ?> personField = anyType.getComponents()[9];
    assertEquals(personField.getName(), "person");
    assertEquals(personField.getType().getObjectClass(), TestRecord.class);
    assertTrue(personField.getType().isFinal());
    assertNull(personField.getDefaultValue());
    TripComponent<TestClass, ?> someObjectField = anyType.getComponents()[10];
    assertEquals(someObjectField.getName(), "someObject");
    assertEquals(someObjectField.getType().getObjectClass(), Object.class);
    assertFalse(someObjectField.getType().isFinal());
    assertNull(someObjectField.getDefaultValue());
  }

  @Test
  public void testRecord() {
    // fallback to RecordTripType
    TripType<TestRecord> recordType = TripFactory.getInstance().getType(TestRecord.class);
    assertTrue(recordType instanceof RecordTripType);
    assertEquals(recordType.getCategory(), TripTypeCategory.RECORD);
    assertTrue(recordType.isFinal());

    assertEquals(recordType.getComponents().length, 6);
    TripComponent<TestRecord, ?> nameField = recordType.getComponents()[0];
    assertEquals(nameField.getName(), "name");
    assertEquals(nameField.getType().getObjectClass(), String.class);
    assertTrue(nameField.getType().isFinal());
    assertNull(nameField.getDefaultValue());
    TripComponent<TestRecord, ?> ageField = recordType.getComponents()[1];
    assertEquals(ageField.getName(), "age");
    assertEquals(ageField.getType().getObjectClass(), Integer.TYPE);
    assertTrue(ageField.getType().isFinal());
    assertEquals(ageField.getType().getCategory(), TripTypeCategory.PRIMITIVE);
    TripComponent<TestRecord, ?> maleField = recordType.getComponents()[2];
    assertEquals(maleField.getName(), "male");
    assertEquals(maleField.getType().getObjectClass(), Boolean.class);
    assertTrue(maleField.getType().isFinal());
    TripComponent<TestRecord, ?> childField = recordType.getComponents()[3];
    assertEquals(childField.getName(), "child");
    assertEquals(childField.getType().getObjectClass(), TestRecord.class);
    assertTrue(childField.getType().isFinal());
    TripComponent<TestRecord, ?> personField = recordType.getComponents()[4];
    assertEquals(personField.getName(), "person");
    assertEquals(personField.getType().getObjectClass(), TestClass.class);
    assertFalse(personField.getType().isFinal());
    TripComponent<TestRecord, ?> someObjectField = recordType.getComponents()[5];
    assertEquals(someObjectField.getName(), "someObject");
    assertEquals(someObjectField.getType().getObjectClass(), Object.class);
    assertFalse(someObjectField.getType().isFinal());
  }

  @Test
  public void testEnum() {
    // fallback to EnumTripType
    TripType<TestEnum> enumType = TripFactory.getInstance().getType(TestEnum.class);
    assertTrue(enumType instanceof EnumTripType);
    assertEquals(enumType.getCategory(), TripTypeCategory.ENUM);
    assertTrue(enumType.isFinal());
    assertEquals(enumType.getComponents().length, 3);
    TripComponent<TestEnum, ?> component = enumType.getComponents()[0];
    assertEquals(component.getDefaultValue(), TestEnum.RED);
    assertEquals(component.getName(), "RED");
    assertEquals(component.getComponentClass(), TestEnum.class);
    component = enumType.getComponents()[1];
    assertEquals(component.getDefaultValue(), TestEnum.GREEN);
    assertEquals(component.getName(), "GREEN");
    assertEquals(component.getComponentClass(), TestEnum.class);
    component = enumType.getComponents()[2];
    assertEquals(component.getDefaultValue(), TestEnum.BLUE);
    assertEquals(component.getName(), "BLUE");
    assertEquals(component.getComponentClass(), TestEnum.class);
  }

}
