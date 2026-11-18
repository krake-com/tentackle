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

package org.tentackle.common;

import java.io.ObjectInputFilter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Global settings.
 *
 * @author harald
 */
public class Settings {

  /**
   * System property holding the initial application deserialization filter pattern.<br>
   * The syntax is that of {@link ObjectInputFilter.Config#createFilter(String)}, for example
   * {@code "com.example.myapp.**;java.base/*;!*"}.
   *
   * @see #getDeserializationFilter()
   */
  public static final String DESERIALIZATION_FILTER_PROPERTY = "tentackle.serialFilter";

  /**
   * The limits always applied when deserializing via Java Object Serialization.<br>
   * Generous enough for any legitimate object graph, but rejecting graphs crafted to exhaust the stack or heap.
   */
  public static final String DESERIALIZATION_LIMITS = "maxdepth=1000;maxrefs=10000000";


  private static volatile Charset encodingCharset = StandardCharsets.UTF_8;

  /**
   * The explicitly set application deserialization filter, null if not set.
   */
  private static volatile FilterSetting deserializationFilterSetting;

  /**
   * Wraps an explicitly set filter, since null is a valid filter.
   *
   * @param filter the filter, null if none
   */
  private record FilterSetting(ObjectInputFilter filter) {}

  /**
   * Holds the initial filter, created lazily from {@link #DESERIALIZATION_FILTER_PROPERTY}.
   */
  private static final class InitialFilterHolder {

    private static final ObjectInputFilter FILTER;
    private static final TentackleRuntimeException FAILURE;

    static {
      ObjectInputFilter filter = null;
      TentackleRuntimeException failure = null;
      String pattern = System.getProperty(DESERIALIZATION_FILTER_PROPERTY);
      if (pattern != null && !pattern.isBlank()) {
        try {
          filter = ObjectInputFilter.Config.createFilter(pattern);
        }
        catch (IllegalArgumentException ex) {
          // fail closed: every deserialization fails, rather than silently running unfiltered
          failure = new TentackleRuntimeException(
              "invalid deserialization filter " + DESERIALIZATION_FILTER_PROPERTY + "=" + pattern, ex);
        }
      }
      FILTER = filter;
      FAILURE = failure;
    }

    private static ObjectInputFilter getFilter() {
      if (FAILURE != null) {
        throw FAILURE;
      }
      return FILTER;
    }

    private InitialFilterHolder() {}
  }


  /**
   * Gets the encoding used to read and write files.<br>
   * Usually set by the Maven plugins from "project.build.sourceEncoding".
   * <p>
   * The charset is also used by the {@link Cryptor} to convert texts, salts and passphrases to bytes.
   * Hence, texts encrypted at build time can only be decrypted at runtime with the same charset,
   * unless they consist of ASCII characters only.
   *
   * @return the charset, default is {@link StandardCharsets#UTF_8}
   */
  public static Charset getEncodingCharset() {
    return encodingCharset;
  }

  /**
   * Sets the encoding used to read and write files.
   *
   * @param encodingCharset the charset, default is {@link StandardCharsets#UTF_8}, must not be null
   */
  public static void setEncodingCharset(Charset encodingCharset) {
    Settings.encodingCharset = encodingCharset;
  }

  /**
   * Gets the application deserialization filter.<br>
   * Applied whenever the framework deserializes an object graph via Java Object Serialization from bytes
   * that are not necessarily trusted, such as {@link Binary#getObject()} and {@link DefaultObjectSerializer}.
   * Notice that the bytes of a {@link Binary} usually come from the database or a remote peer and bypass
   * any allowlist of the remoting layer. Applications processing such data should provide an allowlist,
   * either via the system property {@value #DESERIALIZATION_FILTER_PROPERTY} or {@link #setDeserializationFilter}.
   * <p>
   * The initial filter is created from the system property {@value #DESERIALIZATION_FILTER_PROPERTY}, if set.
   *
   * @return the filter, null if none
   * @throws TentackleRuntimeException if the system property holds an invalid filter pattern
   * @see #createDeserializationFilter(long)
   */
  public static ObjectInputFilter getDeserializationFilter() {
    FilterSetting setting = deserializationFilterSetting;
    return setting != null ? setting.filter() : InitialFilterHolder.getFilter();
  }

  /**
   * Sets the application deserialization filter.<br>
   * Replaces the filter derived from the system property {@value #DESERIALIZATION_FILTER_PROPERTY}.
   *
   * @param filter the filter, null to apply the limits and the JVM-wide filter only
   * @see #getDeserializationFilter()
   */
  public static void setDeserializationFilter(ObjectInputFilter filter) {
    deserializationFilterSetting = new FilterSetting(filter);
  }

  /**
   * Creates the effective filter for a stream deserialized via Java Object Serialization.<br>
   * It merges:
   * <ol>
   *   <li>the {@link #DESERIALIZATION_LIMITS} and, if the size of the stream is known, its size as the limit
   *   of the stream bytes and array lengths (an array cannot have more elements than the stream has bytes),</li>
   *   <li>the application filter, see {@link #getDeserializationFilter()},</li>
   *   <li>the JVM-wide filter {@code jdk.serialFilter}, if any. It must be merged explicitly,
   *   since setting a stream filter replaces it by default.</li>
   * </ol>
   * A class rejected by any of these filters is rejected.
   *
   * @param maxBytes the size of the stream in bytes, 0 or negative if unknown
   * @return the filter, never null
   * @throws TentackleRuntimeException if the system property holds an invalid filter pattern
   */
  public static ObjectInputFilter createDeserializationFilter(long maxBytes) {
    String limits = DESERIALIZATION_LIMITS;
    if (maxBytes > 0) {
      limits += ";maxbytes=" + maxBytes + ";maxarray=" + maxBytes;
    }
    ObjectInputFilter filter = ObjectInputFilter.merge(ObjectInputFilter.Config.createFilter(limits), getDeserializationFilter());
    return ObjectInputFilter.merge(filter, ObjectInputFilter.Config.getSerialFilter());
  }


  private Settings() {
  }
}
