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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import org.tentackle.bind.Bindable;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.swing.FormPanel;
import org.tentackle.swing.FormUtilities;
import org.tentackle.validate.ValidationResult;


/**
 * Panel to view and/or edit an {@link PersistentDomainObject}.
 * <p>
 * The panel is designed to used as a plugin by {@link PdoEditDialog}.
 * Notice: the class should be abstract but most GUI-designers cannot
 * handle abstract classes and need a bean to instantiate.
 * Hence, some methods that should be abstract as well are non-abstract
 * default implementations that *must* be overridden.
 *
 * @param <T> the PDO type
 * @author harald
 */
@SuppressWarnings("serial")
public class PdoPanel<T extends PersistentDomainObject<T>> extends FormPanel implements DomainContextProvider, Printable, Pageable {

  /** the enclosing {@link PdoEditDialog}, null if none **/
  protected PdoEditDialog<T> dialog;

  private PageFormat defaultPageFormat;  // default page format


  /**
   * Sets the database object and updates the view.
   * <p>
   * The default implementation returns false.
   * Must be overridden!
   *
   * @param object the database object
   * @return true if object accepted
   */
  @Bindable
  public boolean setPdo (T object)  {
    return false;
  }

  /**
   * Gets the currently displayed database object.
   * <p>
   * The default implementation returns null.
   * Must be overridden!
   *
   * @return the database object
   */
  @Bindable
  public T getPdo ()  {
    return null;
  }


  @Override
  public DomainContext getDomainContext() {
    T pdo = getPdo();
    return pdo == null ? null : pdo.getDomainContext();
  }

  /**
   * Verifies the data object.
   * <p>
   * Holds verification before the object is actually saved.
   *
   * @return the list of errors, never null
   */
  @SuppressWarnings("unchecked")
  public List<InteractiveError> verifyObject() {
    List<InteractiveError> errorList = new ArrayList<>();
    try {
      if (getPdo().findDuplicate() != null) {
        errorList.add(InteractiveErrorFactory.getInstance().createInteractiveError(MessageFormat.format("{0} {1} already exists",
                                     getPdo().getSingular(), getPdo().toString()),
                Logger.Level.WARNING, null, null, null));
      }
    }
    catch (UnsupportedOperationException ex) {
      // no harm: checking will be done by the persistence layer anyway
    }

    return errorList;
  }


  /**
   * Checks the object for consistency before it is saved.<br>
   *
   * This method must *NOT* do any modifications to the database.
   * It is invoked from {@link PdoEditDialog} within the transaction
   * of saving the object.
   * <p>
   * The default implementation invokes {@link #verifyObject()}.
   *
   * @return true if continue tx, false if rollback tx
   */
  public boolean prepareSave() {
    List<InteractiveError> errorList = verifyObject();
    // show/clear errors
    if (dialog != null) {
      TooltipAndErrorPanel ttep = dialog.getTooltipAndErrorPanel();
      if (ttep != null) {
        ttep.setErrors(errorList);
      }
    }
    return errorList.isEmpty();
  }


  /**
   * Prepares the cancel operation.<br>
   *
   * Invoked when the dialog is closed or the object
   * is removed from the panel in general.
   * <p>
   * The default implementation returns true.
   *
   * @return true if cancel is ok, false if not
   */
  public boolean prepareCancel() {
    return true;
  }


  /**
   * Prepares to create a new object.<br>
   *
   * Invoked when the user instantiates a new object
   * (presses the "new" button, for example).
   * <p>
   * The default implementation returns true.
   *
   * @return true if creating new object is ok, false if not
   */
  public boolean prepareNew() {
    return true;
  }


  /**
   * Prepares to search for objects.<br>
   *
   * Invoked when the user wants to search for objects
   * (presses the "search" button, for example).
   * <p>
   * The default implementation returns true.
   *
   * @return true if search is ok, false if not
   */
  public boolean prepareSearch() {
    return true;
  }


  /**
   * Prepares to delete the current object.<br>
   *
   * Invoked when the user wants to delete the current object
   * (presses the "delete" button, for example).
   * <p>
   * This method must *NOT* do any modifications to the database.
   * It is invoked from {@link PdoEditDialog} within the transaction
   * deleting the object.
   * <p>
   * The default implementation returns true.
   *
   * @return true if delete is ok, false if not
   */
  public boolean prepareDelete() {
    return true;
  }


  /**
   * Announce that a delete is going to happen.<br>
   * As opposed to {@link #prepareDelete()} this method
   * is invoked by {@link PdoEditDialog} <em>before</em> the transaction is started.
   * <p>
   * The default implementation returns true.
   *
   * @return true if continue with delete, false if abort
   */
  public boolean announceDelete() {
    return true;
  }


