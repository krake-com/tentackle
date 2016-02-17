/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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


package org.tentackle.swing;

import java.awt.EventQueue;
import javax.swing.JDialog;


/**
 * A worker thread.
 * <p>
 * The thread will dispose a dialog if terminated.
 * <p>
 * The specified {@link Runnable} must either monitor whether the thread
 * got interrupt()ed by checking {@link #isInterrupted()}
 * or override {@link #interrupt()} and terminate the thread gracefully.
 *
 * @see WorkerDialog
 * @author harald
 */
public class WorkerThread extends Thread {

  private final Runnable runnable;       // what's to do?
  private final JDialog workerDialog;    // worker dialog

  /**
   * Creates a worker thread.
   *
   * @param runnable  the runnable for this thread
   * @param modal true if dialog should be set to modal
   * @param title the dialog's title, null if leave unchanged
   * @param workerDialog the dialog that will be disposed when thread terminates,
   *        null if {@link WorkerDialog}
   */
  public WorkerThread (Runnable runnable, boolean modal, String title, JDialog workerDialog) {

    this.runnable = runnable;
    this.workerDialog = workerDialog == null ? new WorkerDialog(this) : workerDialog;

    if (title != null) {
      this.workerDialog.setTitle(title);
    }
    this.workerDialog.setModal(modal);

    setPriority(NORM_PRIORITY);
  }

  /**
   * Creates a worker thread using an {@link WorkerDialog}.
   *
   * @param worker  what's to do in the run-method?
   * @param modal true if dialog should be set to modal
   * @param title the dialog's title, null if leave unchanged
   */
  public WorkerThread (Runnable worker, boolean modal, String title) {
    this(worker, modal, title, null);
  }

  /**
   * Creates a worker thread using a modal {@link WorkerDialog}
   * and a given title.<br>
   * This is the default case.
   *
   * @param worker  what's to do in the run-method?
   * @param title the dialog's title, null if leave unchanged
   */
  public WorkerThread (Runnable worker, String title) {
    this(worker, true, title, null);
  }



  /**
   * Gets the worker dialog.
   *
   * @return the worker dialog (usually a {@link WorkerDialog})
   */
  public JDialog getWorkerDialog() {
    return workerDialog;
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to dispose the dialog.
   */
  @Override
  public void run() {
    // do what has to be done
    if (runnable != null) {
      runnable.run();
    }
    // dispose from event queue
    EventQueue.invokeLater (() -> workerDialog.dispose());
  }



  /**
   * {@inheritDoc}
   * <p>
   * Overridden to show the dialog.
   * The dialog will be shown by invokeLater. This
   * allows the method to return immediately even if the dialog is modal.
   */
  @Override
  public void start() {
    // show the dialog
    EventQueue.invokeLater(() -> {
      workerDialog.pack();
      workerDialog.setVisible(true);
      // notice: although this method blocks if modal dialog
      // it will not block the event queue! Nice trick... ;-)
    });
    super.start();
  }


  /**
   * Starts the workerthread and waits until the modal
   * worker dialog is closed.
   */
  public void startAndWait() {
    super.start();
    workerDialog.pack();
    workerDialog.setVisible(true);  // this blocks if dialog is modal
  }

}
