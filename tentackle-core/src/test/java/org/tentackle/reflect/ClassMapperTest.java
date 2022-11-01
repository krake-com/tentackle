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

import org.tentackle.reflect.classmapper.CollectiveInvoice;
import org.tentackle.reflect.classmapper.Invoice;

import java.util.Map;
import java.util.TreeMap;

/**
 * Classmapper tests.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ClassMapperTest {

  @Test
  public void testClassMapper() {

    Map<String, String> nameMap = new TreeMap<>();
    nameMap.put("org.tentackle.reflect.classmapper.Invoice", "org.tentackle.reflect.classmapper.InvoicePersistence");

    ClassMapper mapper = new DefaultClassMapper(
            "test", getClass().getClassLoader(), nameMap, new String[]{"org.tentackle.reflect.classmapper"});

    try {
      Assert.assertEquals("org.tentackle.reflect.classmapper.InvoicePersistence", mapper.map(Invoice.class).getName());
    }
    catch (ClassNotFoundException ex) {
      Assert.fail(ex.toString());
    }

    try {
      Assert.assertEquals("org.tentackle.reflect.classmapper.InvoicePersistence", mapper.mapLenient(
              CollectiveInvoice.class).getName());
    }
    catch (ClassNotFoundException ex) {
      Assert.fail(ex.toString());
    }

    nameMap = new TreeMap<>();
    nameMap.put("org.tentackle.reflect.classmapper.Invoice", "org.tentackle.reflect.classmapper.InvoicePersistence");
    nameMap.put("org.tentackle.reflect.classmapper.CollectiveInvoice",
            "org.tentackle.reflect.classmapper.CollectiveInvoicePersistence");
    mapper = new DefaultClassMapper(
            "test", getClass().getClassLoader(), nameMap, new String[]{"org.tentackle.reflect.classmapper"});

    try {
      Assert.assertEquals("org.tentackle.reflect.classmapper.CollectiveInvoicePersistence", mapper.map(
              CollectiveInvoice.class).getName());
    }
    catch (ClassNotFoundException ex) {
      Assert.fail(ex.toString());
    }
  }
}
