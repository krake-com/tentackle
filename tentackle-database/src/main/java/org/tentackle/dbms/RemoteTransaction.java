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

package org.tentackle.dbms;

import org.tentackle.reflect.CanonicalConstructor;
import org.tentackle.reflect.CanonicalName;
import org.tentackle.trip.TripReferencable;

import java.io.Serial;

/**
 * Remote transaction status.<br>
 * Sent from the server to the client.
 */
@TripReferencable(TripReferencable.NEVER)
public final class RemoteTransaction extends AbstractTransaction {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates a remote transaction status.
   *
   * @param txVoucher transaction voucher, 0 if no new transaction begun
   * @param txName    transaction name
   * @param txNumber  transaction number
   * @param txLevel   the initial nesting level (usually 1)
   */
  @CanonicalConstructor
  public RemoteTransaction(@CanonicalName("txVoucher") long txVoucher,
                           @CanonicalName("txName") String txName,
                           @CanonicalName("txNumber") long txNumber,
                           @CanonicalName("txLevel") int txLevel) {

    super(txVoucher, txName, txNumber, txLevel);
  }

}
