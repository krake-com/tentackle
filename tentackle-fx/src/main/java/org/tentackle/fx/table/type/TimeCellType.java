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

package org.tentackle.fx.table.type;

import java.sql.Time;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.tentackle.fx.table.FxTableCell;
import org.tentackle.fx.table.TableCellTypeService;
import org.tentackle.misc.FormatHelper;

/**
 * Time cell type.
 *
 * @author harald
 */
@TableCellTypeService(Time.class)
public class TimeCellType extends UtilDateCellType<Time> {

  @Override
  public DateFormat getFormat(FxTableCell<?, Time> tableCell) {
    DateFormat fmt = super.getFormat(tableCell);
    if (fmt == null) {
      fmt = new SimpleDateFormat(FormatHelper.getTimePattern());
    }
    return fmt;
  }

}
