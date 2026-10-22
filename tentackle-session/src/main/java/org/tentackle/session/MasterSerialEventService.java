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

package org.tentackle.session;

import org.tentackle.common.MappedService;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for the handler of a {@link MasterSerialEvent}.
 * <p>
 * The annotated class must implement {@link java.util.function.Consumer} of the event type and provide
 * a public no-args constructor, which is verified at compile time by the annotation processor.
 * The handler is a stateless singleton. Since this is a {@link MappedService}, different clients
 * may provide different handlers for the same event, and the server does not need to know them at all.
 *
 * @see MasterSerialEventHandlerFactory
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@MappedService(MasterSerialEvent.class)
public @interface MasterSerialEventService {

  /**
   * Determines the serviced event class.
   *
   * @return the event class handled by the annotated class
   */
  Class<? extends MasterSerialEvent> value();

}
