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

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.awt.print.PrinterException;

import static java.awt.print.Pageable.UNKNOWN_NUMBER_OF_PAGES;


/**
 * A generic report.
 * <p>
 * Can be used as {@link Printable} or {@link Pageable}. If used as Pageable the number of
 * pages will be determined in dummy pass first and then printed in a second pass.
 * If used as a Printable there is only one pass.
 * <p>
 * The report is implemented as a statemachine and gets its data
 * from a {@link ReportSource}.
 *
 * @author harald
 */
public class Report implements Printable, Pageable {


  // aligments for panels
  /** align top left (default) **/
  public static final int NORTHWEST = GridBagConstraints.NORTHWEST;
  /** align top right **/
  public static final int NORTHEAST = GridBagConstraints.NORTHEAST;
  /** align bottom left **/
  public static final int SOUTHWEST = GridBagConstraints.SOUTHWEST;
  /** align bottom right **/
  public static final int SOUTHEAST = GridBagConstraints.SOUTHEAST;
  /** center panel **/
  public static final int CENTER    = GridBagConstraints.CENTER;
  /** align right **/
  public static final int EAST      = GridBagConstraints.EAST;
  /** align left **/
  public static final int WEST      = GridBagConstraints.WEST;
  /** align bottom **/
  public static final int SOUTH     = GridBagConstraints.SOUTH;
  /** align top **/
  public static final int NORTH     = GridBagConstraints.NORTH;


  // for scaling to fit on page
  /** fixed scaling (default = 1.0) **/
  public static final int SCALE_FIXED    = 0;
  /** scale (make smaller) to fit if wider than paper **/
  public static final int SCALE_IF_WIDER = 1;
  /** scale to paper-width (enlarge or shrink) **/
  public static final int SCALE_TO_FIT   = 2;





  private final ReportSource source;       // data source for the report
  private final PageFormat pageFormat;     // Seitenformat. (cannot change between pages)
  private final int        subLevels;      // number of logical sub-levels, 0 = default
  private final boolean    imageable;      // true if print on imageable area, else whole paper

  private int     autoScale = SCALE_FIXED; // scaling mode
  private double  scale = 1.0;             // current scaling (always same for x/y)

  private PrintPanel   intro;              // printed once at start of report, null = none
  private PrintPanel   trailer;            // printed once at end of report, null = none
  private PrintPanel   header;             // header on each page, except first
  private PrintPanel   footer;             // footer on each page, except last
  private PrintPanel   line;               // laufende Druckzeile
  private PrintPanel[] subHeader;          // array of sub-headers, one for each logical level
  private PrintPanel[] subFooter;          // array of sub-footers, one for each logical level

  // height of each PrintPanel
  private int   introHeight;               // height of intro, 0 = none
  private int   trailerHeight;             // height of trailer, 0 = none
  private int   headerHeight;              // height of header, 0 = none
  private int   footerHeight;              // height of footer, 0 = none
  private int   lineHeight;                // height of running line
  private int[] subHeaderHeight;           // heights of sub-headers
  private int[] subFooterHeight;           // heights of sub-footers

  // width of each PrintPanel
  private int   introWidth;                // width of intro, 0 = none
  private int   trailerWidth;              // width of trailer, 0 = none
  private int   headerWidth;               // width of header, 0 = none
  private int   footerWidth;               // width of footer, 0 = none
  private int   lineWidth;                 // width of running line
  private int[] subHeaderWidth;            // widths of sub-headers
  private int[] subFooterWidth;            // widths of sub-footers

  // alignment of each PrintPanel
  private int   introAlignment   = NORTHWEST; // alignment of intro, 0 = none
  private int   trailerAlignment = NORTHWEST; // alignment of trailer, 0 = none
  private int   headerAlignment  = NORTHWEST; // alignment of header, 0 = none
  private int   footerAlignment  = NORTHWEST; // alignment of footer, 0 = none
  private int   lineAlignment    = NORTHWEST; // alignment of running line
  private int[] subHeaderAlignment;           // alignments of sub-headers
  private int[] subFooterAlignment;           // alignments of sub-footers

  private boolean initDone;           // init ran
  private int     pages;              // number of pages
  private int     yOffset;            // current position in page
  private int     yMax;               // maximum y pos on paper so far

  private State state;                // current state
  private State savedState;           // saved state




