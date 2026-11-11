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

package org.tentackle.trip.creators;

import org.tentackle.trip.TripInstanceCreatorService;

import java.util.regex.PatternSyntaxException;

/**
 * Instance creator for {@link PatternSyntaxException}.<br>
 * The message is composed of the description, the pattern and the index.
 *
 * @param <T> the exception type
 */
@TripInstanceCreatorService(PatternSyntaxException.class)
public class PatternSyntaxExceptionInstanceCreator<T extends PatternSyntaxException> extends AbstractThrowableInstanceCreator<T> {

  /**
   * Creates the instance creator.
   */
  public PatternSyntaxExceptionInstanceCreator() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  protected T create(Class<T> clazz, Values values) throws ReflectiveOperationException {
    return newInstance(clazz, new Class<?>[] { String.class, String.class, int.class },
                       values.get("description"), values.get("pattern"), values.getInt("index", -1));
  }

}
