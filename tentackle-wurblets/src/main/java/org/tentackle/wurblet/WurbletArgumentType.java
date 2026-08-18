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

package org.tentackle.wurblet;

/**
 * The {@link WurbletArgument} type.
 *
 * @author harald
 */
public enum WurbletArgumentType {

  /**
   * An {@link org.tentackle.model.Attribute} that must meet a condition.
   */
  CONDITION(true, false, false, true, true, true, true),

  /**
   * An extra {@link org.tentackle.model.Attribute}.<br>
   * Usually used for SQL UPDATE.
   */
  EXTRA(false, false, false, true, true, false, false),

  /**
   * An {@link org.tentackle.model.Attribute} used for sorting.
   */
  SORT(true, false, false, true, false, false, false),

  /**
   * A {@link org.tentackle.model.Relation} that will be joined and eagerly loaded.
   */
  JOIN(false, true, false, false, false, false, false);


  private final boolean pathOptional;
  private final boolean pathRequired;
  private final boolean attributeOptional;
  private final boolean attributeRequired;
  private final boolean nameOptional;
  private final boolean relopOptional;
  private final boolean conditionOptional;


  WurbletArgumentType(boolean pathOptional, boolean pathRequired,
                      boolean attributeOptional, boolean attributeRequired,
                      boolean nameOptional, boolean relopOptional,
                      boolean conditionOptional) {

    this.pathOptional = pathOptional;
    this.pathRequired = pathRequired;
    this.attributeOptional = attributeOptional;
    this.attributeRequired = attributeRequired;
    this.nameOptional = nameOptional;
    this.relopOptional = relopOptional;
    this.conditionOptional = conditionOptional;
  }

  /**
   * Returns whether a {@link org.tentackle.model.Relation} path may be given optionally.
   *
   * @return true if optional, false if none or required
   */
  public boolean isPathOptional() {
    return pathOptional;
  }

  /**
   * Returns whether a {@link org.tentackle.model.Relation} path is required.
   *
   * @return true if required, false if not allowed or optional
   */
  public boolean isPathRequired() {
    return pathRequired;
  }

  /**
   * Returns whether a {@link org.tentackle.model.Relation} path is allowed.
   *
   * @return true if optional or required
   */
  public boolean isPathAllowed() {
    return pathOptional || pathRequired;
  }

  /**
   * Returns whether an {@link org.tentackle.model.Attribute} may be given optionally.
   *
   * @return true if optional, false if not allowed or required
   */
  public boolean isAttributeOptional() {
    return attributeOptional;
  }

  /**
   * Returns whether an {@link org.tentackle.model.Attribute} is required.
   *
   * @return true if required, false if none or optional
   */
  public boolean isAttributeRequired() {
    return attributeRequired;
  }

  /**
   * Returns whether an {@link org.tentackle.model.Attribute} is allowed.
   *
   * @return true if optional or required
   */
  public boolean isAttributeAllowed() {
    return attributeOptional || attributeRequired;
  }

  /**
   * Returns whether the name of the method argument is optional.
   *
   * @return true if optional, false if not allowed
   */
  public boolean isNameOptional() {
    return nameOptional;
  }

  /**
   * Returns whether the relop is optional.
   *
   * @return true if optional, false if not allowed
   */
  public boolean isRelopOptional() {
    return relopOptional;
  }

  /**
   * Returns whether the argument may be made optional at runtime.<br>
   * Such arguments are marked with {@code ?<boolean>} and become part of the
   * generated SQL only if the boolean method argument is true.
   *
   * @return true if the argument may be optional, false if not allowed
   */
  public boolean isConditionOptional() {
    return conditionOptional;
  }

}
