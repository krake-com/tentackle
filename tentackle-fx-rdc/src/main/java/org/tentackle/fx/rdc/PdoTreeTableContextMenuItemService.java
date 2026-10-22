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

import org.tentackle.common.MappedService;
import org.tentackle.pdo.PersistentDomainObject;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation to express that the annotated class is a {@link PdoTreeTableContextMenuItem}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@MappedService(PdoTreeTableContextMenuItem.class)
public @interface PdoTreeTableContextMenuItemService {

  /**
   * Determines the serviced PDO class.<br>
   * The PDO class usually is an interface extending the persistent
   * and the domain interfaces.
   * <p>
   * The class may also be a super interface. For example,
   * {@code PersistentDomainObject} will apply to all PDOs.
   *
   * @return the PDO class
   */
  @SuppressWarnings("rawtypes")
  Class<? extends PersistentDomainObject> value();

  /**
   * The ordinal to sort the menu items within the context menu.<br>
   * Lower values come first.
   * <p>
   * If the same ordinal is defined more than once, the menu item closest
   * in the PDO inheritance tree will override all others.
   *
   * @return the ordinal
   */
  int ordinal();

  /**
   * Defines the group the item belongs to.<br>
   * Separators will be inserted between groups.
   *
   * @return the group, empty for default group
   */
  String group() default "";

}

