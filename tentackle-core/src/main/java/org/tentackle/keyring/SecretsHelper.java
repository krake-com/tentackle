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

package org.tentackle.keyring;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Converts secrets between characters and UTF-8 bytes without ever creating a {@link String}.
 * <p>
 * Strings are immutable and cannot be wiped, so a secret turned into one stays in the heap until it
 * happens to be overwritten. All intermediate buffers created here are wiped before they are released.
 */
public final class SecretsHelper {

  /**
   * Encodes a secret to UTF-8.
   *
   * @param secret the secret
   * @return the UTF-8 bytes, to be wiped by the caller
   * @throws KeyringException if the secret is not valid unicode
   */
  public static byte[] encode(char[] secret) {
    try {
      ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .encode(CharBuffer.wrap(secret));
      try {
        return Arrays.copyOfRange(buffer.array(), buffer.arrayOffset() + buffer.position(),
                                  buffer.arrayOffset() + buffer.limit());
      }
      finally {
        wipe(buffer.array());
      }
    }
    catch (CharacterCodingException ex) {
      throw new KeyringException("secret is not valid unicode", ex);
    }
  }

  /**
   * Decodes a secret from UTF-8.
   *
   * @param bytes the UTF-8 bytes
   * @return the secret, to be wiped by the caller
   * @throws KeyringException if the bytes are not valid UTF-8
   */
  public static char[] decode(byte[] bytes) {
    try {
      CharBuffer buffer = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes));
      try {
        return Arrays.copyOfRange(buffer.array(), buffer.arrayOffset() + buffer.position(),
                                  buffer.arrayOffset() + buffer.limit());
      }
      finally {
        wipe(buffer.array());
      }
    }
    catch (CharacterCodingException ex) {
      throw new KeyringException("stored secret is not valid UTF-8", ex);
    }
  }

  /**
   * Wipes a secret.
   *
   * @param secret the secret, null if none
   */
  public static void wipe(char[] secret) {
    if (secret != null) {
      Arrays.fill(secret, '\0');
    }
  }

  /**
   * Wipes a secret.
   *
   * @param secret the secret, null if none
   */
  public static void wipe(byte[] secret) {
    if (secret != null) {
      Arrays.fill(secret, (byte) 0);
    }
  }


  private SecretsHelper() {
  }

}