  /**
   * Creates a report.
   *
   * @param source the data source for this report
   * @param pageFormat the pageformat
   * @param imageable true if honor the imageable size, else full page size
   * @param subLevels number of sublevels, default is 0 (no groups)
   */
  public Report(ReportSource source, PageFormat pageFormat, boolean imageable, int subLevels) {

    this.source        = source;
    this.pageFormat    = pageFormat;
    this.imageable     = imageable;
    this.subLevels     = subLevels;

    if (subLevels > 0)  {
      subHeader = new PrintPanel[subLevels];
      subFooter = new PrintPanel[subLevels];
      subHeaderHeight = new int[subLevels];
      subFooterHeight = new int[subLevels];
      subHeaderWidth = new int[subLevels];
      subFooterWidth = new int[subLevels];
      subHeaderAlignment = new int[subLevels];
      subFooterAlignment = new int[subLevels];
      for (int i=0; i < subLevels; i++) {
        subHeaderAlignment[i] = NORTHWEST;
        subFooterAlignment[i] = NORTHWEST;
      }
    }

    initDone = false;
  }


  /**
   * Creates a standard report which honors the imageable size
   * and has no sublevels.
   *
   * @param source the data source for this report
   * @param pageFormat the pageformat
   */
  public Report(ReportSource source, PageFormat pageFormat) {
    this(source, pageFormat, true, 0);
  }



  /**
   * Sets the intro panel.
   * The intro will be printed at the beginning of the report.
   *
   * @param intro the intro panel, null for none (i.e. print header instead)
   * @throws PrinterException if failed
   */
  public void setIntro(PrintPanel intro) throws PrinterException {
    if (intro == null)  {
      introWidth  = 0;
      introHeight = 0;
    }
    else  {
      introWidth  = intro.getWidth();
      introHeight = intro.getHeight();
    }
    this.intro  = intro;
  }


  /**
   * Sets the trailer panel.
   * The trailer will be printed at the end of the report.
   *
   * @param trailer the trailer panel, null for none (i.e. print footer instead)
   * @throws PrinterException if failed
   */
  public void setTrailer(PrintPanel trailer) throws PrinterException {
    if (trailer == null)  {
      trailerWidth  = 0;
      trailerHeight = 0;
    }
    else  {
      trailerWidth  = trailer.getWidth();
      trailerHeight = trailer.getHeight();
    }
    this.trailer = trailer;
  }


  /**
   * Sets the header panel.
   * The header will be printed at the start of each page, except
   * the first if the intro is set.
   *
   * @param header the header panel, null for none.
   * @throws PrinterException if failed
   */
  public void setHeader(PrintPanel header) throws PrinterException {
    if (header == null)  {
      headerWidth  = 0;
      headerHeight = 0;
    }
    else  {
      headerWidth  = header.getWidth();
      headerHeight = header.getHeight();
    }
    this.header = header;
  }


  /**
   * Sets the footer panel.
   * The footer will be printed at the end of each page, except
   * the last if the trailer is set.
   *
   * @param footer the footer panel, null for none.
   * @throws PrinterException if failed
   */
  public void setFooter(PrintPanel footer) throws PrinterException {
    if (footer == null)  {
      footerWidth  = 0;
      footerHeight = 0;
    }
    else  {
      footerWidth  = footer.getWidth();
      footerHeight = footer.getHeight();
    }
    this.footer = footer;
  }



  /**
   * Sets the panel for printing the data line.
   *
   * @param line the line panel, null if none
   * @throws PrinterException if failed
   */
  public void setLine(PrintPanel line) throws PrinterException {
    if (line == null)  {
      lineWidth  = 0;
      lineHeight = 0;
    }
    else  {
      lineWidth  = line.getWidth();
      lineHeight = line.getHeight();
    }
    this.line = line;
  }


  /**
   * Sets the subheaders.
   *
   * @param subHeader the array of subheaders (size must match levels)
   * @throws PrinterException if failed
   */
  public void setSubHeader(PrintPanel[] subHeader) throws PrinterException {
    if (subHeader.length != subLevels)  {
      throw new PrinterException("subHeader don't match subLevels");
    }
    for (int i=0; i < subLevels; i++) {
      subHeaderWidth[i]   = subHeader[i] == null ? 0 : subHeader[i].getWidth();
      subHeaderHeight[i]  = subHeader[i] == null ? 0 : subHeader[i].getHeight();
    }
    this.subHeader = subHeader;
  }


  /**
   * Sets the subfooters.
   *
   * @param subFooter the array of subfooter (size must match levels)
   * @throws PrinterException if failed
   */
  public void setSubFooter(PrintPanel[] subFooter) throws PrinterException {
    if (subFooter.length != subLevels)  {
      throw new PrinterException("subFooter don't match subLevels");
    }
    for (int i=0; i < subLevels; i++) {
      subFooterWidth[i]   = subFooter[i] == null ? 0 : subFooter[i].getWidth();
      subFooterHeight[i]  = subFooter[i] == null ? 0 : subFooter[i].getHeight();
    }
    this.subFooter = subFooter;
  }



