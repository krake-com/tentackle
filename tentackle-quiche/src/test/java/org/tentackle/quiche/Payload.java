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

package org.tentackle.quiche;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A transfer object sent through the QUIC transport to verify that TRIP serialization
 * works unchanged over QUIC streams.
 *
 * @param text a text field
 * @param number a numeric field
 * @param blob a byte array, large enough to be worth compressing in the {@code tripcj} tests
 * @param tags a collection field
 */
public record Payload(String text, long number, byte[] blob, List<String> tags) {

  /**
   * Creates a payload with a highly compressible blob of the given size.
   *
   * @param text the text
   * @param blobSize the size of the blob in bytes
   * @return the payload
   */
  public static Payload of(String text, int blobSize) {
    byte[] blob = new byte[blobSize];
    Arrays.fill(blob, (byte) 'x');
    return new Payload(text, blobSize, blob, List.of("alpha", "beta", "gamma"));
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Payload other = (Payload) o;
    return number == other.number && Objects.equals(text, other.text) &&
           Arrays.equals(blob, other.blob) && Objects.equals(tags, other.tags);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(text);
    result = 31 * result + Long.hashCode(number);
    result = 31 * result + Arrays.hashCode(blob);
    result = 31 * result + Objects.hashCode(tags);
    return result;
  }

  @Override
  public String toString() {
    return "Payload[" + text + ", " + number + ", blob=" + blob.length + " bytes, " + tags + "]";
  }

}
