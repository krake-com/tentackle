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

package org.tentackle.trip.impl;

import org.tentackle.common.Service;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service(TripFactory.class)
@SuppressWarnings("missing-explicit-ctor")
public class ExtendedTripFactory extends DefaultTripFactory {

  @Override
  public Collection<OpCode> getAllOpCodes() {
    Collection<OpCode> opCodes = new ArrayList<>(super.getAllOpCodes());
    opCodes.addAll(List.of(AdditionalOpCode.values()));
    return opCodes;
  }

  @Override
  public OpCode getOpCode(int code) {
    return code < 0x80 ? super.getOpCode(code) : AdditionalOpCode.valueOf(code);
  }

}
