/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.model;

import java.util.List;

/**
 * Global options for the whole entity.
 *
 * @author harald
 */
public interface EntityOptions extends CommonOptions {

  /**
   * Gets the entity this option-set belongs to.
   *
   * @return the entity
   */
  Entity getEntity();

  /**
   * Returns whether global model defaults should be applied or not.<br>
   *
   * @return true if ignore model defaults completely
   */
  boolean noModelDefaults();

  /**
   * Gets the tracking type.
   *
   * @return the tracking type
   */
  TrackType getTrackType();

  /**
   * true if [NOPKEY]-option set.
   *
   * @return the NoPrimary
   */
  boolean isNoPrimaryKey();

  /**
   * true if [TABLESERIAL]-option set.
   *
   * @return true if tableserial column provided
   */
  boolean isTableSerialProvided();

  /**
   * true if [EDITEDBY]-option set.
   *
   * @return truf if editedby token columns provided
   */
  boolean isTokenLockProvided();

  /**
   * true if [NORMTEXT]-option set.
   *
   * @return true if normtext column provided
   */
  boolean isNormTextProvided();

  /**
   * true if [ROOT]-option set
   *
   * @return true if this is a root entity
   */
  boolean isRootEntity();

  /**
   * true if [ROOTID]-option set.
   *
   * @return true if rootid column provided
   */
  boolean isRootIdProvided();

  /**
   * true if [ROOTCLASSID]-option set.
   *
   * @return true if rootclassid column provided
   */
  boolean isRootClassIdProvided();

  /**
   * Gets the default sorting options.
   *
   * @return the default sorting, null if none
   */
  List<String> getSorting();

  /**
   * Returns whether entity supports remoting.
   *
   * @return true if with remoting, false if only local
   */
  boolean isRemote();

}
