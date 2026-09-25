/*
 * Tentackle - https://tentackle.org.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Persistent fields and methods are annotated with this annotation.
 * <p>
 * Allows examining the model by reflection from within the persistence layer.
 *
 * @author harald
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface Persistent {

  /**
   * Optional comment.
   *
   * @return the comment
   */
  String comment() default "";

  /**
   * Returns whether this is a component of the PDO.<br>
   * This is true for all members/attributes and component relations.
   * It is false for all non-component relations.
   *
   * @return true if this is a component, false if a non-component relation
   */
  boolean component() default true;

  /**
   * Returns whether this is the relation to the parent PDO.<br>
   * The parent PDO provides a composite relation to this PDO.
   *
   * @return true if this is a component and the annotated relation points to the component's parent.
   */
  boolean parent() default false;

  /**
   * Returns whether this is an embedded PDO.
   *
   * @return true if this is an embedded PDO
   */
  boolean embedded() default false;

  /**
   * Gets the ordinal according to the model.<br>
   * Used to order the attributes and relations when analyzed via reflection.
   *
   * @return the ordinal, 0 if no ordinal available
   */
  int ordinal() default 0;

}
