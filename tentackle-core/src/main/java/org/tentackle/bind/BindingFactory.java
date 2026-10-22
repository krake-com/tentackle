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



/**
 * The binding factory.
 * <p>
 * Used to create {@link BindingMember}s, {@link Binding}s and {@link Binder}s.
 *
 * @author harald
 */
public interface BindingFactory {

  /**
   * Creates a binding member.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @param parentClass the class declaring the member
   * @param parentMember the parent member, null if root
   * @param memberName the name of the member within the declaring class
   * @param memberPath the complete member path (binding path)
   * @param element the bound element
   * @return the binding member
   */
  BindingMember createBindingMember(Class<?> parentClass, BindingMember parentMember,
                                    String memberName, String memberPath,
                                    BindableElement element);


  /**
   * Gets the cache for bindable elements.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @return the cache
   */
  BindableCache getBindableCache();


  /**
   * Creates a bindable element.
   * <p>
   * Notice: this method must be implemented by the concrete factory.
   *
   * @param modelClass the model class
   * @param camelName the camelcase name of the bindable
   * @return the bindable element
   */
  BindableElement createBindableElement(Class<?> modelClass, String camelName);


  /**
   * Defines the bindable element class for a model class.<br>
   * The element class applies to subclasses of the model class as well.
   * It must provide a public constructor with the camelcase name as its only argument.
   *
   * @param modelClass the model class
   * @param elementClass the element class
   * @return the old element class if replaced, else null
   */
  Class<? extends BindableElement> putBindableElementClass(Class<?> modelClass,
                                                           Class<? extends BindableElement> elementClass);

  /**
   * Gets the bindable element class for a given model class.
   *
   * @param modelClass the model class
   * @return the bindable element class, null if no specific one, i.e. use a default bindable
   */
  Class<? extends BindableElement> getBindableElementClass(final Class<?> modelClass);

}
