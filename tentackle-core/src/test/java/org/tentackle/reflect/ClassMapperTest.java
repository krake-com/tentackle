/**
 * Tentackle - http://www.tentackle.org
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

import java.util.Map;
import java.util.TreeMap;
import org.tentackle.reflect.classmappertest.CollectiveInvoice;
import org.tentackle.reflect.classmappertest.Invoice;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Classmapper tests.
 *
 * @author harald
 */
public class ClassMapperTest {

  @Test
  public void testClassMapper() {

    Map<String, String> nameMap = new TreeMap<>();
    nameMap.put("org.tentackle.reflect.classmappertest.Invoice", "org.tentackle.reflect.classmappertest.InvoicePersistence");

    ClassMapper mapper = new DefaultClassMapper("test", nameMap, new String[]{"org.tentackle.reflect.classmappertest"});

    try {
      Assert.assertEquals("org.tentackle.reflect.classmappertest.InvoicePersistence", mapper.map(Invoice.class).getName());
    }
    catch (ClassNotFoundException ex) {
      Assert.fail(ex.toString());
    }

    try {
      Assert.assertEquals("org.tentackle.reflect.classmappertest.InvoicePersistence", mapper.mapLenient(
              CollectiveInvoice.class).getName());
    }
    catch (ClassNotFoundException ex) {
      Assert.fail(ex.toString());
    }

    nameMap = new TreeMap<>();
    nameMap.put("org.tentackle.reflect.classmappertest.Invoice", "org.tentackle.reflect.classmappertest.InvoicePersistence");
    nameMap.put("org.tentackle.reflect.classmappertest.CollectiveInvoice",
            "org.tentackle.reflect.classmappertest.CollectiveInvoicePersistence");
    mapper = new DefaultClassMapper("test", nameMap, new String[]{"org.tentackle.reflect.classmappertest"});

    try {
      Assert.assertEquals("org.tentackle.reflect.classmappertest.CollectiveInvoicePersistence", mapper.map(
              CollectiveInvoice.class).getName());
    }
    catch (ClassNotFoundException ex) {
      Assert.fail(ex.toString());
    }
  }
}