  /**
   * Sets the aligment of the intro.
   *
   * @param align the alignment
   * @throws PrinterException if failed
   */
  public void setIntroAlignment(int align) throws PrinterException {
    if (intro == null)  {
      throw new PrinterException("can't set alignment for null-intro");
    }
    introAlignment = align;
  }


  /**
   * Sets the aligment of the trailer.
   *
   * @param align the alignment
   * @throws PrinterException if failed
   */
  public void setTrailerAlignment(int align) throws PrinterException {
    if (trailer == null)  {
      throw new PrinterException("can't set alignment for null-trailer");
    }
    trailerAlignment = align;
  }


  /**
   * Sets the aligment of the header.
   *
   * @param align the alignment
   * @throws PrinterException if failed
   */
  public void setHeaderAlignment(int align) throws PrinterException {
    if (header == null)  {
      throw new PrinterException("can't set alignment for null-header");
    }
    headerAlignment = align;
  }


  /**
   * Sets the aligment of the footer.
   *
   * @param align the alignment
   * @throws PrinterException if failed
   */
  public void setFooterAlignment(int align) throws PrinterException {
    if (footer == null)  {
      throw new PrinterException("can't set alignment for null-footer");
    }
    footerAlignment = align;
  }


  /**
   * Sets the aligment of the data line.
   *
   * @param align the alignment
   * @throws PrinterException if failed
   */
  public void setLineAlignment(int align) throws PrinterException {
    if (line == null)  {
      throw new PrinterException("can't set alignment for null-line");
    }
    lineAlignment = align;
  }


  /**
   * Sets the aligment of the subheaders.
   *
   * @param align the array of alignments (size must match levels)
   * @throws PrinterException if failed
   */
  public void setSubHeaderAlignment(int[] align) throws PrinterException {
    if (subHeader.length != subLevels)  {
      throw new PrinterException("subHeaderAlignments don't match subLevels");
    }
    subHeaderAlignment = align;
  }


  /**
   * Sets the aligment of the subfooters.
   *
   * @param align the array of alignments (size must match levels)
   * @throws PrinterException if failed
   */
  public void setSubFooterAlignment(int[] align) throws PrinterException {
    if (subFooter.length != subLevels)  {
      throw new PrinterException("subFooterAlignments don't match subLevels");
    }
    subFooterAlignment = align;
  }




  /**
   * Optionally sets the size of the intro if
   * not derived from the print panel.
   *
   * @param size the dimension
   * @throws PrinterException if failed
   */
  public void setIntroSize(Dimension size) throws PrinterException {
    if (intro == null)  {
      throw new PrinterException("can't set size for null-intro");
    }
    introWidth  = size.width;
    introHeight = size.height;
  }


  /**
   * Optionally sets the size of the trailer if
   * not derived from the print panel.
   *
   * @param size the dimension
   * @throws PrinterException if failed
   */
  public void setTrailerSize(Dimension size) throws PrinterException {
    if (trailer == null)  {
      throw new PrinterException("can't set size for null-trailer");
    }
    trailerWidth  = size.width;
    trailerHeight = size.height;
  }


  /**
   * Optionally sets the size of the header if
   * not derived from the print panel.
   *
   * @param size the dimension
   * @throws PrinterException if failed
   */
  public void setHeaderSize(Dimension size) throws PrinterException {
    if (header == null)  {
      throw new PrinterException("can't set size for null-header");
    }
    headerWidth  = size.width;
    headerHeight = size.height;
  }


  /**
   * Optionally sets the size of the footer if
   * not derived from the print panel.
   *
   * @param size the dimension
   * @throws PrinterException if failed
   */
  public void setFooterSize(Dimension size) throws PrinterException {
    if (footer == null)  {
      throw new PrinterException("can't set size for null-footer");
    }
    footerWidth  = size.width;
    footerHeight = size.height;
  }


  /**
   * Optionally sets the size of the data line if
   * not derived from the print panel.
   *
   * @param size the dimension
   * @throws PrinterException if failed
   */
  public void setLineSize(Dimension size) throws PrinterException {
    if (line == null)  {
      throw new PrinterException("can't set size for null-line");
    }
    lineWidth  = size.width;
    lineHeight = size.height;
  }


  /**
   * Optionally sets the size of the subheaders if
   * not derived from the print panel.
   *
   * @param sizes the array of dimensions (must match levels)
   * @throws PrinterException if failed
   */
  public void setSubHeaderSize(Dimension[] sizes) throws PrinterException {
    if (subHeader.length != subLevels)  {
      throw new PrinterException("subHeaderSizes don't match subLevels");
    }
    for (int i=0; i < subLevels; i++) {
      subHeaderWidth[i]  = sizes[i].width;
      subHeaderHeight[i] = sizes[i].height;
    }
  }


