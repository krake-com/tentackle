/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.swing.bind;

import org.tentackle.bind.AbstractBinding;
import org.tentackle.bind.Binder;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.reflect.ReflectionHelper;

/**
 * Base class for Swing bindings.
 *
 * @author harald
 */
public abstract class AbstractFormBinding extends AbstractBinding {

  /**
   * Creates a binding.
   *
   * @param binder the binder managing this binding
   * @param parents the members building the declaration chain to this member, null if this binding's member is in container
   * @param member the member field to bind
   */
  public AbstractFormBinding(Binder binder, BindingMember[] parents, BindingMember member) {
    super(binder, parents, member);
  }


  /**
   * Verifies that the type of the bean matches the type of the bound member.
   * <p>
   * The verification only detects obvious type mismatches.
   * <p>
   * For example, if the member type is a {@link Double} and the component's type
   * is an {@link Integer}, there is no way to cast in either direction.
   * <p>
   * However, if a generic component deals with a supertype (let's say a <tt>PDO</tt>),
   * the member type may be more specific, for example a <tt>Customer</tt>.
   * In such cases, we cannot tell whether the component really returns a <tt>Customer</tt>.
   * All we know is that it accepts a <tt>Customer</tt> for display but returns
   * <em>some</em> <tt>PDO</tt> to the member.
   * <p>
   * As a consequence, the verification succeeds if the member type is assignable
   * to the component's type <em>or</em> vice versa.
   *
   * @throws BindingException if verification fails
   */
  protected void verifyType() {
    try {
      Class<?> fieldType = getMember().getType();
      if (fieldType.isPrimitive()) {
        fieldType = ReflectionHelper.primitiveToWrapperClass(fieldType);
      }
      Class<?> componentType = getViewType();
      if (!fieldType.isAssignableFrom(componentType) &&
          !componentType.isAssignableFrom(fieldType)) {
        throw new BindingException("type '" + componentType.getName() + "' of '" + viewComponentToString() +
                                   "' does not match type " + fieldType.getName() + " of " + getMember());
      }
    }
    catch (Exception ex) {
      throw new BindingException("couldn't verify type for " + getMember(), ex);
    }
  }

}
