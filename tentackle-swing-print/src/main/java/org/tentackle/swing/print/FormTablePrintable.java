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


package org.tentackle.swing.print;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.Date;
import javax.swing.border.LineBorder;
import org.tentackle.swing.FormTable;
import org.tentackle.swing.FormUtilities;
import org.tentackle.swing.FormWindow;
import org.tentackle.misc.FormatHelper;



/**
 * Creates a {@link Printable} out of a {@link FormTable}.
 *
 * @author harald
 */
public class FormTablePrintable implements Printable {

  private FormTable  table;
  private String     title;
  private String     intro;
  private Date       printDate;



  /**
   * Creates a FormTablePrintable.
   *
   * @param table the form table
   * @param title the title to be printed on each page
   * @param intro the intro to be printed on the first page
   */
  public FormTablePrintable(FormTable table, String title, String intro) {
    this.table = table;
    this.title = title;
    this.intro = intro;
  }

  /**
   * Creates a FormTablePrintable without title and intro.
   *
   * @param table the form table
   */
  public FormTablePrintable(FormTable table)  {
    this(table, null, null);
  }



  /**
   * Prints the table.<br>
   * Asks the user for the printing dialog and prints the table.
   */
  public void doPrint() {
    if (title == null)  {
      // get default from window title
      Window parent = FormUtilities.getInstance().getParentWindow(table);
      if (parent instanceof FormWindow) {
        // paint page-title
        title = ((FormWindow)parent).getTitle();
      }
    }
    printDate = new Date();
    // print with jobname being the title
    PrintHelper.print(this, title);
  }



  @Override
  public int print(Graphics g, PageFormat pageFormat, int pageIndex)
         throws PrinterException {
    return processPrinting(g, pageFormat, pageIndex, false);
  }





