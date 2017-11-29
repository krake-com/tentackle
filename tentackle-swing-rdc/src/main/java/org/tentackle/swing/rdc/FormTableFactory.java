/**
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


package org.tentackle.swing.rdc;

import javax.swing.table.TableModel;
import org.tentackle.common.ServiceFactory;
import org.tentackle.swing.FormTable;
import org.tentackle.swing.FormTableUtilityPopup;


interface FormTableFactory$Singleton {
  FormTableFactory INSTANCE = ServiceFactory.createService(FormTableFactory.class, DefaultFormTableFactory.class);
}


/**
 * The formtable factory.<br>
 * Allows application specific replacement in RDC.
 *
 * @author harald
 */
public interface FormTableFactory {


  /**
   * The singleton.
   *
   * @return the singleton
   */
  static FormTableFactory getInstance() {
    return FormTableFactory$Singleton.INSTANCE;
  }



  /**
   * Creates a formtable.
   *
   * @return the created formtable
   */
  FormTable<?> createFormTable();

  /**
   * Creates a formtable for a given data model.
   *
   * @param model the data model
   * @return the created formtable
   */
  FormTable<?> createFormTable(TableModel model);

  /**
   * Creates a popup.
   *
   * @return the popup
   */
  FormTableUtilityPopup createPopup();

}