  /**
   * Announce that a save is going to happen.<br>
   * As opposed to {@link #prepareSave()} this method
   * is invoked by {@link PdoEditDialog} <em>before</em> the transaction is started.
   * <p>
   * The default implementation returns true.
   *
   * @return true if continue with delete, false if abort
   */
  public boolean announceSave() {
    return true;
  }



  /**
   * Creates an interactive error from a validation result.
   *
   * @param validationResult the validation result
   * @return the interactive error
   */
  public InteractiveError createInteractiveError(ValidationResult validationResult) {
    return InteractiveErrorFactory.getInstance().createInteractiveError(null, getBinder(), validationResult);
  }


  /**
   * Creates interactive errors from validation results.
   *
   * @param validationResults the validation results
   * @return the interactive errors
   */
  public List<InteractiveError> createInteractiveErrors(List<ValidationResult> validationResults) {
    List<InteractiveError> errors = new ArrayList<>();
    for (ValidationResult validationResult: validationResults) {
      errors.add(createInteractiveError(validationResult));
    }
    return errors;
  }

  /**
   * Sets the parent {@code PdoEditDialog}.
   * <p>
   * Invoked from {@link PdoEditDialog} when this panel
   * is "plugged in".
   *
   * @param dialog the parent dialog
   */
  public void setPdoEditDialog (PdoEditDialog<T> dialog) {
    this.dialog = dialog;
  }

  /**
   * Gets the parent PdoEditDialog.
   *
   * @return the parent dialog, null if this panel is not a child of an {@code PdoEditDialog}.
   */
  public PdoEditDialog<T> getPdoEditDialog() {
    return dialog;
  }


  /**
   * Sets the initial focus.<br>
   *
   * Usually requests the focus for the top left component.
   * The default implementation does nothing.
   * Must be overridden!
   */
  public void setInitialFocus() {
    // override this!!!
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to generate the title from the current object
   * if title is null.
   */
  @Override
  public String getTitle()  {
    String title = super.getTitle();
    if (title == null) {
      T obj = getPdo();
      if (obj != null)  {
        String contextName = obj.getBaseContext().toString();
        if (obj.isNew()) {
          title = contextName == null || contextName.length() == 0 ?
                    obj.getPlural() :
                    obj.getPlural() + " in " + contextName;
        }
        else  {
          title = obj.getSingular() + " " +
                    (contextName == null || contextName.length() == 0 ?
                      obj.toString() :
                      obj.toString() + " in " + contextName);
        }
      }
    }
    return title;
  }


  /**
   * Packs the parent window.
   */
  public void pack() {
    if (dialog != null) {
      dialog.pack();
    }
    else {
      FormUtilities.getInstance().packParentWindow(this);
    }
  }


  /**
   * Gets the {@link Pageable} of this panel.<br>
   *
   * PdoEditDialog invokes this method if the "print" button is pressed.
   * If this method returns null, getPrintable() will be invoked.
   * The default implementation returns null.
   * @param printJob the printer job
   * @return the pageable, null if try getPrintable
   */
  public Pageable getPageable(PrinterJob printJob) {
    defaultPageFormat = printJob.defaultPage();
    return null;
  }


  /**
   * Gets the {@link Printable} of this panel.<br>
   *
   * PdoEditDialog invokes this method if the "print" button is pressed
   * and getPageable() returned null.
   * The default implementation returns {@code this}, which is simply
   * a screendump of this panel.
   *
   * @param printJob the printer job
   * @return the printable, null if printing is disabled
   */
  public Printable getPrintable(PrinterJob printJob) {
    defaultPageFormat = printJob.defaultPage();
    return this;
  }


  /**
   * Indicates a successful printout.<br>
   *
   * The default implementation does nothing.
   * Can be used to set a printing date in an object or alike.
   */
  public void markPrinted() {
  }

  // ------------------------------ implements Printable ---------------------

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation prints a screendump of this panel.
   */
  @Override
  public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
    if (pageIndex > 0) {
      return(Printable.NO_SUCH_PAGE);
    }
    else {
      Graphics2D g2d = (Graphics2D)graphics;
      g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
      double scaleX = pageFormat.getImageableWidth() / getWidth();
      double scaleY = pageFormat.getImageableHeight() / getHeight();
      double scale  = scaleX < scaleY ? scaleX : scaleY;
      if (scale < 1.0)  {
        g2d.scale(scale, scale);
      }
      paint(g2d);
      return(Printable.PAGE_EXISTS);
    }
  }


  // ----------------- implements Pageable -------------------------------

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation returns {@code this}.<br>
   */
  @Override
  public Printable getPrintable(int pageIndex) {
    return this;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation returns the default pageformat of the printjob.
   */
  @Override
  public PageFormat getPageFormat(int pageIndex) {
    return defaultPageFormat;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation returns {@code UNKNOWN_NUMBER_OF_PAGES}.
   */
  @Override
  public int getNumberOfPages() {
    return Pageable.UNKNOWN_NUMBER_OF_PAGES;
  }

}
