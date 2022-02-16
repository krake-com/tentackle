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

package org.tentackle.pdo.pdofactory;

import org.testng.Reporter;

import org.tentackle.pdo.PdoMethodCache;
import org.tentackle.pdo.PdoMethodCacheProvider;
import org.tentackle.pdo.mock.MockPersistentObject;

import java.io.Serial;


/**
 * Invoice persistence implementation.
 *
 * @author harald
 */
public class InvoicePersistenceImpl<T extends Invoice<T>> extends MockPersistentObject<T,InvoicePersistenceImpl<T>>
       implements InvoicePersistence<T>, PdoMethodCacheProvider<T> {

  @Serial
  private static final long serialVersionUID = 1L;

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static final PdoMethodCache METHOD_CACHE = new PdoMethodCache<>(Invoice.class);

  private String invoiceNo;

  /**
   * Creates an invoice persistence object.
   *
   * @param pdo the invoice
   */
  public InvoicePersistenceImpl(T pdo) {
    super(pdo);
    Reporter.log("InvoicePersistenceImpl created<br/>");
  }

  @Override
  public void setInvoiceNo(String invoiceNo) {
    this.invoiceNo = invoiceNo;
  }

  @Override
  public String getInvoiceNo() {
    return invoiceNo;
  }

  @Override
  public String toString() {
    return "BlahPersist";
  }

  @Override
  @SuppressWarnings("unchecked")
  public PdoMethodCache<T> getPdoMethodCache() {
    return METHOD_CACHE;
  }
}
