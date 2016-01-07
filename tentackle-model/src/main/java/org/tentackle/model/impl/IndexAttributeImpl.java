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
 */

package org.tentackle.model.impl;

import org.tentackle.model.Attribute;
import org.tentackle.model.Index;
import org.tentackle.model.IndexAttribute;
import org.tentackle.model.SourceInfo;

/**
 * Index part.
 *
 * @author harald
 */
public class IndexAttributeImpl implements IndexAttribute {

  private final Index index;
  private final SourceInfo sourceInfo;

  private Attribute attribute;


  private boolean descending;


  /**
   * Creates an index attribute.
   * @param index the index
   * @param sourceInfo the source
   */
  public IndexAttributeImpl(Index index, SourceInfo sourceInfo) {
    this.index = index;
    this.sourceInfo = sourceInfo;
  }


  @Override
  public Attribute getAttribute() {
    return attribute;
  }

  @Override
  public boolean isDescending() {
    return descending;
  }


  public void setAttribute(Attribute attribute) {
    this.attribute = attribute;
  }

  public void setDescending(boolean descending) {
    this.descending = descending;
  }

}
