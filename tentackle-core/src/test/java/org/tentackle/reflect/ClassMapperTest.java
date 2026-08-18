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

package org.tentackle.reflect;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.log.Logger;
import org.tentackle.reflect.classmapper.CollectiveInvoice;
import org.tentackle.reflect.classmapper.Invoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Classmapper tests.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ClassMapperTest {

  private static final String INVOICE = "org.tentackle.reflect.classmapper.Invoice";
  private static final String INVOICE_PERSISTENCE = "org.tentackle.reflect.classmapper.InvoicePersistence";
  private static final String COLLECTIVE_INVOICE = "org.tentackle.reflect.classmapper.CollectiveInvoice";
  private static final String COLLECTIVE_INVOICE_PERSISTENCE =
          "org.tentackle.reflect.classmapper.CollectiveInvoicePersistence";
  private static final String PACKAGE = "org.tentackle.reflect.classmapper";
  private static final String NESTED = "org.tentackle.reflect.ClassMapperTest$Nested";
  private static final String OBJECT_ARRAY = "java.lang.Object[]";

  /**
   * A member class, i.e. its binary name differs from its canonical name.
   */
  public static class Nested {}

  /**
   * The same as a record, which adds {@link Record} to the superclass chain.
   */
  public record NestedRecord(String name) {}


  private DefaultClassMapper newMapper(Map<String, String> nameMap) {
    return new DefaultClassMapper("test", getClass().getClassLoader(), nameMap, new String[]{PACKAGE});
  }


  @Test
  public void testClassMapper() throws ClassNotFoundException {

    Map<String, String> nameMap = new TreeMap<>();
    nameMap.put(INVOICE, INVOICE_PERSISTENCE);

    ClassMapper mapper = newMapper(nameMap);

    assertEquals(mapper.map(Invoice.class).getName(), INVOICE_PERSISTENCE);
    // CollectiveInvoice has no explicit mapping -> lenient resolves via the Invoice super interface
    assertEquals(mapper.mapLenient(CollectiveInvoice.class).getName(), INVOICE_PERSISTENCE);

    nameMap = new TreeMap<>();
    nameMap.put(INVOICE, INVOICE_PERSISTENCE);
    nameMap.put(COLLECTIVE_INVOICE, COLLECTIVE_INVOICE_PERSISTENCE);
    mapper = newMapper(nameMap);

    assertEquals(mapper.map(CollectiveInvoice.class).getName(), COLLECTIVE_INVOICE_PERSISTENCE);
  }


  @Test
  public void testMapByName() throws ClassNotFoundException {
    Map<String, String> nameMap = new TreeMap<>();
    nameMap.put(INVOICE, INVOICE_PERSISTENCE);
    ClassMapper mapper = newMapper(nameMap);
    assertEquals(mapper.map(INVOICE).getName(), INVOICE_PERSISTENCE);
  }


  @Test
  public void testNestedClassArrayMapping() throws ClassNotFoundException {
    Map<String, String> nameMap = new TreeMap<>();
    nameMap.put(NESTED + "[]", INVOICE_PERSISTENCE);
    nameMap.put(OBJECT_ARRAY, COLLECTIVE_INVOICE_PERSISTENCE);
    ClassMapper mapper = newMapper(nameMap);

    // explicit mapping, keyed by the binary name of the component class
    assertEquals(mapper.mapLenient(Nested[].class).getName(), INVOICE_PERSISTENCE);

    // no explicit mapping: the lenient lookup climbs NestedRecord[] -> Record[] -> Object[].
    // Before, the component class was looked up by its canonical name, which is not loadable
    // for a member class, so the array silently ended up unmapped.
    assertEquals(mapper.mapLenient(NestedRecord[].class).getName(), COLLECTIVE_INVOICE_PERSISTENCE);

    // ... and the same for a plain member class, i.e. without the explicit mapping
    nameMap = new TreeMap<>();
    nameMap.put(OBJECT_ARRAY, COLLECTIVE_INVOICE_PERSISTENCE);
    mapper = newMapper(nameMap);
    assertEquals(mapper.mapLenient(Nested[].class).getName(), COLLECTIVE_INVOICE_PERSISTENCE);
  }


  @Test
  public void testUnmappedThrows() {
    ClassMapper mapper = newMapper(new TreeMap<>());
    assertThrows(ClassNotFoundException.class, () -> mapper.map(Invoice.class));
    assertThrows(ClassNotFoundException.class, () -> mapper.mapLenient(CollectiveInvoice.class));
  }


  @Test
  public void testDefaultClass() throws ClassNotFoundException {
    DefaultClassMapper mapper = newMapper(new TreeMap<>());
    Assert.assertNull(mapper.getDefaultClass());
    // no mapping and no default -> exception
    assertThrows(ClassNotFoundException.class, () -> mapper.map("no.such.Class"));

    mapper.setDefaultClass(String.class);
    assertSame(mapper.getDefaultClass(), String.class);
    // now the default class is returned instead of failing
    assertSame(mapper.map("no.such.Class"), String.class);
  }


  @Test
  public void testListeners() throws ClassNotFoundException {
    Map<String, String> nameMap = new TreeMap<>();
    nameMap.put(INVOICE, INVOICE_PERSISTENCE);
    DefaultClassMapper mapper = newMapper(nameMap);

    List<String> notified = new ArrayList<>();
    ClassMappedListener listener = (name, clazz) -> notified.add(name);
    mapper.addListener(listener);

    mapper.map(Invoice.class);
    assertEquals(notified, List.of(INVOICE));

    // second lookup is served from the cache and does not notify again
    mapper.map(Invoice.class);
    assertEquals(notified.size(), 1);

    assertTrue(mapper.removeListener(listener));
    assertFalse(mapper.removeListener(listener));
    mapper.removeAllListeners();
  }


  @Test
  public void testPropertiesConstructor() throws ClassNotFoundException {
    Properties props = new Properties();
    props.setProperty(INVOICE, INVOICE_PERSISTENCE);
    DefaultClassMapper mapper = new DefaultClassMapper("props", getClass().getClassLoader(), props, null);
    assertEquals(mapper.map(Invoice.class).getName(), INVOICE_PERSISTENCE);
    // not lenient and not mapped -> fails
    assertThrows(ClassNotFoundException.class, () -> mapper.map(CollectiveInvoice.class));
  }


  @Test
  public void testAccessorsAndImmutableMapCopy() {
    Map<String, String> nameMap = new TreeMap<>();
    nameMap.put(INVOICE, INVOICE_PERSISTENCE);
    DefaultClassMapper mapper = new DefaultClassMapper("mymapper", getClass().getClassLoader(),
        nameMap, new String[]{PACKAGE});

    assertEquals(mapper.getName(), "mymapper");
    assertEquals(mapper.toString(), "mymapper");
    assertSame(mapper.getNameMap(), nameMap);
    assertEquals(mapper.getPackageNames(), new String[]{PACKAGE});

    Map<String, String> copy = mapper.getMap();
    assertEquals(copy.get(INVOICE), INVOICE_PERSISTENCE);
    // getMap() returns an immutable copy
    assertThrows(UnsupportedOperationException.class, () -> copy.put("x", "y"));
  }


  @Test
  public void testLogLevel() {
    DefaultClassMapper mapper = newMapper(new TreeMap<>());
    assertEquals(mapper.getLogLevel(), Logger.Level.FINE);   // default
    mapper.setLogLevel(Logger.Level.INFO);
    assertEquals(mapper.getLogLevel(), Logger.Level.INFO);
  }
}