  /**
   * workhorse to either print or compute the number of pages
   */
  private int processPrinting (Graphics g, PageFormat pageFormat, int pageIndex, boolean countOnly)
          throws PrinterException {

    /**
     * the format of the layout is:
     * - page title (default is derived from the window-title, but can be set explicitly)
     * - optional page intro (e.g. to describe selection criteria. None is default)
     *   the intro is only printed on the first page after the title.
     * - table-header
     * - table-data (clipped to fit on page)
     * - page footer (prints the page-number)
     *
     * Because rows may be of different height, we must step through all
     * rows and sum-up the height.
     */

    Graphics2D g2  = (Graphics2D)g;

    g2.setFont(g2.getFont().deriveFont(Font.BOLD));
    FontMetrics fm = g2.getFontMetrics();

    g2.setColor(Color.black);
    int fontHeight  = fm.getHeight();
    int fontDescent = fm.getDescent();

    int tableWidth  = table.getWidth();

    double pageHeight = pageFormat.getImageableHeight();
    double pageWidth  = pageFormat.getImageableWidth();

    // scale table to fit in width on page
    double scale = tableWidth > pageWidth ? pageWidth / tableWidth : 1.0;

    // compute size of intro, if any
    double introHeight = 0.0;
    PrintTextArea introField = null;
    if (intro != null) {
      introHeight = (fontHeight + fontDescent) * scale * 3;
      if (pageIndex == 0) {
        introField = new PrintTextArea();
        introField.setBorder(new LineBorder(Color.GRAY));
        introField.setWrapStyleWord(true);
        introField.setLineWrap(true);
        introField.setSize(new Dimension((int)(pageWidth / scale), (int)(introHeight)));
      }
    }

    // leave one line between table and title/footer
    double pageTitleHeight  = title == null ? 0.0 : fontHeight * scale * 2.0;
    double pageHeaderHeight = table.getTableHeader().getHeight() * scale;
    double pageFootHeight   = fontHeight * scale * 2.0;

    // size of the table-data per page (all except first page with intro)
    double pageTableHeight        = pageHeight - pageTitleHeight - pageHeaderHeight - pageFootHeight;
    double currentPageTableHeight = pageTableHeight - introHeight;  // start value for first page

    int rows            = table.getRowCount();
    int pageNo          = 0;            // current/max pagecount
    double dataSkip     = 0.0;          // number of scaled pixels to transform
    double dataHeight   = 0.0;          // height of clipping area
    double pageSkip     = 0.0;          // skip to current page
    double yInTable     = 0.0;          // current y-Position of next row relative to table-data region

    for (int row=0; row < rows; row++)  {
      double rowHeight = table.getRowHeight(row) * scale;
      if (yInTable - pageSkip + rowHeight > currentPageTableHeight)  {
        // skip to next page
        if (pageNo == pageIndex)  {
          // this is the end of the requested page
          dataHeight = yInTable - dataSkip;
        }
        pageNo++;
        currentPageTableHeight = pageTableHeight;   // after first page
        pageSkip = yInTable;
        if (pageNo == pageIndex)  {
          // this is the start of the requested page!
          dataSkip = pageSkip;
        }
      }
      yInTable += rowHeight;
    }

    if (pageNo == pageIndex)  {
      dataHeight = yInTable - dataSkip;
    }

    /**
     * hier gibt es ein Problem:
     * durch das Rendern (paint) ändern sich die Zeilenhöhen.
     * Swing löst das so, dass die Seite 2 x gedruckt wird.
     * Das geht aber nur bis zur aktuellen Seite, d.h. alle nachfolgenden
     * noch nicht gedruckten haben die falsche rowHeight.
     * Deshalb lassen wir erstmal die Anzahl Seiten weg :-((
     */

    pageNo++;   // count last page

    if (countOnly) {
      return pageNo;      // don't print: simply return number of pages
    }

    if (pageIndex >= pageNo) {
      return Printable.NO_SUCH_PAGE;
    }


    // all done, go on printing!
    g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
    g2.scale(scale, scale);

    // print title centered, if any
    if (title != null)  {
      int left = (int)(pageWidth/2.0/scale) - fm.stringWidth(title)/2;
      if (left < 0) {
        left = 0;
      }
      g2.drawString(title, left, fontHeight - fontDescent);
    }
    g2.translate(0.0, pageTitleHeight/scale);

    // print intro, if any
    if (introField != null)  {
      introField.setPrintValue(intro);
      introField.paint(g2);
      g2.translate(0.0, introHeight/scale);
    }

    // print table header
    table.getTableHeader().paint(g2);

    // print table data
    g2.scale(1.0/scale, 1.0/scale);
    g2.translate(0.0, pageHeaderHeight - dataSkip);
    g2.setClip(0, (int) Math.ceil(dataSkip),
               (int) Math.ceil(pageWidth), (int) Math.ceil(dataHeight));
    g2.scale(scale, scale);
    table.paint(g2);

    // print footer
    g2.scale(1.0/scale, 1.0/scale);
    g2.translate(0.0, dataSkip + pageTableHeight - (pageIndex == 0 ? introHeight : 0) + fontHeight);
    g2.setClip(0, 0, (int)Math.ceil(pageWidth), (int)Math.ceil(pageHeight));
    g2.scale(scale, scale);
    g2.drawString(FormatHelper.formatShortDate(printDate), 0, fontHeight - fontDescent);
    String footer = "- " + (pageIndex+1) + " -";
    g2.drawString(footer,
                  tableWidth/2 - fm.stringWidth(footer)/2,
                  fontHeight - fontDescent);

    return Printable.PAGE_EXISTS;
  }



// ---------------- todo: implement Pageable --------------------------

//  /** Returns the number of pages in the set.
//   * To enable advanced printing features,
//   * it is recommended that <code>Pageable</code>
//   * implementations return the true number of pages
//   * rather than the
//   * UNKNOWN_NUMBER_OF_PAGES constant.
//   * @return the number of pages in this <code>Pageable</code>.
//   *
//   */
//  public int getNumberOfPages() {
//    return UNKNOWN_NUMBER_OF_PAGES;
//  }
//
//  /** Returns the <code>PageFormat</code> of the page specified by
//   * <code>pageIndex</code>.
//   * @param pageIndex the zero based index of the page whose
//   *            <code>PageFormat</code> is being requested
//   * @return the <code>PageFormat</code> describing the size and
//   * 		orientation.
//   * @throws IndexOutOfBoundsException if
//   *          the <code>Pageable</code> does not contain the requested
//   * 		page.
//   *
//   */
//  public PageFormat getPageFormat(int pageIndex) throws IndexOutOfBoundsException {
//    return format;
//  }
//
//  /** Returns the <code>Printable</code> instance responsible for
//   * rendering the page specified by <code>pageIndex</code>.
//   * @param pageIndex the zero based index of the page whose
//   *            <code>Printable</code> is being requested
//   * @return the <code>Printable</code> that renders the page.
//   * @throws IndexOutOfBoundsException if
//   *            the <code>Pageable</code> does not contain the requested
//   * 		  page.
//   *
//   */
//  public Printable getPrintable(int pageIndex) throws IndexOutOfBoundsException {
//    return this;
//  }

}