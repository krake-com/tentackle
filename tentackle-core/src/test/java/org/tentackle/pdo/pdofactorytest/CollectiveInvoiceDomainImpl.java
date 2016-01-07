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


package org.tentackle.pdo.pdofactorytest;

import org.testng.Reporter;

/**
 * Collective invoice domain implementation.
 *
 * @author harald
 */
public class CollectiveInvoiceDomainImpl extends InvoiceDomainImpl<CollectiveInvoice> implements CollectiveInvoiceDomain {

  /**
   * Creates a collective invoice domain object.
   *
   * @param pdo the invoice
   */
  public CollectiveInvoiceDomainImpl(CollectiveInvoice pdo) {
    super(pdo);
  }

  @Override
  public void sumup() {
    Reporter.log("sumup invoked<br/>");
  }

  @Override
  public String toString() {
    return "Collective" + super.toString();
  }

  @Override
  public String getSumAsString() {
    return "9999.00";
  }
}
