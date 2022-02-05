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

package org.tentackle.misc;

import java.io.Serial;
import java.util.Objects;
import java.util.UUID;

/**
 * Extended UUID.<br>
 * An {@code XUUID} is a combination of an ID and a {@link UUID}.
 * The external representation is {@code "id-uuid"}, i.e. the ID prepended to the UUID separated by a dash.
 * With {@code XUUID}s, the internal logic (domain/persistence) does not need to be changed, but an additional check for the UUID
 * can be applied to prevent brute force attacks.<br>
 */
public class XUUID implements Identifiable {

  @Serial
  private static final long serialVersionUID = 1L;

  private final long id;
  private final Identifiable identifiable;
  private final UUID uuid;

  /**
   * Creates an extended UUID.
   *
   * @param id the ID
   * @param uuid the UUID
   */
  public XUUID(long id, UUID uuid) {
    this.id = id;
    this.uuid = Objects.requireNonNull(uuid, "uuid");
    this.identifiable = null;
  }

  /**
   * Creates an extended UUID from an {@link Identifiable}.
   *
   * @param identifiable the identifiable
   * @param uuid the UUID
   */
  public XUUID(Identifiable identifiable, UUID uuid) {
    this.identifiable = Objects.requireNonNull(identifiable, "identifiable");
    this.id = identifiable.getId();
    this.uuid = Objects.requireNonNull(uuid, "uuid");
  }

  @Override
  public long getId() {
    return id;
  }

  @Override
  public String toGenericString() {
    StringBuilder buf = new StringBuilder();
    if (identifiable != null) {
      buf.append(identifiable.toGenericString());
    }
    else {
      buf.append('[').append(id).append(']');
    }
    buf.append('{').append(uuid).append('}');
    return buf.toString();
  }

  @Override
  public String toString() {
    return id + "-" + uuid;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    XUUID xuuid = (XUUID) o;

    if (id != xuuid.id) {
      return false;
    }
    return uuid.equals(xuuid.uuid);
  }

  @Override
  public int hashCode() {
    int result = (int) (id ^ (id >>> 32));
    result = 31 * result + uuid.hashCode();
    return result;
  }

}
