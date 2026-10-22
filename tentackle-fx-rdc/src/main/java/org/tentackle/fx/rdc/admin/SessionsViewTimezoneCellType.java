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

package org.tentackle.fx.rdc.admin;

import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.FxTreeTableCell;
import org.tentackle.fx.table.type.AbstractTableCellType;
import org.tentackle.misc.FormatHelper;

import javafx.scene.control.Tooltip;
import java.time.ZonedDateTime;
import java.util.TimeZone;

/**
 * Special timezone cell type for the sessions view.<br>
 * Shows the current local time and date in the given timezone as a tooltip.
 * Supports tables only.
 */
public class SessionsViewTimezoneCellType extends AbstractTableCellType<String>  {

  /**
   * Creates a timezone cell type.
   */
  public SessionsViewTimezoneCellType() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void updateItem(FxTableCell<?, String> tableCell, String item) {
    tableCell.setText(item);
    if (tableCell.getTooltip() == null) {
      tableCell.setTooltip(createTooltip(tableCell));
    }
  }

  @Override
  public void updateItem(FxTreeTableCell<?, String> treeTableCell, String item) {
    // not used
  }


  private Tooltip createTooltip(FxTableCell<?, String> tableCell) {
    Tooltip tooltip = new Tooltip();
    tooltip.setOnShowing(_ -> {
      String tzText = tableCell.getItem();
      if (tzText != null) {
        int ndx = tzText.indexOf(" (");
        if (ndx >= 0) {
          tzText = tzText.substring(0, ndx);    // cut off the offset (see AdminExtensionAdapterRemoteDelegateImpl)
          TimeZone timeZone = TimeZone.getTimeZone(tzText);
          if (timeZone.getID().equals(tzText)) {    // if not the fallback to GMT
            tooltip.setText(FormatHelper.formatShortZonedDateTime(ZonedDateTime.now(timeZone.toZoneId())));
          }
        }
      }
    });
    return tooltip;
  }

}