  /**
   * Optionally sets the size of the subfooters if
   * not derived from the print panel.
   *
   * @param sizes the array of dimensions (must match levels)
   * @throws PrinterException if failed
   */
  public void setSubFooterSize(Dimension[] sizes) throws PrinterException {
    if (subFooter.length != subLevels)  {
      throw new PrinterException("subFooterSizes don't match subLevels");
    }
    for (int i=0; i < subLevels; i++) {
      subFooterWidth[i]  = sizes[i].width;
      subFooterHeight[i] = sizes[i].height;
    }
  }


  /**
   * Get the current logical group level.
   *
   * @return the logical level of the statemachine, 0 = top, 1 = first data group, etc...
   */
  public int getLevel() {
    return state.level;
  }


  /**
   * Gets the current state.
   *
   * @return the state
   * @see ReportSource#advance
   */
  public int currentState() {
    return state.advance;
  }

  /**
   * Get the current page-number.
   * Pages start at 1.
   * @return the page number
   */
  public int currentPage()  {
    return state.lastPageIndex + 1;
  }


  /**
   * Sets the scaling.
   * Overwrites the automatic scaling.
   * @param scale the scaling
   */
  public void setScale(double scale)  {
    this.scale = scale;
  }

  /**
   * Gets the current scaling
   * @return the scaling
   */
  public double getScale()  {
    return scale;
  }


  /**
   * Sets the automatic scaling.
   * (one of SCALE_...)
   * @param autoScale the scale mode
   */
  public void setAutoScale(int autoScale)  {
    this.autoScale = autoScale;
  }

  /**
   * Gets the autoscale mode.
   * @return the scale mode
   */
  public int getAutoScale() {
    return autoScale;
  }



  /**
   * Sets the pixels to move before printing the next panel.<br>
   * The attribute will be cleared when used.
   *
   * @param moveBefore the number of pixels (&gt; 0 to move down)
   */
  public void setMoveBefore(int moveBefore) {
    state.moveBefore = moveBefore;
  }

  /**
   * Gets the pending pixels to move before printing the next panel.
   *
   * @return the pixels to move before printing the next panel
   */
  public int getMoveBefore()  {
    return state.moveBefore;
  }


  /**
   * Sets the pixels to move after printing the next panel.<br>
   * The attribute will be cleared when used.
   *
   * @param moveAfter the number of pixels (&gt; 0 to move down)
   */
  public void setMoveAfter(int moveAfter) {
    state.moveAfter = moveAfter;
  }

  /**
   * Gets the pending pixels to move after printing the next panel.
   *
   * @return the pixels to move after printing the next panel
   */
  public int getMoveAfter()  {
    return state.moveAfter;
  }



  /**
   * Sets a runnable to be executed once before printing the next panel.
   * @param runBefore the runnable
   */
  public void setRunBefore(Runnable runBefore)  {
    state.runBefore = runBefore;
  }

  /**
   * Gets the runnable to be executed once before printing the next panel.
   * @return the runnable
   */
  public Runnable getRunBefore() {
    return state.runBefore;
  }


  /**
   * Sets a runnable to be executed once after printing the next panel
   * @param runAfter the runnable
   */
  public void setRunAfter(Runnable runAfter)  {
    state.runAfter = runAfter;
  }

  /**
   * Gets
   * therunnable to be executed once after printing the next panel
   * @return the runnable
   */
  public Runnable getRunAfter() {
    return state.runAfter;
  }


  /**
   * Triggers the last panel to be printed again.
   */
  public void triggerPrintAgain() {
    state.printAgain = true;
  }



  /**
   * Retrieves the maximum printed y position so far for current page.
   * @return the max y-position
   */
  public int getYMax()  {
    return yMax;
  }


  /**
   * Retrieves the maximum printed y offset so far for current page.
   * @return the max y-offset
   */
  public int getYOffset() {
    return yOffset;
  }


  /**
   * Gets last returned code.
   *
   * @return the last advance code
   * @see ReportSource#advance
   */
  public int getAdvance() {
    return state.advance;
  }




  /**
   * Determines the number of pages *before* running the report.
   * If used as a Pageable this method will be invoked from PrinterJob.
   */
  @Override
  public int getNumberOfPages() {
    if (pages == UNKNOWN_NUMBER_OF_PAGES) { // if not yet known
      init();
      try {
        // run in layout-mode
        pages = 0;

        // @todo: number of pages are based on graphics-context (cause of scaling!)
        // rework that!!!!

        while (print(null, pageFormat, pages) == Printable.PAGE_EXISTS)  {
          pages++;
        }
      }
      catch (PrinterException e)  {
        pages = UNKNOWN_NUMBER_OF_PAGES;
      }
      initDone = false;
    }
    return pages;
  }


