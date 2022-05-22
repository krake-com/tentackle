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


package org.tentackle.fx.rdc;

import org.tentackle.common.Analyze;
import org.tentackle.common.MappedService;
import org.tentackle.pdo.PersistentDomainObject;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;



/**
 * Annotation to express that the annotated class
 * is a {@link GuiProvider} for PDO.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Analyze("org.tentackle.buildsupport.BundleAnalyzeHandler")
@MappedService(GuiProvider.class)
public @interface GuiProviderService {

  /**
   * Determines the serviced PDO class.<br>
   * The PDO-class usually is an interface extending the persistent
   * and the domain interfaces.
   *
   * @return the PDO class
   */
  @SuppressWarnings("rawtypes")
  Class<? extends PersistentDomainObject> value();

  /**
   * Flag to disable entry in META-INF/bundles.<br>
   * Avoids warnings in I18N maven goals.
   *
   * @return true if GuiProvider does not provide a bundle
   */
  boolean noBundle() default false;

  /**
   * Returns whether provider should be unit tested.
   *
   * @return true if test (default), false if no test
   */
  boolean test() default true;

}

