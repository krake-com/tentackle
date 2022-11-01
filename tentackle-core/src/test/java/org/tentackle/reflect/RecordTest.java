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

package org.tentackle.reflect;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;

/**
 * Simple test to check that records don't perform a deep equals and deep hashcode on arrays.<br>
 * Just to ensure that this doesn't change in future releases of Java.
 */
@SuppressWarnings("missing-explicit-ctor")
public class RecordTest {

  @Test
  public void recordDeepEqualsOverridden() {

    record Key(Class<?> clazz, Class<?>[] parameterTypes) {

      @Override
      public boolean equals(Object obj) {
        if (obj == null) {
          return false;
        }
        if (getClass() != obj.getClass()) {
          return false;
        }
        final Key other = (Key) obj;
        if (this.clazz != other.clazz) {
          return false;
        }
        return Arrays.equals(this.parameterTypes, other.parameterTypes);
      }

      @Override
      public int hashCode() {
        int hash = 3;
        hash = 83 * hash + (this.clazz != null ? this.clazz.hashCode() : 0);
        hash = 83 * hash + Arrays.hashCode(this.parameterTypes);
        return hash;
      }
    }

    Key key1 = new Key(String.class, new Class<?>[]{String.class, Long.class, Integer.class});
    Key key2 = new Key(String.class, new Class<?>[]{String.class, Long.class, Integer.class});
    Assert.assertEquals(key1, key2);
    Assert.assertEquals(key1.hashCode(), key2.hashCode());
  }


  @Test
  public void recordDeepEqualsNotOverridden() {

    record Key(Class<?> clazz, Class<?>[] parameterTypes) {}

    Key key1 = new Key(String.class, new Class<?>[] {String.class, Long.class, Integer.class });
    Key key2 = new Key(String.class, new Class<?>[] {String.class, Long.class, Integer.class });
    Assert.assertNotEquals(key1, key2);
    Assert.assertNotEquals(key1.hashCode(), key2.hashCode());
  }

}
