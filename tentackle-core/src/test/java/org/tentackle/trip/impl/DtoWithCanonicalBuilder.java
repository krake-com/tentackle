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

import org.tentackle.reflect.CanonicalBuilder;

import java.util.Objects;

@CanonicalBuilder(DtoWithCanonicalBuilder.Builder.class)
public class DtoWithCanonicalBuilder {

  private final long userId;
  private final String userName;

  // either class or constructor, but not both!
  // @CanonicalBuilder(Builder.class)
  protected DtoWithCanonicalBuilder(Builder builder) {
    this.userId = builder.userId;
    this.userName = builder.userName;
  }

  public long getUserId() {
    return userId;
  }

  public String getUserName() {
    return userName;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    DtoWithCanonicalBuilder that = (DtoWithCanonicalBuilder) o;
    return userId == that.userId && Objects.equals(userName, that.userName);
  }

  @Override
  public int hashCode() {
    int result = Long.hashCode(userId);
    result = 31 * result + Objects.hashCode(userName);
    return result;
  }


  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private long userId;
    private String userName;

    protected Builder() {}

    public DtoWithCanonicalBuilder build() {
      return new DtoWithCanonicalBuilder(this);
    }

    public Builder userId(long userId) {
      this.userId = userId;
      return this;
    }

    public Builder userName(String userName) {
      this.userName = userName;
      return this;
    }

  }

}
