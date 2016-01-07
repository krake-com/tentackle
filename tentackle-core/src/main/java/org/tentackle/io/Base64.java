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



package org.tentackle.io;

import java.util.Arrays;

/**
 * Yet another Base64 implementation...<br>
 *
 * (because Sun does not provide an "official" one)<br>
 *
 * Does not support line-breaking. This needs to be
 * done by the application.
 *
 * @author harald
 */
public class Base64 {


  // map integers to Base64 charset
  private static final char[] intToBase64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

  // map Base64 charset to integers
  private static final int[] base64ToInt = new int[256];

  static {
    // initially build reverse map from intToBase64
    Arrays.fill(base64ToInt, -1);
    for (int i=intToBase64.length-1; i >=0; i--)  {
      base64ToInt[intToBase64[i]] = i;
    }
  }


  /**
   * Decodes base64.
   *
   * @param src the source character array in base64
   * @return the decoded byte array or null if src is null
   *
   * @throws IllegalArgumentException if src is not well-formatted
   */
  public static byte[] base64ToByteArray(char[] src) {

    if (src == null)  {
      return null;
    }

    int srcLen = src.length;
    if (srcLen == 0) {
      return new byte[0];
    }

    if (srcLen % 4 != 0)  {
      throw new IllegalArgumentException("source length must be a multiple of 4 (found " + srcLen + ")");
    }

    // count trailing '='
    int tripletRest = 3;
    for (int i=srcLen-1; i >= 0; i--) {
      if (src[i] == '=')  {
        tripletRest--;
      }
      else  {
        break;
      }
    }
    if (tripletRest == 3) {
      tripletRest = 0;
    }

    int srcNdx = 0;
    int dstNdx = 0;
    int tripletNum = (srcLen >> 2) - (tripletRest > 0 ? 1 : 0);
    byte[] dst = new byte[tripletNum * 3 + tripletRest];       // destination buffer

    // decode full triplets
    for (int i=0; i < tripletNum; i++) {
      // extract 3 byte-value from 4 base64 characters
      int value = 0;
      for (int j=0; j < 4; j++) {
        int b = base64ToInt[src[srcNdx++]];
        if (b == -1)  {
          throw new IllegalArgumentException("illegal character at position " + (srcNdx-1));
        }
        value |= b << (18 - j * 6);
      }
      // create the 3 bytes
      dst[dstNdx++] = (byte)(value >> 16);
      dst[dstNdx++] = (byte)(value >> 8);
      dst[dstNdx++] = (byte)value;
    }

    // decode rest (if any)
    if (tripletRest > 0)  {
      int value = 0;
      for (int j=0; j < tripletRest+1; j++) {
        int b = base64ToInt[src[srcNdx++]];
        if (b == -1)  {
          throw new IllegalArgumentException("illegal character at position " + (srcNdx-1));
        }
        value |= b << (12 - j * 6);
      }
      // create remaining bytes
      dst[dstNdx++] = (byte)(value >> 10);
      if (tripletRest == 2) {
        dst[dstNdx] = (byte)(value >> 2);
      }
    }

    return dst;
  }


  /**
   * Decodes base64.
   *
   * @param src the source String in base64
   * @return the decoded byte array  or null if src is null
   */
  public static byte[] base64ToByteArray(String src) {
    return src == null ? null : base64ToByteArray(src.toCharArray());
  }


  /**
   * Encodes to base64.
   *
   * @param src the byte array
   * @return the character array in base64 encoding or null if src is null
   */
  public static char[] byteArrayToBase64Array(byte[] src) {

    if (src == null)  {
      return null;
    }

    int srcLen = src.length;
    if (srcLen == 0) {
      return new char[0];
    }

    int tripletNum  = srcLen / 3;                                         // number of full 24-bit triplets
    int tripletRest = srcLen % 3;                                         // 0, 1 or 2 bytes left
    char[] dst = new char[(tripletNum + (tripletRest > 0 ? 1 : 0)) << 2]; // destination buffer

    // encode full triplets
    int srcNdx = 0;     // index in source
    int dstNdx = 0;     // index in destination
    for (int i=0; i < tripletNum; i++)  {
      // combine three bytes to a single 24-bit integer
      int triplet = (src[srcNdx++] & 0xff) << 16 |
                    (src[srcNdx++] & 0xff) << 8  |
                    (src[srcNdx++] & 0xff);
      // encode into four base64 chars
      dst[dstNdx++] = intToBase64[(triplet >>> 18) & 0x3f];
      dst[dstNdx++] = intToBase64[(triplet >>> 12) & 0x3f];
      dst[dstNdx++] = intToBase64[(triplet >>> 6) & 0x3f];
      dst[dstNdx++] = intToBase64[triplet & 0x3f];
    }

    // create partial triplet
    if (tripletRest > 0) {
      // Prepare the int
      int triplet = (src[srcNdx++] & 0xff) << 10;
      if (tripletRest == 2) {
        triplet |= (src[srcNdx] & 0xff) << 2;
      }
      dst[dstNdx++] = intToBase64[triplet >> 12];
      dst[dstNdx++] = intToBase64[(triplet >>> 6) & 0x3f];
      dst[dstNdx++] = tripletRest == 2 ? intToBase64[triplet & 0x3f] : '=';
      dst[dstNdx]   = '=';
    }

    return dst;
  }


  /**
   * Encodes to base64.
   *
   * @param src the byte array
   * @return the String in base64 encoding or null if src is null
   */
  public static String byteArrayToBase64(byte[] src) {
    return src == null ? null : new String(byteArrayToBase64Array(src));
  }

  
  private Base64() {
  }

}
