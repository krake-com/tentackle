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
 *
 */

package org.tentackle.model;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;

interface ModelUtilities$Singleton {
  ModelUtilities INSTANCE = ServiceFactory.createService(ModelUtilities.class, ModelUtilities.class);
}


/**
 * Model utility methods.
 */
@Service(ModelUtilities.class)    // defaults to self
public class ModelUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static ModelUtilities getInstance() {
    return ModelUtilities$Singleton.INSTANCE;
  }


  /**
   * Creates the column postfix for a given index.<br>
   * For datatypes mapping to more than one column, the column names get a postfix.
   *
   * @param javaType the java type
   * @param index the index starting at 0
   * @return the postfix, never null
   */
  public String createColumnPostfix(String javaType, int index) {
    /**
     * The first index 0 gets no postfix. The others start at "_2" (for second column),
     * "_3" and so forth.<br>
     * The postfix currently does not depend on the javatype
     */
    StringBuilder postfix = new StringBuilder();
    if (index > 0) {
      postfix.append('_').append(index + 1);
    }
    return postfix.toString();
  }

}