  /**
   * Runs the report.<br>
   * This implements the interface {@link Printable}.
   */
  @Override
  public int print(Graphics graphics, PageFormat pageFormat, int pageIndex)
         throws PrinterException {
    return print((Graphics2D)graphics, pageFormat, pageIndex, false);
  }



  @Override
  public PageFormat getPageFormat(int pageIndex) throws IndexOutOfBoundsException {
    return pageFormat;
  }


  @Override
  public Printable getPrintable(int pageIndex) throws IndexOutOfBoundsException {
    if (pageIndex < 0 || pageIndex >= pages)  {
      throw new IndexOutOfBoundsException("requested page " + (pageIndex+1) +
                                          " of " + pages);
    }
    if (pageIndex == pages-1) { // this was the last page
      source.close();           // we can safely close the data-source
    }
    return this;
  }




  /**
   * this is the printing workhorse
   */
  private int print(Graphics2D g2d, PageFormat pageFormat, int pageIndex, boolean countOnly)
         throws PrinterException {

    init();   // initially get heights of panels

    if (state.lastPageIndex == pageIndex && savedState != null) {
      // page will be printed again
      state = savedState;
      savedState = null;
      source.restore();
    }
    else  {
      // save this state. Could be restored!
      savedState = state.clone();
      source.save();
    }

    // save to check if page has been printed twice
    state.lastPageIndex = pageIndex;



    if (!state.lastPagePrinted && pageIndex >= 0 &&
        (pages == UNKNOWN_NUMBER_OF_PAGES || pageIndex < pages))  {

      int pageHeight;   // size of printable area
      int pageWidth;


      // if not portrait: check if g2d is already transformed (job.print(aset)!) or not (job.print())
      PrintHelper.adjustGraphics2D(g2d, pageFormat);

      // if not fixed: recompute scaling
      if (autoScale != SCALE_FIXED) {
        scale = (imageable ? pageFormat.getImageableWidth() : pageFormat.getWidth()) / getMaxWidth();
        if (autoScale == SCALE_IF_WIDER && scale > 1.0) {
          scale = 1.0;
        }
      }

      // set scaling
      g2d.scale(scale, scale);


      if (imageable)  {
        // position to top left corner
        g2d.translate((int)(pageFormat.getImageableX() / scale), (int)(pageFormat.getImageableY() / scale));
        // get printable height in pixels
        pageHeight = (int)(pageFormat.getImageableHeight() / scale);
        pageWidth  = (int)(pageFormat.getImageableWidth() / scale);
      }
      else  {
        // get total height in pixels
        pageHeight = (int)(pageFormat.getHeight() / scale);
        pageWidth  = (int)(pageFormat.getWidth() / scale);
      }

      yOffset = 0;   // we are at start
      yMax    = 0;   // clear maximum y so far

      int yDiff;         // advanced pixels downwards


      if (pageIndex == 0) {     // first page
        if (intro != null)  {   // print intro if any
          if (countOnly) {
            yOffset += introHeight +
                       alignPanelVertically(intro, introAlignment,
                                            introHeight, pageHeight);
          }
          else  {
            do  {
              state.printAgain = false;
              yDiff = source.prepareIntro(this);
              if (yDiff != 0) {
                g2d.translate(0, yDiff);
                yOffset += yDiff;
              }
              yDiff = printPanel(g2d, intro, introAlignment,
                                 introWidth, introHeight,
                                 pageWidth, pageHeight, yOffset);
              g2d.translate(0, yDiff);
              yOffset += yDiff;
            } while (state.printAgain);
          }
          yMax = yOffset;
        }
      }
      else  {     // successor page
        if (header != null)  {   // print header if any
          if (countOnly) {
            yOffset += headerHeight +
                       alignPanelVertically(header, headerAlignment,
                                            headerHeight, pageHeight);
          }
          else {
            do  {
              state.printAgain = false;
              yDiff = source.prepareHeader(this);
              if (yDiff != 0) {
                g2d.translate(0, yDiff);
                yOffset += yDiff;
              }
              yDiff = printPanel(g2d, header, headerAlignment,
                                 headerWidth, headerHeight,
                                 pageWidth, pageHeight, yOffset);
              g2d.translate(0, yDiff);
              yOffset += yDiff;
            } while (state.printAgain);
          }
          yMax = yOffset;
        }
      }

      // read data from the source until page is full or end of data or some error
      for(;;) {

        // check line-height whether to fit on page
        int topHeight = pageIndex == 0 ? introHeight : headerHeight;
        if (lineHeight > pageHeight - topHeight - footerHeight) {
          // pathological case
          throw new PrinterException("not even the first data line will not fit onto the page");
        }

        // print page if next line would not fit
        if (source.hasNext() &&
            yOffset + lineHeight + footerHeight > pageHeight) {
          break;
        }


        if (state.runBefore != null)  {
          state.runBefore.run();
          state.runBefore = null;
        }

        if (state.pending != ReportSource.ERROR) {
          state.advance = state.pending;        // something was pending
          state.pending = ReportSource.ERROR;   // clear pending
        }
        else  {
          if (state.advance != ReportSource.EOF)  {
            // get new state
            state.advance = source.advance(this);
          }
        }

        if (state.advance == ReportSource.ERROR)  {
          throw new PrinterException("ReportSource.prepare() failed");
        }

        else  {

          if (state.moveBefore != 0)  {
            g2d.translate(0, state.moveBefore);
            yOffset += state.moveBefore;
            if (yOffset > yMax) {
              yMax = yOffset;
            }
            state.moveBefore = 0;
            state.pending = state.advance;
            continue;
          }

          if (state.advance == ReportSource.EOF)  {

            // finish report
            while (state.level > 0) {   // close all pending sublevels
              // print subfooter if any
              state.level--;        // count the new level
              int subHeight = subFooterHeight[state.level];
              if (subHeight > 0)  {
                // check that the footer fits
                if (yOffset + subHeight >= pageHeight) {
                  // would not fit
                  state.pending = state.advance;
                  state.level++;
                  return Printable.PAGE_EXISTS; // print page
                }
                else  {
                  if (countOnly) {
                    yOffset += subHeight +
                               alignPanelVertically(subFooter[state.level],
                                                    subFooterAlignment[state.level],
                                                    subHeight, pageHeight);
                  }
                  else {
                    do  {
                      state.printAgain = false;
                      yDiff = source.prepareSubFooter(this, state.level + 1);
                      if (yDiff != 0) {
                        g2d.translate(0, yDiff);
                        yOffset += yDiff;
                      }
                      yDiff = printPanel(g2d, subFooter[state.level],
                                         subFooterAlignment[state.level],
                                         subFooterWidth[state.level], subHeight,
                                         pageWidth, pageHeight, yOffset);
                      g2d.translate(0, yDiff);
                      yOffset += yDiff;
                    } while (state.printAgain);
                  }
                }
              }
            } // while (state.level > 0)


            // print trailer if any
            if (trailerHeight > 0)  {
              // check that the footer fits
              if (yOffset + trailerHeight >= pageHeight) {
                // would not fit
                state.pending = state.advance;
                break;    // print footer if any
              }
              else  {
                if (yMax > yOffset) {
                  g2d.translate(0, yMax - yOffset);
                  yOffset = yMax;
                }
                if (countOnly) {
                  yOffset += trailerHeight +
                             alignPanelVertically(trailer, trailerAlignment,
                                                  trailerHeight, pageHeight);
                }
                else {
                  do  {
                    state.printAgain = false;
                    yDiff = source.prepareTrailer(this);
                    if (yDiff != 0) {
                      g2d.translate(0, yDiff);
                      yOffset += yDiff;
                    }
                    yDiff = printPanel(g2d, trailer, trailerAlignment,
                                       trailerWidth, trailerHeight,
                                       pageWidth, pageHeight, yOffset);
                    g2d.translate(0, yDiff);
                    yOffset += yDiff;
                  } while (state.printAgain);
                }
              }
            }
            state.lastPagePrinted = true;       // trigger NO_SUCH_PAGE on next run
            return Printable.PAGE_EXISTS;       // print page

          } // if (state.advance == ReportSource.EOF)


          else if (state.advance == ReportSource.PRINT)  {

            if (countOnly) {
              yOffset += lineHeight +
                         alignPanelVertically(line, lineAlignment,
                                              lineHeight, pageHeight);
            }
            else {
              do  {
                state.printAgain = false;
                yDiff = source.prepareLine(this);
                if (yDiff != 0) {
                  g2d.translate(0, yDiff);
                  yOffset += yDiff;
                }
                // cause prepareLine could have changed the line-Height
                // we must align and optionally clip if too large
                boolean clip = false;
                if (yOffset + lineHeight + footerHeight > pageHeight)	{
		  // we must clip!
		  lineHeight = pageHeight - footerHeight - yOffset;
                  if (lineHeight <= 0) {
                    lineHeight = 1; // pathological case
                  }
                  g2d.setClip(0, 0, pageWidth, lineHeight);
                  clip = true;
                }
                yDiff = printPanel(g2d, line, lineAlignment,
                                   lineWidth, lineHeight,
                                   pageWidth, pageHeight, yOffset);
                if (clip) {
                  // clear clipping
                  g2d.setClip(0, 0, pageWidth, pageHeight);
                }
                g2d.translate(0, yDiff);
                yOffset += yDiff;
              } while (state.printAgain);
            }

          } // if (state.advance == ReportSource.PRINT)


          else if (state.advance == ReportSource.PUSH)  {

            if (state.level >= subLevels) {
              throw new PrinterException("PUSH beyond " + subLevels);
            }
            // print subheader if any
            int subHeight = subHeaderHeight[state.level];

            if (subHeight > 0)  {
              // check that at least one header plus one dataline fits
              if (yOffset + subHeight + lineHeight + footerHeight >= pageHeight) {
                // would not fit
                state.pending = state.advance;
                break;  // print page
              }
              else  {
                if (countOnly) {
                  yOffset += subHeight +
                             source.prepareSubHeader(this, state.level + 1) +
                             alignPanelVertically(subHeader[state.level],
                                                  subHeaderAlignment[state.level],
                                                  subHeight, pageHeight);
                }
                else {
                  do  {
                    state.printAgain = false;
                    yDiff = source.prepareSubHeader(this, state.level + 1);
                    if (yDiff != 0) {
                      g2d.translate(0, yDiff);
                      yOffset += yDiff;
                    }
                    yDiff = printPanel(g2d, subHeader[state.level],
                                       subHeaderAlignment[state.level],
                                       subHeaderWidth[state.level], subHeight,
                                       pageWidth, pageHeight, yOffset);
                    g2d.translate(0, yDiff);
                    yOffset += yDiff;
                  } while (state.printAgain);
                }
              }
            }
            else  {
              // nothing to print, but make sure that subheader application code is run
              source.prepareSubHeader(this, state.level + 1);
            }

            state.level++;        // count the new level
          } // if (state.advance == ReportSource.PUSH)


          else if (state.advance == ReportSource.POP)  {

            if (state.level <= 0) {
              throw new PrinterException("POP from level zero");
            }
            // print subfooter if any
            state.level--;        // count the new level
            int subHeight = subFooterHeight[state.level];
            if (subHeight > 0)  {
              // check that the footer fits
              if (yOffset + subHeight >= pageHeight) {
                // would not fit
                state.pending = state.advance;
                state.level++;  // stay in this level
                break;  // print page
              }
              else  {
                if (countOnly) {
                  yOffset += subHeight +
                             source.prepareSubFooter(this, state.level + 1) +
                             alignPanelVertically(subFooter[state.level],
                                                  subFooterAlignment[state.level],
                                                  subHeight, pageHeight);
                }
                else {
                  do  {
                    state.printAgain = false;
                    yDiff = source.prepareSubFooter(this, state.level + 1);
                    if (yDiff != 0) {
                      g2d.translate(0, yDiff);
                      yOffset += yDiff;
                    }
                    yDiff = printPanel(g2d, subFooter[state.level],
                                       subFooterAlignment[state.level],
                                       subFooterWidth[state.level], subHeight,
                                       pageWidth, pageHeight, yOffset);
                    g2d.translate(0, yDiff);
                    yOffset += yDiff;
                  } while (state.printAgain);
                }
              }
            }
            else  {
              source.prepareSubFooter(this, state.level + 1);
            }

          } // if (state.advance == ReportSource.POP)


          // remember new yMax
          if (yOffset > yMax) {
            yMax = yOffset;
          }

          if (state.moveAfter != 0) {
            g2d.translate(0, state.moveAfter);
            yOffset += state.moveAfter;
            state.moveAfter = 0;
          }

        }

        if (state.runAfter != null) {
          state.runAfter.run();
          state.runAfter = null;
        }

      } // while (yOffset + lineHeight + footerHeight < pageHeight)

      // print footer (will always fit)
      if (state.advance != ReportSource.EOF || state.pending == ReportSource.EOF)  {
        if (footerHeight != 0)  {
          if (yMax > yOffset) {
            g2d.translate(0, yMax - yOffset);
            yOffset = yMax;
          }
          if (countOnly) {
            yOffset += footerHeight +
                       alignPanelVertically(footer, footerAlignment,
                                            footerHeight, pageHeight);
          }
          else {
            do  {
              state.printAgain = false;
              yDiff = source.prepareFooter(this);
              if (yDiff != 0) {
                g2d.translate(0, yDiff);
                yOffset += yDiff;
              }
              yDiff = printPanel(g2d, footer, footerAlignment,
                                 footerWidth, footerHeight,
                                 pageWidth, pageHeight, yOffset);
              g2d.translate(0, yDiff);
              yOffset += yDiff;
            } while (state.printAgain);
          }
        }
        return Printable.PAGE_EXISTS;   // drucken bzw. so tun als ob
      }
    }

    if (!countOnly && pages == UNKNOWN_NUMBER_OF_PAGES) { // if invoked as a Printable
      source.close();   // end of report
    }
    return Printable.NO_SUCH_PAGE;
  }


