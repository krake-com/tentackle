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
 * is the {@link GuiProvider} for a PDO type.
 * <p>
 * The annotated class must provide a public constructor with the PDO as its only argument.
 * The mapping from the PDO class to the provider class is recorded under {@code META-INF}
 * at build time, and the provider's resource bundle under {@code META-INF/bundles}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Analyze("org.tentackle.buildsupport.BundleAnalyzeHandler")
@MappedService(GuiProvider.class)
public @interface GuiProviderService {

  /**
   * Determines the serviced PDO class.<br>
   * The PDO class usually is an interface extending the persistent
   * and the domain interfaces.
   *
   * @return the PDO class
   */
  @SuppressWarnings("rawtypes")
  Class<? extends PersistentDomainObject> value();

  /**
   * Flag to disable the entry in {@code META-INF/bundles}.<br>
   * Set it if the provider has no resource bundle, to avoid warnings in the I18N maven goals.
   *
   * @return true if GuiProvider does not provide a bundle
   */
  boolean noBundle() default false;

  /**
   * Returns whether the provider should be unit tested.<br>
   * Used by the generic {@code GuiProviderTest} of the {@code tentackle-test-fx-rdc} module.
   *
   * @return true if test (default), false if no test
   */
  boolean test() default true;

}
