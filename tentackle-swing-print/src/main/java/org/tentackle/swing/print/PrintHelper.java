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

// Created on February 6, 2003, 7:24 PM

package org.tentackle.swing.print;

import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.swing.FormError;
import org.tentackle.swing.FormInfo;
import org.tentackle.swing.FormUtilities;
import org.tentackle.swing.WorkerThread;



/**
 * Utility methods for printing.
 *
 * @author harald
 */
public final class PrintHelper {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(PrintHelper.class);

  private static final double DOTS_PER_INCH = 72.0;

  private static MediaSize mediaSize;
  private static MediaSizeName mediaSizeName;
  private static MediaPrintableArea area;
  private static Runnable runBeforePrint;
  private static Runnable runAfterPrint;


  static  {
    // european locale.
    // @todo get from Locale
    setMediaSizeName(MediaSizeName.ISO_A4);
  }




  /**
   * Asks the user for a printer and settings and prints a printable.
   *
   * @param printable the printable
   * @param jobName the name of the job
   * @return true if printed, false if error or user aborted
   */
  public static boolean print(Printable printable, String jobName)  {
    PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
    aset.add(mediaSizeName);
    aset.add(area);
    PrinterJob job = PrinterJob.getPrinterJob();
    if (jobName != null)  {
      aset.add(new JobName(jobName, Locale.getDefault()));
      job.setJobName(jobName);
    }
    job.setPrintable(printable);
    if (job.printDialog(aset)) {
      try {
        // this does the trick to use the format of the print dialog!
        job.print(aset);
        return true;
      }
      catch(PrinterException pe) {
        FormError.showException(PrintSwingPrintBundle.getString("PRINTING FAILED!"), pe);
      }
    }
    return false;
  }



  /**
   * Asks the user for a printer and settings and return the pageformat.<br>
   * Does not print! Use {@link #printJob(java.awt.print.PrinterJob, javax.print.attribute.PrintRequestAttributeSet, boolean)} to print.
   *
   * @param   job is the new printerjob
   * @param   jobName is the jobname, null = none
   * @param   aset is an initialized (and possibly empty) attribute set, must not be null, because its returned!
   * @return  the pageformat and possibly updated aset, null if printing aborted
   */
  public static PageFormat print(PrinterJob job, String jobName, PrintRequestAttributeSet aset)  {
    if (aset.get(Media.class) == null)  {
      aset.add(mediaSizeName);
    }
    if (aset.get(MediaPrintableArea.class) == null) {
      aset.add(area);
    }
    if (jobName != null)  {
      aset.add(new JobName(jobName, Locale.getDefault()));
      job.setJobName(jobName);
    }
    if (job.printDialog(aset)) {
      return getPageFormat(aset);
    }
    return null;
  }



  /**
   * Adjusts the graphics context according to the given format.<br>
   *
   * The job can be printed in two ways: by job.print() and by job.print(aset).
   * The latter analyzes the aset, builds a pageformat and transforms the graphics-context accordingly.
   * However, the first variant does not, i.e. if the pageformat returned by print(job, jobname) above
   * is *not* PORTRAIT, the graphics context still has to be transformed and the page-dimensions
   * changed.
   *
   * @param g the graphics context
   * @param format the pageformat
   * @return  true if transformation adjusted, else false
   */
  public static boolean adjustGraphics2D(Graphics2D g, PageFormat format)  {
    if (format.getOrientation() != PageFormat.PORTRAIT) {
      // check if transformation necessary
      AffineTransform oldTransform = g.getTransform();
      if ((oldTransform.getType() & AffineTransform.TYPE_QUADRANT_ROTATION) == 0)  {
        g.setTransform(new AffineTransform(format.getMatrix()));
      }
    }
    return false;
  }