  /**
   * computes the heights of all printpanels and initialize some variables
   * assumes all panels already layed out!
   */
  private void init() {
    if (!initDone) {
      state = new State();
      pages = UNKNOWN_NUMBER_OF_PAGES;    // no pages computed so far
      if (initDone) {
        source.rewind();      // rewind the source
      }
      else  {
        source.open();        // open the datasource
      }
      initDone = true;
    }
  }


  /**
   * aligns a panel horizontally according to the alignment-flag.
   * returns the new x-offset where to start printing.
   */
  private int alignPanelHorizontally (PrintPanel panel, int align,
                                    int panelWidth, int pageWidth)   {
    switch (align)  {

      case NORTHEAST:
      case EAST:
      case SOUTHEAST:
        // shift rightmost
        return pageWidth - panelWidth;

      case CENTER:
      case NORTH:
      case SOUTH:
        // center horizontally
        return (pageWidth - panelWidth) / 2;
    }

    // nothing to do for NORTHWEST, WEST, SOUTHWEST

    return 0;
  }


  /**
   * aligns a panel vertically according to the alignment-flag.
   * returns the new y-offset where to start printing.
   */
  private int alignPanelVertically (PrintPanel panel, int align,
                                    int panelHeight, int pageHeight)   {
    switch (align)  {

      case SOUTHWEST:
      case SOUTH:
      case SOUTHEAST:
        // shift downmost
        return pageHeight - panelHeight;

      case CENTER:
      case WEST:
      case EAST:
        // center vertically
        return (pageHeight - panelHeight) / 2;
    }

    // nothing to do for NORTHWEST, NORTH, NORTHEAST

    return 0;
  }


