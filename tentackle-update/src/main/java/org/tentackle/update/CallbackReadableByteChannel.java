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
package org.tentackle.update;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A {@link ReadableByteChannel} with an optional callback lambda to show the progress.
 * <p>
 * After each read that returned data, the consumer receives the ratio of the bytes read so far
 * to the expected size. If the expected size is negative (unknown), the ratio is negative as well,
 * which a JavaFX progress bar, for example, displays as indeterminate.
 */
public class CallbackReadableByteChannel implements ReadableByteChannel {

  private final ReadableByteChannel channel;
  private final Consumer<Double> progressConsumer;
  private final long expectedSize;

  // Size read so far. A long because the images may well exceed 2GB.
  private final AtomicLong size = new AtomicLong();

  /**
   * Creates a callback read channel.
   *
   * @param channel the channel to be wrapped
   * @param expectedSize the expected size in bytes, negative if indefinite
   * @param progressConsumer the optional consumer receiving the progress from 0.0 to 1.0 (negative if indefinite), null if none
   */
  public CallbackReadableByteChannel(ReadableByteChannel channel, long expectedSize, Consumer<Double> progressConsumer) {
    this.channel = channel;
    this.expectedSize = expectedSize;
    this.progressConsumer = progressConsumer;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    int n;
    if ((n = channel.read(dst)) > 0) {
      long sz = size.addAndGet(n);
      if (progressConsumer != null) {
        double progress = sz > 0 ? (double) sz / (double) expectedSize : 0.0;
        progressConsumer.accept(progress);
      }
    }
    return n;
  }

  @Override
  public boolean isOpen() {
    return channel.isOpen();
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }

  /**
   * Gets the number of bytes read so far.
   *
   * @return the size
   */
  public long getSize() {
    return size.get();
  }

}
