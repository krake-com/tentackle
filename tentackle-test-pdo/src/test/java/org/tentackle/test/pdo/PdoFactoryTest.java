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

package org.tentackle.test.pdo;

import java.util.Map;
import java.util.TreeMap;
import org.tentackle.pdo.AbstractOperationFactory;
import org.tentackle.pdo.AbstractPdoFactory;
import org.tentackle.pdo.OperationFactory;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.reflect.ClassMapper;
import org.tentackle.reflect.DefaultClassMapper;
import org.tentackle.test.pdo.pdofactory.CollectiveInvoice;
import org.tentackle.test.pdo.pdofactory.CollectiveInvoiceDomainImpl;
import org.tentackle.test.pdo.pdofactory.Invoice;
import org.tentackle.test.pdo.pdofactory.InvoicePersistenceImpl;
import org.tentackle.test.pdo.pdofactory.LogItInterceptor;
import org.tentackle.test.pdo.pdofactory.SingleInvoice;
import org.tentackle.test.pdo.pdofactory.SingleInvoiceDomainImpl;
import org.tentackle.test.pdo.pdofactory.SomeOperation;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests for the PDO Factory.
 *
 * @author harald
 */
public class PdoFactoryTest {

  @Test
  public void testPdoFactory() {

    Map<String, String> domainObjectMap = new TreeMap<>();
    domainObjectMap.put("org.tentackle.test.pdo.pdofactory.SingleInvoice", "org.tentackle.test.pdo.pdofactory.SingleInvoiceDomainImpl");
    domainObjectMap.put("org.tentackle.test.pdo.pdofactory.CollectiveInvoice", "org.tentackle.test.pdo.pdofactory.CollectiveInvoiceDomainImpl");
    final ClassMapper domainObjectMapper = new DefaultClassMapper(
            "domain", getClass().getClassLoader(), domainObjectMap, new String[]{"org.tentackle.test.pdo.pdofactory"});

    Map<String, String> persistenceObjectMap = new TreeMap<>();
    persistenceObjectMap.put("org.tentackle.test.pdo.pdofactory.Invoice", "org.tentackle.test.pdo.pdofactory.InvoicePersistenceImpl");
    final ClassMapper persistenceObjectMapper = new DefaultClassMapper(
            "persistence", getClass().getClassLoader(), persistenceObjectMap, new String[]{"org.tentackle.test.pdo.pdofactory"});

    PdoFactory pdoFactory = new AbstractPdoFactory() {

      @Override
      public ClassMapper getPersistenceMapper() {
        return persistenceObjectMapper;
      }

      @Override
      public ClassMapper getDomainMapper() {
        return domainObjectMapper;
      }
    };

    SingleInvoice invoice = pdoFactory.create(SingleInvoice.class);

    Assert.assertTrue(invoice instanceof Invoice);
    Assert.assertTrue(SingleInvoice.class.isAssignableFrom(invoice.getClass()));
    Assert.assertNotEquals(SingleInvoice.class, invoice.getClass());
    Assert.assertEquals(SingleInvoice.class, invoice.getEffectiveClass());
    invoice.setInvoiceNo("HarrHarr");
    Assert.assertEquals("1234.00", invoice.getSumAsString());
    Assert.assertEquals("1234.00", LogItInterceptor.lastResult);
    Assert.assertEquals("BlahDomain", invoice.toString());
    Assert.assertEquals(invoice.getEffectiveClass(), SingleInvoice.class);
    Assert.assertEquals(pdoFactory.getDomainClass(SingleInvoice.class), SingleInvoiceDomainImpl.class);
    Assert.assertEquals(invoice.getDomainDelegate().getClass(), SingleInvoiceDomainImpl.class);
    Assert.assertEquals(pdoFactory.getPersistentClass(SingleInvoice.class), InvoicePersistenceImpl.class);
    Assert.assertEquals(invoice.getPersistenceDelegate().getClass(), InvoicePersistenceImpl.class);

    CollectiveInvoice collinv = pdoFactory.create(CollectiveInvoice.class);
    Assert.assertTrue(collinv instanceof Invoice);
    collinv.setInvoiceNo("HarrHarr");
    collinv.sumup();
    Assert.assertEquals("HarrHarr", collinv.getInvoiceNo());
    Assert.assertEquals("9999.00", collinv.getSumAsString());
    Assert.assertEquals("9999.00", LogItInterceptor.lastResult);
    Assert.assertEquals("CollectiveBlahDomain", collinv.toString());
    Assert.assertEquals(collinv.getEffectiveClass(), CollectiveInvoice.class);
    Assert.assertEquals(pdoFactory.getDomainClass(CollectiveInvoice.class), CollectiveInvoiceDomainImpl.class);
    Assert.assertEquals(collinv.getDomainDelegate().getClass(), CollectiveInvoiceDomainImpl.class);
    Assert.assertEquals(collinv.getPersistenceDelegate().getClass(), InvoicePersistenceImpl.class);

    Map<String, String> domainOperationMap = new TreeMap<>();
    final ClassMapper domainOperationMapper = new DefaultClassMapper(
            "domain", getClass().getClassLoader(), domainOperationMap, new String[]{"org.tentackle.test.pdo.pdofactory"});

    Map<String, String> persistenceOperationMap = new TreeMap<>();
    persistenceOperationMap.put("org.tentackle.test.pdo.pdofactory.SomeOperation", "org.tentackle.test.pdo.pdofactory.SomeOperationPersistenceImpl");
    final ClassMapper persistenceOperationMapper = new DefaultClassMapper(
            "persistence", getClass().getClassLoader(), persistenceOperationMap, new String[]{"org.tentackle.test.pdo.pdofactory"});

    OperationFactory operationFactory = new AbstractOperationFactory() {

      @Override
      public ClassMapper getPersistenceMapper() {
        return persistenceOperationMapper;
      }

      @Override
      public ClassMapper getDomainMapper() {
        return domainOperationMapper;
      }
    };

    SomeOperation someOper = operationFactory.create(SomeOperation.class);
    Assert.assertEquals("hello", someOper.echo("hello"));
  }

}