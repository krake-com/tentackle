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

package org.tentackle.io;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * A Writer to an Appendable.
 * <p>
 * This is a more generic approach than {@link StringWriter}.
 * StringWriter uses a {@link StringBuffer} instance that cannot be replaced.
 * {@link AppendableWriter} accepts an Appendable in its constructor, which may be a StringBuffer
 * or a StringBuilder.<br>
 * An AppendableWriter using a StringBuffer can be used as a direct replacement for StringWriter.
 * <p>
 * Notice that the AppendableWriter is not thread-safe if a StringBuilder is used.
 *
 * @author harald
 */
public class AppendableWriter extends Writer {

  private static final String NULL_STRING = "null";    // replacement for null


  private final Appendable appendable;     // the appendable


  /**
   * Creates an Appendable writer.
   *
   * @param appendable the appendable
   */
  public AppendableWriter(Appendable appendable) {
    this.appendable = appendable;
  }

  /**
   * Creates an Appendable writer using a {@link StringBuffer}.
   */
  public AppendableWriter() {
    this(new StringBuffer());
  }


  /**
   * Gets the appendable.
   *
   * @return the appendable
   */
  public Appendable getAppendable() {
    return appendable;
  }

  @Override
  public void write(char[] cbuf, int off, int len) throws IOException {
    if ((off < 0) || (off > cbuf.length) || (len < 0)
            || ((off + len) > cbuf.length) || ((off + len) < 0)) {
      throw new IndexOutOfBoundsException();
    }
    else if (len == 0) {
      return;
    }
    appendable.append(new String(cbuf), off, off + len);
  }

  @Override
  public void write(String str) throws IOException {
    appendable.append(str);
  }

  @Override
  public void write(String str, int off, int len) throws IOException {
    appendable.append(str.substring(off, off + len));
  }

  @Override
  public AppendableWriter append(CharSequence csq) throws IOException {
    if (csq == null) {
      write(NULL_STRING);
    }
    else {
      write(csq.toString());
    }
    return this;
  }

  @Override
  public AppendableWriter append(CharSequence csq, int start, int end) throws IOException {
    CharSequence cs = (csq == null ? NULL_STRING : csq);
    write(cs.subSequence(start, end).toString());
    return this;
  }

  @Override
  public AppendableWriter append(char c) throws IOException {
    write(c);
    return this;
  }

  /**
   * Flushing an <code>AppendableWriter</code> has no effect. The methods in this class
   * can be called after the stream has been closed without generating an
   * <code>IOException</code>.
   */
  @Override
  public void flush() {
    // nothing to do
  }

  /**
   * Closing an <code>AppendableWriter</code> has no effect. The methods in this class
   * can be called after the stream has been closed without generating an
   * <code>IOException</code>.
   */
  @Override
  public void close() {
    // nothing to do
  }

  /**
   * Returns the appendable's current value as a string.
   *
   * @return the appendable's content
   */
  @Override
  public String toString() {
    return appendable.toString();
  }

}
