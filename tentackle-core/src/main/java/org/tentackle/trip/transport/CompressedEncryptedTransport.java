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

package org.tentackle.trip.transport;

import org.tentackle.io.CompressedInputStream;
import org.tentackle.trip.TransportService;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * Transport implementation for compressed and encrypted TCP.<br>
 * Uses a {@link org.tentackle.common.Cryptor} for symmetric encryption.
 * This kind of encryption is less secure than SSL since
 * the key is pre-shared between client and server.
 * However, it does not need a certificate.<br>
 * The data is compressed before being encrypted.
 */
@TransportService(CompressedEncryptedTransport.PROTOCOL)
public class CompressedEncryptedTransport extends EncryptedTransport {

  /**
   * The protocol name for compressed encrypted TCP.
   */
  public static final String PROTOCOL = "tripce";

  /**
   * Creates a new transport.
   *
   * @param uri the URI of the transport
   */
  public CompressedEncryptedTransport(URI uri) {
    super(uri);
  }


  @Override
  protected OutputStream createEncryptingOutputStream(OutputStream outputStream) {
    return new FastCompressedOutputStream(super.createEncryptingOutputStream(outputStream));
  }

  @Override
  protected InputStream createDecryptingInputStream(InputStream inputStream) {
    return new CompressedInputStream(super.createDecryptingInputStream(inputStream));
  }

}