  /**
   * Builds a pageformat from an attribute set.<br>
   *
   * This is the "missing" link between printDialog(aset) and setting the
   * pageFormat without calling pageDialog().
   *
   * @param aset the attribute set
   * @return the pageformat
   */
  public static PageFormat getPageFormat(PrintRequestAttributeSet aset) {

    PageFormat format = new PageFormat();   // constructs a default format (always letter, portrait)
    Paper paper = format.getPaper();        // gets the paper to be changed

    // check if printable area has been requested
    Attribute attrib = aset.get(MediaPrintableArea.class);
    if (attrib != null)  {
      MediaPrintableArea mpa = (MediaPrintableArea)attrib;
      paper.setImageableArea(mpa.getX(MediaPrintableArea.INCH) * DOTS_PER_INCH,
                             mpa.getY(MediaPrintableArea.INCH) * DOTS_PER_INCH,
                             mpa.getWidth(MediaPrintableArea.INCH) * DOTS_PER_INCH,
                             mpa.getHeight(MediaPrintableArea.INCH) * DOTS_PER_INCH);
    }

    // check if size has been requested
    attrib = aset.get(Media.class);
    if (attrib instanceof MediaSizeName) {
      MediaSize size = MediaSize.getMediaSizeForName((MediaSizeName)attrib);
      paper.setSize(size.getX(MediaSize.INCH) * DOTS_PER_INCH, size.getY(MediaSize.INCH) * DOTS_PER_INCH);
    }

    format.setPaper(paper);   // we must set, because paper is cloned()

    // check if orientation has been requested
    attrib = aset.get(OrientationRequested.class);
    if (attrib != null) {
      OrientationRequested orientation = (OrientationRequested)attrib;
      if (orientation == OrientationRequested.LANDSCAPE)  {
        format.setOrientation(PageFormat.LANDSCAPE);
      }
      else if (orientation == OrientationRequested.PORTRAIT)  {
        format.setOrientation(PageFormat.PORTRAIT);
      }
      else if (orientation == OrientationRequested.REVERSE_LANDSCAPE)  {
        format.setOrientation(PageFormat.REVERSE_LANDSCAPE);
      }
      else if (orientation == OrientationRequested.REVERSE_PORTRAIT)  {
        format.setOrientation(PageFormat.PORTRAIT); // not supported, use PORTRAIT instead
      }
    }

    return format;
  }




  // a queued printer job
  private static class JobRequest {

    PrinterJob job;
    PrintRequestAttributeSet aset;
    String title;

    JobRequest(PrinterJob job, PrintRequestAttributeSet aset) {
      this.job = job;
      this.aset = aset;
      title = job.getJobName();
      if (title == null) {
        title = PrintSwingPrintBundle.getString("PRINTING...");
      }
      else {
        title = MessageFormat.format(PrintSwingPrintBundle.getString("PRINTING {0} ..."), title);
      }
      LOGGER.fine("queued printing job {0}", job.getJobName());
    }
  }


  // the printing thread
  private static class JobThread extends WorkerThread {

    private final Queue<JobRequest> jobQueue;
    private volatile JobRequest request;
    private volatile boolean abortRequested;

    public JobThread(final boolean modal) {
      super(null, modal, PrintSwingPrintBundle.getString("PRINTING..."));
      jobQueue = modal ? modalJobQueue : nonModalJobQueue;
    }

    @Override
    public void run() {
      while (!abortRequested) {
        synchronized(jobQueue) {
          request = jobQueue.poll();
          if (request == null)  {
            LOGGER.finer("printing queue is empty");
            break;    // queue is empty
          }
        }
        try {
          LOGGER.fine("printing job {0}", request.job.getJobName());
          if (runBeforePrint != null) {
            runBeforePrint.run();
          }
          // set title
          final String title = request.title;
          EventQueue.invokeLater(() -> getWorkerDialog().setTitle(title));
          // print document
          request.job.print(request.aset);
        }
        catch (Exception e)  {
          if (e instanceof PrinterAbortException) {
            FormInfo.show(PrintSwingPrintBundle.getString("PRINTING ABORTED"));
          }
          else  {
            LOGGER.logStacktrace(Level.SEVERE, e);
            FormError.showException(PrintSwingPrintBundle.getString("PRINTING FAILED!"), e);
          }
        }
        finally {
          if (runAfterPrint != null) {
            runAfterPrint.run();
          }
        }
      }
      if (abortRequested) {
        synchronized(jobQueue) {
          jobQueue.clear(); // aborted: empty queue
        }
      }
      super.run();    // cleanup thread and close dialog
    }


    @Override
    public void start() {
      super.start();
      if (!EventQueue.isDispatchThread()) {
        // wait for dialog to show up
        FormUtilities.getInstance().waitForEmptyEventQueue();
      }
    }

