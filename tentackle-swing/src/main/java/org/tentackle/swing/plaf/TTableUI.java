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


package org.tentackle.swing.plaf;

import org.tentackle.swing.AbstractFormTableModel;
import org.tentackle.swing.FormTable;
import org.tentackle.swing.FormTableEntry;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.plaf.basic.BasicTableUI;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * TableUI extended for handling dynamically merged cells.
 * Installs its own transferhandler.
 * 
 * @author harald
 * @see TTableTransferHandler
 */
public class TTableUI extends BasicTableUI {
  
  private static final TransferHandler defaultTransferHandler = new TTableTransferHandler();
  
  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    if (c instanceof FormTable) {
      ((FormTable)c).setTransferHandler(defaultTransferHandler);
    }
  }
    
  
  // with tentackle extension
  @Override
  public void paint(Graphics g, JComponent c) {
    Rectangle clip = g.getClipBounds();

    Rectangle bounds = table.getBounds();
    // account for the fact that the graphics has already been translated
    // into the table's bounds
    bounds.x = bounds.y = 0;

    if (table.getRowCount() <= 0 || table.getColumnCount() <= 0 ||
        // this check prevents us from painting the entire table
        // when the clip doesn't intersect our bounds at all
        !bounds.intersects(clip)) {

      return;
    }

    Point upperLeft = clip.getLocation();
    Point lowerRight = new Point(clip.x + clip.width - 1, clip.y + clip.height - 1);
    int rMin = table.rowAtPoint(upperLeft);
    int rMax = table.rowAtPoint(lowerRight);
    // This should never happen (as long as our bounds intersect the clip,
    // which is why we bail above if that is the case).
    if (rMin == -1) {
      rMin = 0;
    }
    // If the table does not have enough rows to fill the view we'll get -1.
    // (We could also get -1 if our bounds don't intersect the clip,
    // which is why we bail above if that is the case).
    // Replace this with the index of the last row.
    if (rMax == -1) {
      rMax = table.getRowCount()-1;
    }

    boolean ltr = table.getComponentOrientation().isLeftToRight();
    int cMin = table.columnAtPoint(ltr ? upperLeft : lowerRight); 
    int cMax = table.columnAtPoint(ltr ? lowerRight : upperLeft);        
    // This should never happen.
    if (cMin == -1) {
      cMin = 0;
    }
    // If the table does not have enough columns to fill the view we'll get -1.
    // Replace this with the index of the last column.
    if (cMax == -1) {
      cMax = table.getColumnCount()-1;
    }

    // Paint the grid.
    paintGrid(g, rMin, rMax, cMin, cMax);

    // Paint the cells.
    paintCells(g, rMin, rMax, cMin, cMax);
  }



  /**
   * to speed up (table does not change for this TableUI)
   */

  private boolean tableChecked  = false;
  private boolean cellRectFixed = true;

  private boolean isCellRectFixed() {
    if (tableChecked == false)  {
      if (table instanceof FormTable) {
        cellRectFixed = ((FormTable)table).isCellRectFixed();
      }
      tableChecked = true;
    }
    return cellRectFixed;
  }

  

  // with tentackle extension
  private void paintGrid(Graphics g, int rMin, int rMax, int cMin, int cMax) {

    g.setColor(table.getGridColor());

    Rectangle minCell = table.getCellRect(rMin, cMin, true);
    Rectangle maxCell = table.getCellRect(rMax, cMax, true);
    Rectangle damagedArea = minCell.union( maxCell );

    if (isCellRectFixed())  {
      // original code from 1.5.0_05:
      if (table.getShowHorizontalLines()) {
        int tableWidth = damagedArea.x + damagedArea.width;
        int y = damagedArea.y;
        for (int row = rMin; row <= rMax; row++) {
          y += table.getRowHeight(row);
          g.drawLine(damagedArea.x, y - 1, tableWidth - 1, y - 1);
        }
      }
      if (table.getShowVerticalLines()) {
        TableColumnModel cm = table.getColumnModel();
        int tableHeight = damagedArea.y + damagedArea.height;
        int x;
        if (table.getComponentOrientation().isLeftToRight()) {
          x = damagedArea.x;
          for (int column = cMin; column <= cMax; column++) {
            int w = cm.getColumn(column).getWidth();
            x += w;
            g.drawLine(x - 1, 0, x - 1, tableHeight - 1);
          }
        } 
        else {
          x = damagedArea.x + damagedArea.width;
          for (int column = cMin; column < cMax; column++) {
            int w = cm.getColumn(column).getWidth();
            x -= w;
            g.drawLine(x - 1, 0, x - 1, tableHeight - 1);
          }
          x -= cm.getColumn(cMax).getWidth();
          g.drawLine(x, 0, x, tableHeight - 1);
        }
      }
    }
    else  {
      // Tentacle extension
      if (table.getShowHorizontalLines()) {
        int tableWidth = damagedArea.x + damagedArea.width;
        int y = damagedArea.y;
        FormTableEntry entry;
        int rowHeight;
        AbstractFormTableModel model = (AbstractFormTableModel)((FormTable)table).getModel();
        for (int row = rMin; row <= rMax; row++) {
          entry = model.getEntryAt(row);
          rowHeight = table.getRowHeight(row);
          if (table.getShowVerticalLines()) {
            TableColumnModel cm = table.getColumnModel();
            TableColumn tc;
            int x;
            if (table.getComponentOrientation().isLeftToRight()) {
              x = damagedArea.x;
              for (int column = cMin; column <= cMax; column++) {
                tc = cm.getColumn(column);
                int w = tc.getWidth();
                x += w;
                if (entry.getShowVerticalLine(row, column))  {
                  g.drawLine(x - 1, y, x - 1, y + rowHeight - 1);
                }
              }
            } 
            else {
              x = damagedArea.x + damagedArea.width;
              for (int column = cMin; column < cMax; column++) {
                tc = cm.getColumn(column);
                int w = tc.getWidth();
                x -= w;
                if (entry.getShowVerticalLine(row, column))  {
                  g.drawLine(x - 1, y, x - 1, y + rowHeight - 1);
                }
              }
              tc = cm.getColumn(cMax);
              x -= tc.getWidth();
              g.drawLine(x, y, x, y + rowHeight - 1);
            }
          }
          y += rowHeight;
          if (entry.getShowHorizontalLine(row))  {
            g.drawLine(damagedArea.x, y - 1, tableWidth - 1, y - 1);
          }
        }
      }
    }
  }


  // unchanged
  private int viewIndexForColumn(TableColumn aColumn) {
      TableColumnModel cm = table.getColumnModel();
      for (int column = 0; column < cm.getColumnCount(); column++) {
          if (cm.getColumn(column) == aColumn) {
              return column;
          }
      }
      return -1;
  }


  // with tentackle extension
  private void paintCells(Graphics g, int rMin, int rMax, int cMin, int cMax) {
    JTableHeader header = table.getTableHeader();
    TableColumn draggedColumn = (header == null) ? null : header.getDraggedColumn();

    TableColumnModel cm = table.getColumnModel();
    int columnMargin = cm.getColumnMargin();

    Rectangle cellRect;
    TableColumn aColumn;
    int columnWidth;
    if (table.getComponentOrientation().isLeftToRight()) {
      // tentackle extension
      if (isCellRectFixed())  {
        // original code
        for(int row = rMin; row <= rMax; row++) {
          cellRect = table.getCellRect(row, cMin, false);
          for(int column = cMin; column <= cMax; column++) {
            aColumn = cm.getColumn(column);
            columnWidth = aColumn.getWidth();
            cellRect.width = columnWidth - columnMargin;
            if (aColumn != draggedColumn) {
              paintCell(g, cellRect, row, column);
            }
            cellRect.x += columnWidth;
          }
        }
      }
      else  {
        // determine cellrect for every cell
        for(int row = rMin; row <= rMax; row++) {
          boolean isRowSelected = table.getRowSelectionAllowed() && table.isRowSelected(row);
          FormTableEntry entry = ((AbstractFormTableModel)((FormTable)table).getModel()).getEntryAt(row);
          for(int column = cMin; column <= cMax; column++) {
            boolean isColumnSelected = table.getColumnSelectionAllowed() && table.isColumnSelected(column);
            aColumn = cm.getColumn(column);
            if (aColumn != draggedColumn) {
              if (entry.isCellVisible(row, column)) {
                paintCell(g, table.getCellRect(row, column, false), row, column);
              }
              else if ((isRowSelected || isColumnSelected) &&
                       entry.getReferencedColumn(row, column) == column &&  // and not mapped, i.e. not a merged cell
                       entry.getReferencedRow(row, column) == row) {
                Color col = g.getColor();
                g.setColor(table.getSelectionBackground());
                Rectangle cr = table.getCellRect(row, column, true);
                g.fillRect(cr.x, cr.y, cr.width, cr.height);
                g.setColor(col);
              }
            }
          }
        }            
      }
    } 
    else {
      for(int row = rMin; row <= rMax; row++) {
        boolean isRowSelected = table.getRowSelectionAllowed() && table.isRowSelected(row);
        FormTableEntry entry = isCellRectFixed() ? 
                               null : ((AbstractFormTableModel)((FormTable)table).getModel()).getEntryAt(row);
        cellRect = table.getCellRect(row, cMin, false);
        aColumn = cm.getColumn(cMin);
        boolean isColumnSelected = table.getColumnSelectionAllowed() && table.isColumnSelected(cMin);
        if (aColumn != draggedColumn) {
          columnWidth = aColumn.getWidth();
          cellRect.width = columnWidth - columnMargin;
          if (entry == null || entry.isCellVisible(row, cMin))  {
            paintCell(g, cellRect, row, cMin);
          }
          else if ((isRowSelected || isColumnSelected) &&
                   entry.getReferencedColumn(row, cMin) == cMin &&  // and not mapped, i.e. not a merged cell
                   entry.getReferencedRow(row, cMin) == row) {
            Color col = g.getColor();
            g.setColor(table.getSelectionBackground());
            Rectangle cr = table.getCellRect(row, cMin, true);
            g.fillRect(cr.x, cr.y, cr.width, cr.height);
            g.setColor(col);
          }
        }
        if (isCellRectFixed())  {
          // original code
          for(int column = cMin+1; column <= cMax; column++) {
            aColumn = cm.getColumn(column);
            columnWidth = aColumn.getWidth();
            cellRect.width = columnWidth - columnMargin;
            cellRect.x -= columnWidth;
            if (aColumn != draggedColumn) {
              paintCell(g, cellRect, row, column);
            }
          }
        }
        else  {
          // determine cellrect for every cell
          for(int column = cMin+1; column <= cMax; column++) {
            isColumnSelected = table.getColumnSelectionAllowed() && table.isColumnSelected(column);
            aColumn = cm.getColumn(column);
            if (aColumn != draggedColumn) {
              if (entry.isCellVisible(row, column)) {
                paintCell(g, table.getCellRect(row, column, false), row, column);
              }
              else if ((isRowSelected || isColumnSelected) &&
                       entry.getReferencedColumn(row, column) == column &&  // and not mapped, i.e. not a merged cell
                       entry.getReferencedRow(row, column) == row) {
                Color col = g.getColor();
                g.setColor(table.getSelectionBackground());
                Rectangle cr = table.getCellRect(row, column, true);
                g.fillRect(cr.x, cr.y, cr.width, cr.height);
                g.setColor(col);
              }
            }
          }                  
        }
      }
    }

    // Paint the dragged column if we are dragging.
    if (draggedColumn != null) {
      paintDraggedArea(g, rMin, rMax, draggedColumn, header.getDraggedDistance());
    }

    // Remove any renderers that may be left in the rendererPane.
    rendererPane.removeAll();
  }



  // unchanged
  private void paintDraggedArea(Graphics g, int rMin, int rMax, TableColumn draggedColumn, int distance) {
      int draggedColumnIndex = viewIndexForColumn(draggedColumn);

      Rectangle minCell = table.getCellRect(rMin, draggedColumnIndex, true);
      Rectangle maxCell = table.getCellRect(rMax, draggedColumnIndex, true);

      Rectangle vacatedColumnRect = minCell.union(maxCell);

      // Paint a gray well in place of the moving column.
      g.setColor(table.getParent().getBackground());
      g.fillRect(vacatedColumnRect.x, vacatedColumnRect.y,
                 vacatedColumnRect.width, vacatedColumnRect.height);

      // Move to the where the cell has been dragged.
      vacatedColumnRect.x += distance;

      // Fill the background.
      g.setColor(table.getBackground());
      g.fillRect(vacatedColumnRect.x, vacatedColumnRect.y,
                 vacatedColumnRect.width, vacatedColumnRect.height);

      // Paint the vertical grid lines if necessary.
      if (table.getShowVerticalLines()) {
          g.setColor(table.getGridColor());
          int x1 = vacatedColumnRect.x;
          int y1 = vacatedColumnRect.y;
          int x2 = x1 + vacatedColumnRect.width - 1;
          int y2 = y1 + vacatedColumnRect.height - 1;
          // Left
          g.drawLine(x1-1, y1, x1-1, y2);
          // Right
          g.drawLine(x2, y1, x2, y2);
      }

      for(int row = rMin; row <= rMax; row++) {
          // Render the cell value
          Rectangle r = table.getCellRect(row, draggedColumnIndex, false);
          r.x += distance;
          paintCell(g, r, row, draggedColumnIndex);

          // Paint the (lower) horizontal grid line if necessary.
          if (table.getShowHorizontalLines()) {
              g.setColor(table.getGridColor());
              Rectangle rcr = table.getCellRect(row, draggedColumnIndex, true);
              rcr.x += distance;
              int x1 = rcr.x;
              int y1 = rcr.y;
              int x2 = x1 + rcr.width - 1;
              int y2 = y1 + rcr.height - 1;
              g.drawLine(x1, y2, x2, y2);
          }
      }
  }

  // unchanged
  private void paintCell(Graphics g, Rectangle cellRect, int row, int column) {
      if (table.isEditing() && table.getEditingRow()==row &&
                               table.getEditingColumn()==column) {
          Component component = table.getEditorComponent();
          component.setBounds(cellRect);
          component.validate();
      }
      else {
          TableCellRenderer renderer = table.getCellRenderer(row, column);
          Component component = table.prepareRenderer(renderer, row, column);
          rendererPane.paintComponent(g, component, table, cellRect.x, cellRect.y,
                                      cellRect.width, cellRect.height, true);
      }
  }

}
