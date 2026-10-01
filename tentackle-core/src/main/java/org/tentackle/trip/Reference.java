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

package org.tentackle.trip;

interface TripTypeHolder {
  @SuppressWarnings("rawtypes")
  TripType<Reference> REF_INSTANCE = TripFactory.getInstance().getType(Reference.class);
}


/**
 * A reference to an object within a stream.
 * <p>
 * An object is written only once per message. Later occurrences are sent as references only,
 * which preserves shared objects and cycles and implements deduplication.
 *
 * @param <T> the type of the de-duplicatable object
 */
public final class Reference<T> {

  private final int referenceId;      // the reference id
  private final boolean isSender;     // true if this is a reference from the sender, false if from the receiver
  private boolean sent;               // true already sent, false if object content follows
  private boolean objectFollowing;    // true if the object content follows this reference
  private T object;                   // the object the reference belongs to

  /**
   * Creates a new reference.
   *
   * @param referenceId the reference id, starting from 0
   * @param sent true if already sent, false if the object content follows in the stream
   * @param isSender true if this is a reference from the sender, false if from the receiver
   * @throws IllegalArgumentException if referenceId is negative
   */
  public Reference(int referenceId, boolean sent, boolean isSender) {
    this.referenceId = referenceId;
    if (referenceId < 0) {
      throw new IllegalArgumentException("reference id must be >= 0");
    }
    this.sent = sent;
    this.objectFollowing = !sent;
    this.isSender = isSender;
  }

  /**
   * Creates an unsent sender reference.
   *
   * @param referenceId the reference id, starting from 0
   */
  public Reference(int referenceId) {
    this(referenceId, false, true);
  }

  /**
   * Gets the reference id.
   *
   * @return the reference id, starting from 0
   */
  public int getReferenceId() {
    return referenceId;
  }

  /**
   * Checks if this is a reference from the sender.
   *
   * @return true if this is a reference from the sender, false if from the receiver
   */
  public boolean isSender() {
    return isSender;
  }

  /**
   * Checks if the reference has been sent.
   *
   * @return true if already sent, false if the object content follows in the stream
   */
  public boolean isSent() {
    return sent;
  }

  /**
   * Gets the referenced object.
   *
   * @return the referenced object
   */
  public T getObject() {
    return object;
  }

  /**
   * Sets the referenced object.
   *
   * @param object the referenced object
   */
  public void setObject(T object) {
    this.object = object;
  }

  /**
   * Sends the reference to the given stream.
   *
   * @param stream the stream to write to
   */
  public void send(TripStream stream) {
    TripTypeHolder.REF_INSTANCE.write(this, stream);
    markSent();
  }

  /**
   * Marks the reference as sent.
   */
  public void markSent() {
    objectFollowing = !sent;
    sent = true;
  }

  /**
   * Marks the reference as received and the object's content read.
   */
  public void markReceived() {
    sent = true;
    objectFollowing = false;
  }

  /**
   * Returns whether the object content follows this reference.
   *
   * @return true if the object content follows this reference
   */
  public boolean isObjectFollowing() {
    return objectFollowing;
  }

  /**
   * Reads a reference from the given stream.
   *
   * @param <T> the type of the referenced object
   * @param stream the stream to read from
   * @param opCode the op code (already read from the stream)
   * @return the reference
   */
  @SuppressWarnings("unchecked")
  public static <T> Reference<T> read(TripStream stream, OpCode opCode) {
    return (Reference<T>) TripTypeHolder.REF_INSTANCE.read(stream, opCode);
  }


  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (Reference<?>) obj;
    return this.referenceId == that.referenceId;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(referenceId);
  }

  @Override
  public String toString() {
    return "R" + referenceId;
  }

}