    @Override
    public void interrupt() {
      // no super.interrupt() because
      // we better terminate the print job gracefully --
      // hopefully...
      if (request != null) {
        LOGGER.fine("printing canceled");
        request.job.cancel();
        request = null;
      }
      abortRequested = true;
    }
  }


  // job queues
  private static final Queue<JobRequest> modalJobQueue = new LinkedList<>();
  private static final Queue<JobRequest> nonModalJobQueue = new LinkedList<>();

  // the current workerthread, null if none
  private static WorkerThread modalJobThread;
  private static WorkerThread nonModalJobThread;



  /**
   * Prints a job in separate worker thread while displaying a printing worker dialog.<br>
   * Requests are automatically queued in background if jobs are not finished yet.
   * The jobs are printed in the order of invocations of this method.
   * There are 2 queues: one for modal and one for non-modal status dialogs. At any
   * time there is no more than one modal and no more than one non-modal worker dialog.
   * It is recommended (though not strictly necessary) to invoke this method from another
   * thread than swing's event dispatch thread. If not invoked from the dispatch thread
   * the first job request will wait until the worker dialog is shown before actually
   * start printing (better user feedback).
   *
   * @param job the printer job
   * @param aset the printing attributes
   * @param modal true if modal dialog
   * @return the WorkerThread assigned to the job
   */
  public static WorkerThread printJob(PrinterJob job, PrintRequestAttributeSet aset, boolean modal) {

    JobRequest request = new JobRequest(job, aset);

    if (modal) {
      synchronized(modalJobQueue) {
        modalJobQueue.add(request);
        if (modalJobThread == null || !modalJobThread.isAlive()) {
          modalJobThread = new JobThread(true);
          modalJobThread.start();
        }
        return modalJobThread;
      }
    }
    else  {
      synchronized(nonModalJobQueue) {
        nonModalJobQueue.add(request);
        if (nonModalJobThread == null || !nonModalJobThread.isAlive()) {
          nonModalJobThread = new JobThread(false);
          nonModalJobThread.start();
        }
        return nonModalJobThread;
      }
    }
  }


  /**
   * Prints a job in separate thread in a modal dialog.
   *
   * @param job the printer job
   * @param aset the printing attributes
   * @return the WorkerThread assigned to the job
   * @see #printJob(java.awt.print.PrinterJob, javax.print.attribute.PrintRequestAttributeSet, boolean)
   */
  public static WorkerThread printJob(PrinterJob job, PrintRequestAttributeSet aset) {
    return printJob(job, aset, true);
  }



  /**
   * Sets the media size.
   * @param name the MediaSizeName
   */
  public static void setMediaSizeName(MediaSizeName name) {
    mediaSizeName = name;
    mediaSize     = MediaSize.getMediaSizeForName(mediaSizeName);
    area          = new MediaPrintableArea(0.5f, 0.5f,
                                           mediaSize.getX(MediaSize.INCH) - 1.0f,
                                           mediaSize.getY(MediaSize.INCH) - 1.0f,
                                           MediaPrintableArea.INCH);
  }


  /**
   * Gets the media size name.
   *
   * @return the media size name
   */
  public static MediaSizeName getMediaSizeName()  {
    return mediaSizeName;
  }


  /**
   * Sets a Runnable to be invoked before job.print(aset).
   * Default is null.
   * @param run the runnable
   */
  public static void setRunBeforePrint(Runnable run)  {
    runBeforePrint = run;
  }

  /**
   * Gets the Runnable to be invoked before job.print(aset).
   * @return the runnable, null if none
   */
  public static Runnable getRunBeforePrint() {
    return runBeforePrint;
  }



  /**
   * Sets a Runnable to be invoked after job.print(aset).
   * Default is null.
   * @param run the runnable
   */
  public static void setRunAfterPrint(Runnable run)  {
    runAfterPrint = run;
  }

  /**
   * Gets the Runnable to be invoked after job.print(aset).
   * @return the runnable, null if none
   */
  public static Runnable getRunAfterPrint() {
    return runAfterPrint;
  }


  /**
   * prevent instantiation.
   */
  private PrintHelper() {}

}
