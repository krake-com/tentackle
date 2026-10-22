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

package org.tentackle.bind;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a member of a class for eligibility in GUI bindings.
 * <p>
 * The class may be any kind of class (not necessarily a bean, but maybe),
 * usually a PDO or a GUI-view. The members may be regular objects
 * or primitives. Annotated objects themselves may contain annotated members.
 * In fact, only the leaves of such binding hierarchies are actually bound.
 * <p>
 * Fields, getters, setters and "from"- or "with"-methods (of immutable objects) can be annotated.
 *
 * @author harald
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface Bindable {

  /**
   * GUI Binding options.
   * <p>
   * Example: <code>@Bindable(options="UC,MAXCOLS=5")</code>
   *
   * @return the options, the empty string if none
   */
  String options() default "";

  /**
   * An optional number to sort the bindings.<br>
   * The binder processes the bindings sorted by ordinal plus the name of the binding member.
   * Useful when configurations are automatically created according to the binding,
   * for example, to control the column-order in table configurations.
   * <p>
   * Important: if the element's getter and setter are annotated with {@code @Bindable}, the ordinals must be equal!
   *
   * @return the ordinal
   */
  int ordinal() default 0;

}
