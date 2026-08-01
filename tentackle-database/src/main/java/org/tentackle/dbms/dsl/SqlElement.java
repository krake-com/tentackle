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

package org.tentackle.dbms.dsl;

/**
 * An element of an SQL query.
 * <p>
 * Elements render themselves to SQL-code and register their parameters
 * in exactly the order of the question marks they emit.
 * Applications may implement this interface to extend the DSL by
 * constructs that are not covered yet.
 *
 * @author harald
 */
@FunctionalInterface
public interface SqlElement {

  /**
   * Renders this element into the given context.
   *
   * @param context the render context
   */
  void render(RenderContext context);

}
