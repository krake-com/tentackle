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

package org.tentackle.wurblet;

import org.testng.annotations.Test;
import org.wurbelizer.wurbel.WurbelContext;
import org.wurbelizer.wurblet.Wurblet;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link WurbletThreadSafety} and the {@code isThreadSafe} overrides of the wurblet base classes.
 */
public class WurbletThreadSafetyTest {

  private static List<Wurblet> createWurblets() {
    return List.of(new ModelWurblet(), new DTOWurblet(), new IncludeWurblet());
  }

  @Test
  public void notThreadSafeWithoutWurbletClassLoader() {
    WurbelContext context = new WurbelContext();
    WurbelContext.call(context, () -> {
      for (Wurblet wurblet : createWurblets()) {
        assertFalse(wurblet.isThreadSafe(), wurblet.getClass().getName());
      }
      return null;
    });
  }

  @Test
  public void threadSafeIfLoadedByWurbletClassLoader() {
    WurbelContext context = new WurbelContext();
    context.setWurbletClassLoader(ModelWurblet.class.getClassLoader());
    WurbelContext.call(context, () -> {
      for (Wurblet wurblet : createWurblets()) {
        assertTrue(wurblet.isThreadSafe(), wurblet.getClass().getName());
      }
      return null;
    });
  }

  @Test
  public void notThreadSafeIfLoadedByOtherClassLoader() throws Exception {
    try (URLClassLoader otherLoader = new URLClassLoader(new URL[0], ModelWurblet.class.getClassLoader())) {
      WurbelContext context = new WurbelContext();
      context.setWurbletClassLoader(otherLoader);
      WurbelContext.call(context, () -> {
        for (Wurblet wurblet : createWurblets()) {
          assertFalse(wurblet.isThreadSafe(), wurblet.getClass().getName());
        }
        return null;
      });
    }
  }

}