  /**
   * does the printing incl. alignment.
   * returns the points printed vertically
   */
  private int printPanel(Graphics graphics, PrintPanel panel, int align,
                         int panelWidth, int panelHeight,
                         int pageWidth, int pageHeight,
                         int currentOffset)  {

    int xOff = alignPanelHorizontally (panel, align, panelWidth,  pageWidth);
    int yOff = alignPanelVertically   (panel, align, panelHeight, pageHeight - currentOffset);

    Graphics2D g2d = (Graphics2D)graphics;

    if (xOff != 0 || yOff != 0) {
      g2d.translate(xOff, yOff);
    }
    // print
    panel.print(graphics);

    // translate back
    if (xOff != 0 || yOff != 0) {
      g2d.translate(-xOff, -yOff);
    }

    return yOff + panelHeight;
  }



  /**
   * returns the maximum width (used for scaling)
   */
  private int getMaxWidth()  {
    int max = introWidth;
    if (trailerWidth > max) {
      max = trailerWidth;
    }
    if (headerWidth > max) {
      max = headerWidth;
    }
    if (footerWidth > max) {
      max = footerWidth;
    }
    if (lineWidth > max) {
      max = lineWidth;
    }
    if (subHeaderWidth != null) {
      for (int i=0; i < subHeaderWidth.length; i++) {
        if (subHeaderWidth[i] > max) {
          max = subHeaderWidth[i];
        }
      }
    }
    if (subFooterWidth != null) {
      for (int i=0; i < subFooterWidth.length; i++) {
        if (subFooterWidth[i] > max) {
          max = subFooterWidth[i];
        }
      }
    }
    return max;
  }




  /**
   * Represents the state of this state machine.
   */
  private static class State implements Cloneable {
    // printing state of report
    int       lastPageIndex   = -1;                 // last printed page
    int       level           = 0;                  // current level (0=top, ... , subLevels)
    int       advance         = ReportSource.ERROR; // last state from source.advance()
    int       pending         = ReportSource.ERROR; // pending state from source, 0 = none
    boolean   lastPagePrinted = false;              // last page was printed
    int       moveBefore;                           // Y-pixels to move before printing the next panel
    int       moveAfter;                            // Y-pixels to move after having printed the next panel
    Runnable  runBefore;                            // run once before printing next panel
    Runnable  runAfter;                             // run once after printing next panel
    boolean   printAgain;                           // true if print same panel again

    @Override
    public State clone() {
      try {
        return (State)super.clone();
      }
      catch (CloneNotSupportedException ex) {
        throw new InternalError();    // should never happen
      }
    }
  }

}