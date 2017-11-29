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

package org.tentackle.fx.rdc.table;

import java.net.URL;
import java.util.ResourceBundle;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.FxControllerService;

/**
 * Table printing dialog.
 * <p>
 * View contains a combobox of available printers.
 * The default printer is selected initially.
 * A button aside the combobox opens the system printer dialog in case some special
 * settings should be made.
 * The orientation and papersize is shown as well.
 * Any change leads to a new PrinterJob which holds the printer and its job settings.
 * Below the printer settings is a preview area showing the first page of the printout according
 * to the settings.
 * Below the preview are 2 buttons: cancel and print.
 *
 * @author harald
 */
@FxControllerService
public class TablePrinter extends AbstractFxController {

  private ResourceBundle resources;   // I18N resources

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    this.resources = resources;
  }

}
