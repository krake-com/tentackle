/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.fx;

import org.tentackle.bind.Bindable;
import org.tentackle.bind.Binder;
import org.tentackle.bind.Binding;
import org.tentackle.bind.BindingException;
import org.tentackle.bind.BindingMember;
import org.tentackle.common.Constants;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.common.StripMode;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.FxTreeTableView;
import org.tentackle.fx.component.Note;
import org.tentackle.fx.component.auto.AutoCompletionController;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.log.Logger;
import org.tentackle.misc.ConcurrencyHelper;
import org.tentackle.misc.Holder;
import org.tentackle.misc.SubString;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.validate.ValidationFailedException;
import org.tentackle.validate.ValidationMapper;
import org.tentackle.validate.ValidationResult;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.TransformationList;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Skinnable;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.skin.TableViewSkin;
import javafx.scene.control.skin.TreeTableViewSkin;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Scale;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.StringTokenizer;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;


interface FxUtilitiesHolder {
  FxUtilities INSTANCE = ServiceFactory.createService(FxUtilities.class, FxUtilities.class);
}


/**
 * Utility methods for Fx.
 *
 * @author harald
 */
@Service(FxUtilities.class)    // defaults to self
public class FxUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static FxUtilities getInstance() {
    return FxUtilitiesHolder.INSTANCE;
  }


  private static final Logger LOGGER = Logger.get(FxUtilities.class);

  /**
   * Gap between a column header's text and its graphic.
   */
  private static final double HEADER_GRAPHIC_TEXT_GAP = 4.0;

  /**
   * Executor for {@link #runAndWait(Runnable)} below.
   */
  private final Executor blockingFxExecutor = ConcurrencyHelper.createBlockingExecutor(Platform::runLater);

  /**
   * The help url.<br>
   * Prefix for online help, e.g.: <code>http://localhost/manual/index.html</code>
   */
  private String helpURL;

  /**
   * Flag to avoid invoking Platform.exit() more than once
   */
  private boolean terminated;


  /**
   * Creates the FX utilities.
   */
  public FxUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  /**
   * Shows a stage.<br>
   * Should be preferred over {@link Stage#show()} to allow adding
   * application-specific bookkeeping.
   *
   * @param stage the stage to show
   */
  public void show(Stage stage) {
    stage.show();
  }

  /**
   * Gets the default cursor for the given node.
   *
   * @param node the node
   * @return the default cursor, never null
   */
  public Cursor getDefaultCursor(Node node) {
    // default impl treats all nodes the same
    return Cursor.DEFAULT;
  }

  /**
   * Gets the wait cursor for the given node.
   *
   * @param node the node
   * @return the wait cursor, never null
   */
  public Cursor getWaitCursor(Node node) {
    // default impl treats all nodes the same
    return Cursor.WAIT;
  }

  /**
   * Returns whether decimal separator should be inserted automatically if missing in money input.<br>
   * This is usually a global setting for the whole application, but it can be filtered according
   * to the component (parent component, etc...).
   *
   * @param component the input component
   * @return true if insert separator according to scale if missing
   */
  public boolean isLenientMoneyInput(FxTextComponent component) {
    return false;
  }

  /**
   * Gets the default column width for text areas if not set by the binding or the model's COLS= option.
   *
   * @param textArea the text area component
   * @return the column width (default is 30)
   */
  public int getDefaultTextAreaColumns(FxTextArea textArea) {
    return 30;
  }

  /**
   * Gets the number of rows of the multi-line editor a one-line text area expands to,
   * if not set on the component.
   *
   * @param textArea the text area component
   * @return the row count (default is 6)
   */
  public int getDefaultExpandedRowCount(FxTextArea textArea) {
    return 6;
  }

  /**
   * Gets the timeout for the prefix selection in dropdown lists such as in combo boxes.
   *
   * @return the timeout in milliseconds (default is 750)
   */
  public long getPrefixSelectionTimeout() {
    return 750;
  }

  /**
   * Sets the help URL prefix.
   *
   * @param helpURL the URL prefix
   */
  public void setHelpURL(String helpURL) {
    this.helpURL = helpURL;
  }

  /**
   * Gets the help URL prefix.
   *
   * @return the URL prefix
   */
  public String getHelpURL() {
    return helpURL;
  }

  /**
   * Opens the online help for a given component.
   *
   * @param owner the owner window or node
   * @see #setHelpURL(String)
   */
  public void showHelp(Object owner)  {
    String compUrl = null;
    FxControl control = null;
    if (owner instanceof FxControl) {
      control = (FxControl) owner;
    }
    while (compUrl == null && control != null) {
      compUrl = control.getHelpUrl();
      control = control.getParentContainer();
    }

    StringBuilder buf = new StringBuilder();
    String urlPrefix = getHelpURL();
    if (urlPrefix != null) {
      buf.append(urlPrefix);
      if (!urlPrefix.endsWith("/")) {
        buf.append('/');
      }
    }
    if (compUrl != null) {
      buf.append(compUrl);
    }
    String url = buf.toString();

    if (!url.isEmpty())  {
      run(owner, () -> browse(URI.create(url)));
    }
  }

  /**
   * Terminates the FX client.<br>
   * The default implementation invokes {@link Platform#exit()}.
   */
  public void terminate() {
    synchronized (this) {
      if (terminated) {
        return;
      }
      terminated = true;
    }
    Platform.exit();
  }

  /**
   * Launches the default browser to display a {@code URI}.<br>
   * Throws {@link FxRuntimeException} if failed.
   *
   * @param uri the URI to be displayed in the user default browser
   */
  public void browse(URI uri) {
    try {
      LOGGER.info("launching browser for {0}", uri);
      Desktop.getDesktop().browse(uri);
    }
    catch (IOException ex) {
      throw new FxRuntimeException(MessageFormat.format(FxFxBundle.getString("CANT OPEN URL {0}"), uri), ex);
    }
  }

  /**
   * Edits/opens a given file via the Desktop utilities.<br>
   * The corresponding application will be chosen according to the mime/file type.
   *
   * @param owner the owner window or node
   * @param file the file
   */
  public void edit(Object owner, File file) {
    run(owner, () -> {
      try {
        LOGGER.info("launching EDIT action for {0}", file);
        Desktop.getDesktop().edit(file);
      }
      catch (UnsupportedOperationException | IOException ex) {
        LOGGER.info("{0} -> trying OPEN action for {1}", ex.getMessage(), file);
        try {
          Desktop.getDesktop().open(file);
        }
        catch (IOException ex2) {
          throw new FxRuntimeException(MessageFormat.format(FxFxBundle.getString("CANT OPEN FILE {0}"), file.getName()), ex2);
        }
      }
    });
  }

  /**
   * Gets the stage for a node.
   *
   * @param node the node
   * @return the stage, null if the node does not belong to a scene or the scene does not belong to a stage.
   */
  public Stage getStage(Node node) {
    Stage stage = null;
    while (node != null) {
      Scene scene = node.getScene();
      if (scene != null) {
        Window window = scene.getWindow();
        if (window instanceof Stage) {
          stage = (Stage) window;
          break;
        }
      }
      node = node.getParent();
    }
    return stage;
  }

  /**
   * Gets the window of an owner object.<br>
   * The owner can be a window, a scene, or a node.<br>
   * Throws {@link FxRuntimeException} if the window cannot be determined.
   *
   * @param owner the owner object
   * @return the window
   */
  public Window getWindow(Object owner) {
    if (owner instanceof Scene scene) {
      owner = scene.getWindow();
    }
    else if (owner instanceof Node node) {
      Scene scene = node.getScene();
      if (scene != null) {
        owner = scene.getWindow();
      }
      else {
        Stage stage = getStage(node);
        if (stage != null) {
          owner = stage;
        }
        else {
          Parent parent = node.getParent();
          while (parent != null) {
            scene = parent.getScene();
            if (scene != null) {
              owner = scene.getWindow();
              break;
            }
            parent = parent.getParent();
          }
        }
      }
    }
    if (owner instanceof Window window) {
      return window;
    }
    throw new FxRuntimeException("cannot determine window of " + owner);
  }

  /**
   * Returns whether the stage is modal.
   *
   * @param stage the stage
   * @return true if effectively modal
   */
  public boolean isModal(Stage stage) {
    return stage != null && switch (stage.getModality()) {
      case APPLICATION_MODAL -> true;
      case WINDOW_MODAL -> stage.getOwner() != null;
      default -> false;
    };
  }

  /**
   * Copies a text to the clipboard.
   *
   * @param text the message to copy
   */
  public void copyToClipboard(String text) {
    ClipboardContent cbc = new ClipboardContent();
    cbc.putString(text);
    Clipboard.getSystemClipboard().setContent(cbc);
  }

  /**
   * Closes the hierarchy of stages from the given node stopping at the given stage.
   *
   * @param node the node
   * @param stopStage the stop stage, null to close the whole application
   */
  public void closeStageHierarchy(Node node, Stage stopStage) {
    Stage stage = getStage(node);
    while (stage != null && stage != stopStage) {
      stage.close();
      Window owner = stage.getOwner();
      if (owner instanceof Stage) {
        stage = (Stage) owner;
      }
      else {
        break;
      }
    }
  }

  /**
   * Dumps the component hierarchy.
   *
   * @param node the node
   * @return the formatted string
   */
  @SuppressWarnings("rawtypes")
  public String dumpComponentHierarchy(Node node) {
    StringBuilder buf = new StringBuilder();
    buf.append(">>> ")
       .append(BundleMonkeyHelper.LOCALE_PREFIX)
       .append(LocaleProvider.getInstance().getLocale())
       .append(BundleMonkeyHelper.LOCALE_POSTFIX)
       .append(" component hierarchy within ");
    Stage stage = getStage(node);
    if (stage != null) {
      buf.append("stage '").append(stage.getTitle()).append("'");
    }
    while (node != null) {
      buf.append("\n    ");
      if (node instanceof FxContainer) {
        FxController controller = ((FxContainer) node).getController();
        if (controller != null) {

          FxControllerService annotation = controller.getClass().getAnnotation(FxControllerService.class);
          String bundleName = null;
          if (annotation != null) {
            if (annotation.resources().isEmpty()) {
              bundleName = controller.getClass().getName();
            }
            else if (!annotation.resources().equals(FxControllerService.RESOURCES_NONE)) {
              bundleName = annotation.resources();
            }
          }
          if (bundleName != null) {
            buf.append(BundleMonkeyHelper.createNavigationString(bundleName));
          }
          if (!controller.getClass().getName().equals(bundleName)) {
            if (bundleName != null) {
              buf.append(' ');
            }
            buf.append(controller.getClass().getSimpleName());
          }
          buf.append(": ");
        }
      }
      else if (node instanceof FxTableView) {
        TableConfiguration configuration = ((FxTableView) node).getConfiguration();
        if (configuration != null) {
          if (configuration.getBaseBundleName() != null) {
            buf.append(BundleMonkeyHelper.createNavigationString(configuration.getBaseBundleName())).append(' ');
          }
          buf.append(configuration.getName()).append(": ");
        }
      }
      else if (node instanceof FxTreeTableView) {
        TableConfiguration configuration = ((FxTreeTableView) node).getConfiguration();
        if (configuration != null) {
          if (configuration.getBaseBundleName() != null) {
            buf.append(BundleMonkeyHelper.createNavigationString(configuration.getBaseBundleName())).append(' ');
          }
          buf.append(configuration.getName()).append(": ");
        }
      }

      buf.append(node).append(" @ [").append(node.getLayoutX()).append("/").append(node.getLayoutY()).append(']');

      if (node instanceof FxComponent fxComponent) {
        Binding binding = fxComponent.getBinding();
        if (binding != null) {
          BindingMember member = binding.getMember();
          if (member != null) {
            buf.append(" bound to '");
            Class<?> type = member.getType();
            if (type != null) {
              String typeName = type.getSimpleName();
              buf.append(typeName);
              Type genericType = member.getGenericType();
              if (genericType != null) {
                Class<?> innerType = ReflectionHelper.extractGenericInnerTypeClass(genericType);
                if (innerType != null) {
                  String innerTypeName = innerType.getSimpleName();
                  if (!innerTypeName.equals(typeName)) {
                    buf.append('<').append(innerTypeName).append('>');
                  }
                }
              }
              buf.append(' ');
            }
            buf.append(member).append("'");
            ValueTranslator<?, ?> valueTranslator = fxComponent.getValueTranslator();
            if (valueTranslator != null) {
              buf.append(" via ").append(valueTranslator.getClass().getSimpleName());
            }
          }
        }
      }

      node = node.getParent();
    }
    buf.append("\n<<<");
    return buf.toString();
  }

  /**
   * Runs a command on the FX application thread and waits for its execution to finish.
   *
   * @param runnable the Runnable whose run method will be executed on the FX application thread
   * @throws FxRuntimeException if the calling thread is the FX application thread
   * @see Platform#runLater(java.lang.Runnable)
   */
  public void runAndWait(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      throw new FxRuntimeException("calling thread is the FX application thread");
    }
    blockingFxExecutor.execute(runnable);
  }

  /**
   * Runs a runnable in a separate thread.<br>
   * Avoids blocking the FX-application thread.<br>
   * If the execution fails, an error dialog is shown.
   *
   * @param owner the owner window or node
   * @param runnable the runnable to execute in the background
   */
  public void run(Object owner, Runnable runnable) {
    javafx.concurrent.Service<Void> service = new javafx.concurrent.Service<>() {

      @Override
      protected Task<Void> createTask() {
        return new Task<>() {

          @Override
          protected Void call() {
            runnable.run();
            return null;
          }
        };
      }
    };

    service.setOnFailed(event -> {
      Throwable exception = event.getSource().getException();
      if (exception != null) {
        LOGGER.warning("FX background execution failed", exception);
        showErrorDialog(owner, exception.getMessage(), exception, null);
      }
    });

    service.start();
  }

  /**
   * Shows a notification popup.<br>
   * The notification is centered above the owner window.
   *
   * @param owner the owner window or node
   * @param popup the popup
   * @param notification the notification node
   * @param onClose the optional runner to execute when the notification is closed/hidden
   */
  public void showNotification(Object owner, Popup popup, Parent notification, Runnable onClose) {
    popup.getContent().add(notification);
    if (onClose != null) {
      // runLater: let the window close completely before running onClose to avoid race conditions
      popup.setOnHidden(event -> Platform.runLater(onClose));
    }
    Window window = getWindow(owner);
    if (window instanceof Stage stage) {
      stage.toFront();
    }
    popup.addEventFilter(WindowEvent.WINDOW_SHOWN, event -> { // filter event is triggered _before_ actually shown
      // center above the owner window
      popup.setAnchorX(window.getX() + (window.getWidth() - popup.getWidth()) / 2.0);
      popup.setAnchorY(window.getY() + (window.getHeight() - popup.getHeight()) / 2.0);
    });
    popup.show(window);
  }

  /**
   * Shows an info dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @param onClose optional runnable invoked when the dialog is closed
   */
  public void showInfoDialog(Object owner, String message, Runnable onClose) {
    Popup popup = FxFactory.getInstance().createNotificationPopup();
    Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                   .type(NotificationBuilder.Type.INFORMATION)
                                   .text(message)
                                   .button(FxFxBundle.getString("OK"), null, false, null)
                                   .hide(popup::hide)
                                   .build();
    showNotification(owner, popup, notification, onClose);
  }

  /**
   * Shows a warning dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @param onClose optional runnable invoked when the dialog is closed
   */
  public void showWarningDialog(Object owner, String message, Runnable onClose) {
    Popup popup = FxFactory.getInstance().createNotificationPopup();
    Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                   .type(NotificationBuilder.Type.WARNING)
                                   .text(message)
                                   .button(FxFxBundle.getString("OK"), null, false, null)
                                   .hide(popup::hide)
                                   .build();
    showNotification(owner, popup, notification, onClose);
  }

  /**
   * Shows a question dialog.<br>
   * To avoid blocking the event queue (see {@link javafx.scene.control.Dialog#showAndWait()}),
   * the implementation is <em>not</em> based on {@link javafx.scene.control.Alert} and the
   * method returns immediately. The result is then provided via a {@link Consumer}.<br>
   * As a convenience, typing the key "0" is interpreted as "no" and "1" as "yes".
   * <p>
   * Note: if you need to wait for a decision of the user (i.e., no async use), you can do that from another
   * than the FX-thread by using a {@link org.tentackle.misc.BlockingHolder} like so:
   * <pre>
   *      BlockingHolder&lt;Boolean&gt; answer = new BlockingHolder&lt;&gt;();
   *      Platform.runLater(() -> Fx.question(..., answer);
   *      if (answer.get()) {
   *        ...
   *      }
   *      else {
   *        ...
   *      }
   * </pre>
   *
   * @param owner the owner window or node
   * @param message the message
   * @param defaultYes true if yes is the default button
   * @param answer the user's answer with {@link Boolean#TRUE} for YES or {@link Boolean#FALSE} for NO, never null
   */
  public void showQuestionDialog(Object owner, String message, boolean defaultYes, Consumer<Boolean> answer) {
    Holder<Boolean> result = new Holder<>(Boolean.FALSE);   // ESC or hiding w/o pressing a button -> NO
    Popup popup = FxFactory.getInstance().createNotificationPopup();
    Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                   .type(NotificationBuilder.Type.QUESTION)
                                   .text(message)
                                   .button(FxFxBundle.getString("NO"), null, !defaultYes, null)
                                   .button(FxFxBundle.getString("YES"), null, defaultYes, () -> result.accept(Boolean.TRUE))
                                   .hide(popup::hide)
                                   .build();
    popup.addEventFilter(KeyEvent.KEY_TYPED, event -> {
      if ("0".equals(event.getCharacter())) {
        event.consume();
        popup.hide();
      }
      else if ("1".equals(event.getCharacter())) {
        event.consume();
        result.accept(Boolean.TRUE);
        popup.hide();
      }
    });
    // trigger the result consumer when the popup is closed
    showNotification(owner, popup, notification, () -> answer.accept(result.get()));
  }

  /**
   * Shows an error dialog.
   *
   * @param owner the owner window or node
   * @param message the message
   * @param t optional throwable
   * @param onClose optional runnable invoked when the dialog is closed
   */
  public void showErrorDialog(Object owner, String message, Throwable t, Runnable onClose) {
    String details;
    if (t != null) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      t.printStackTrace(pw);
      details = sw.toString();
    }
    else {
      details = null;
    }

    Popup popup = FxFactory.getInstance().createNotificationPopup();
    Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                   .type(NotificationBuilder.Type.ERROR)
                                   .text(message)
                                   .details(details)
                                   .button(FxFxBundle.getString("OK"), null, false, null)
                                   .hide(popup::hide).build();
    try {
      showNotification(owner, popup, notification, onClose);
    }
    catch (RuntimeException rx) {
      if (details != null) {
        // log the stacktrace in case we cannot show the dialog
        LOGGER.severe(details);
      }
      throw rx;
    }
  }

  /**
   * Shows an error popup for a component.
   *
   * @param component the component
   * @return the popup window (null if no errormessage in the component)
   */
  public PopupWindow showErrorPopup(ErrorPopupSupported component) {
    PopupWindow popup = null;
    String errorMessage = component.getError();
    if (!StringHelper.isAllWhitespace(errorMessage)) {
      Note note = Fx.create(Note.class);
      note.setPosition(Note.Position.RIGHT);
      note.setType(Note.Type.ERROR);
      note.setText(errorMessage);
      note.show((Node) component);
      popup = note;
    }
    return popup;
  }

  /**
   * Shows an info popup for a component.
   *
   * @param component the component
   * @return the popup window (null if no info-message in the component)
   */
  public PopupWindow showInfoPopup(InfoPopupSupported component) {
    PopupWindow popup = null;
    String infoMessage = component.getInfo();
    if (!StringHelper.isAllWhitespace(infoMessage)) {
      Note note = Fx.create(Note.class);
      note.setPosition(Note.Position.RIGHT);
      note.setType(Note.Type.INFO);
      note.setText(infoMessage);
      note.show((Node) component);
      popup = note;
    }
    return popup;
  }

  /**
   * Creates an interactive error from a validation result.
   *
   * @param validationResult the validation result
   * @param validationMappers the validation mappers
   * @param binder the binder
   * @return the interactive error
   */
  public InteractiveError createInteractiveError(ValidationResult validationResult, NavigableSet<ValidationMapper> validationMappers, Binder binder) {
    return InteractiveErrorFactory.getInstance().createInteractiveError(
        validationMappers, binder, validationResult);
  }

  /**
   * Creates interactive errors from validation results.
   *
   * @param validationResults the validation results
   * @param validationMappers the validation mappers
   * @param binder the binder
   * @return the interactive errors
   */
  public List<InteractiveError> createInteractiveErrors(List<ValidationResult> validationResults, NavigableSet<ValidationMapper> validationMappers, Binder binder) {
    List<InteractiveError> errors = new ArrayList<>();
    for (ValidationResult validationResult: validationResults) {
      errors.add(createInteractiveError(validationResult, validationMappers, binder));
    }
    return errors;
  }

  /**
   * Shows the validation warnings and errors in a dialog and marks the {@link FxControl}s related to those errors.<br>
   * Errors and warnings are shown in error or info dialogs.
   *
   * @param owner the owner window or node
   * @param ex the validation exception
   * @param validationMappers the validation mappers
   * @param binder the binder
   * @return true if there were errors, false if warnings only
   */
  public boolean showValidationResults(Object owner, ValidationFailedException ex, NavigableSet<ValidationMapper> validationMappers, Binder binder) {
    StringBuilder warnings = new StringBuilder();
    StringBuilder errors = new StringBuilder();
    List<InteractiveError> errorList = createInteractiveErrors(ex.getResults(), validationMappers, binder);
    for (InteractiveError error : errorList) {
      if (error.isWarning()) {
        if (!warnings.isEmpty()) {
          warnings.append('\n');
        }
        warnings.append(error.getText());
      }
      else {
        if (!errors.isEmpty()) {
          errors.append('\n');
        }
        errors.append(error.getText());
      }
    }

    if (!warnings.isEmpty() && !errors.isEmpty()) {
      errors.append("\n\n").append(warnings);
      warnings.setLength(0);
    }

    if (!errors.isEmpty()) {
      showErrorDialog(owner, errors.toString(), null, () -> {
        for (InteractiveError error : errorList) {
          if (error.getControl() instanceof FxComponent component) {
            if (error.isWarning()) {
              component.setInfo(error.getText());
            }
            else {
              component.setError(error.getText());
            }
          }
        }
      });
    }
    else if (!warnings.isEmpty()) {
      showInfoDialog(owner, warnings.toString(), () -> {
        for (InteractiveError error : errorList) {
          if (error.getControl() instanceof FxComponent component) {
            component.setInfo(error.getText());
          }
        }
      });
    }

    return !errors.isEmpty();
  }

  /**
   * Adds focus handling to sync with the model.
   *
   * @param control the fx control
   */
  public void setupFocusHandling(Control control) {
    if (control instanceof FxComponent component) {
      control.focusedProperty().addListener((observable, oldValue, newValue) -> {
        if (newValue) {
          FxUtilities.getInstance().focusGained(component);
          if (component.getError() == null) {
            component.showInfoPopup();
          }
          component.showErrorPopup();
        }
        else {
          FxUtilities.getInstance().focusLost(component);
          component.hideInfoPopup();
          component.hideErrorPopup();
        }
      });
      control.hoverProperty().addListener((observable, oldValue, newValue) -> {
        if (!control.isFocused()) {
          if (newValue) {
            if (component.getError() == null) {
              component.showInfoPopup();
            }
            component.showErrorPopup();
          }
          else {
            component.hideInfoPopup();
            component.hideErrorPopup();
          }
        }
      });
    }
  }

  /**
   * Performs all necessary operations on an FxComponent when it gained the focus.
   *
   * @param component the fx component
   */
  public void focusGained(FxComponent component) {
    if (component.getError() == null) {  // don't override the view if there is a parsing error
      component.updateView();
      if (component.isChangeable() && component instanceof FxTextComponent) {
        // By default, FX auto-selects a field if on re-focus.
        // This cannot be configured and is hard-wired (for whatever reason).
        // In TT, this is configurable via the autoSelect feature.
        // in FX components are auto-selected if the field is focused by TAB (or ENTER in TT).
        // If by mouse, the cursor is positioned where the mouse points to and nothing is selected.
        // For numeric fields, however, this behavior leads to unexpected data entry.
        Platform.runLater(((FxTextComponent) component)::autoSelect);
      }
    }
  }

  /**
   * Performs all necessary operations on an FxComponent when it lost the focus.
   *
   * @param component the fx component
   */
  public void focusLost(FxComponent component) {
    component.updateModel();    // some components will do nothing here and...
    // ... isModelUpdate() will return false.
    if (component.isModelUpdated() && component.getError() == null) {    // no conversion error
      // update view again to match the converted value
      component.updateView();   // this will clear modelUpdated
      ValueTranslator<?, ?> translator = component.getValueTranslator();
      if (translator != null && translator.needsToModelTwice()) {
        component.updateModel();
      }
    }
  }

  /**
   * Remaps certain keys.
   *
   * @param control the fx control
   */
  public void remapKeys(Control control) {

    List<KeyEvent> mappedEvents = new ArrayList<>();

    control.addEventFilter(KeyEvent.ANY, new EventHandler<>() {
      @Override
      public void handle(KeyEvent event) {
        if (mappedEvents.remove(event)) {
          return;
        }

        if (KeyCode.ENTER == event.getCode()) {
          KeyEvent newEvent = remap(event, KeyCode.TAB);
          mappedEvents.add(newEvent);
          event.consume();
          Event.fireEvent(event.getTarget(), newEvent);
        }
      }

      private KeyEvent remap(KeyEvent event, KeyCode code) {
        KeyEvent newEvent = new KeyEvent(
            event.getEventType(),
            event.getCharacter(),
            event.getText(),
            code,
            event.isShiftDown(),
            event.isControlDown(),
            event.isAltDown(),
            event.isMetaDown());

        return newEvent.copyFor(event.getSource(), event.getTarget());
      }
    });
  }

  /**
   * Filters certain keys for special features.
   *
   * @param control the fx control
   */
  public void filterKeys(Control control) {
    if (control instanceof FxComponent comp) {
      control.addEventFilter(KeyEvent.ANY, event -> {
        if (!event.isAltDown() && !event.isMetaDown() && !event.isShiftDown()) {
          if (event.isControlDown()) {
            if (event.getCode() == KeyCode.Z) {
              if (comp.isViewModified() && comp.isSavedViewObjectValid()) {
                if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                  // Ctrl-Z restores the saved view value (usually the persisted values in the database)
                  Object viewObject = comp.getViewObject();
                  comp.setViewObject(comp.getSavedViewObject());
                  // With Ctrl-Z and Ctrl-Y the user can switch between the old persisted value and the new input value
                  comp.getDelegate().setLastViewObject(viewObject);
                }
                event.consume();
              }
            }
            else if (event.getCode() == KeyCode.Y) {
              Object viewObject = comp.getDelegate().getLastViewObject();
              if (viewObject != null) {
                if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                  // Ctrl-Y brings back the last set view value (the value when the user entered the field)
                  comp.setViewObject(viewObject);
                }
                event.consume();
              }
            }
          }
          else {    // !controlDown
            if (event.getCode() == KeyCode.F1) {
              if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                comp.showHelp();
              }
              event.consume();
            }
          }
        }
      });
    }
  }

  /**
   * Registers event filters for windows.
   *
   * @param window the window
   */
  public void registerWindowEventFilters(Window window) {

    window.addEventFilter(MouseEvent.ANY, event -> {
      if (event.isShiftDown() && event.isControlDown() && !event.isMetaDown() && !event.isAltDown() &&
          event.isPopupTrigger()) {
        event.consume();    // ctrl + shift + right mouse button should not trigger anything at all
        PickResult pickResult = event.getPickResult();
        if (pickResult != null) {
          Node node = pickResult.getIntersectedNode();
          if (node != null) {
            String componentHierarchy = dumpComponentHierarchy(node);
            LOGGER.info(() -> "\n" + componentHierarchy);
            BundleMonkeyHelper.navigateTo(componentHierarchy);
          }
        }
      }
    });
  }

  /**
   * Recursively expands all tree items while checking for recursion loops.<br>
   * The default implementation in TreeViewBehavior and TreeTableViewBehavior is not only in a private API
   * but cannot be accessed from the applications at all. The default implementation is dangerous since
   * it does not check for recursion loops. We replace it by mapping the event to this utility method.
   *
   * @param treeItem the item to start expansion
   * @param <T> the item's value type
   */
  public <T> void expandAll(TreeItem<T> treeItem) {
    if (treeItem != null && !treeItem.isLeaf()) {
      if (!isValueInParentPath(treeItem)) {
        treeItem.setExpanded(true);
        ObservableList<TreeItem<T>> children = treeItem.getChildren();
        if (children != null) {
          for (TreeItem<T> childItem : children) {
            expandAll(childItem);
          }
        }
      }
    }
  }

  /**
   * Checks whether one of the parents already contains the item's value.
   *
   * @param treeItem the tree item to check
   * @param <T> the value's type
   * @return true if value is in the path
   */
  public <T> boolean isValueInParentPath(TreeItem<T> treeItem) {
    boolean inPath = false;
    T value = treeItem.getValue();
    if (value != null) {
      TreeItem<T> parentItem = treeItem.getParent();
      while (parentItem != null) {
        if (value.equals(parentItem.getValue())) {
          inPath = true;
          break;
        }
        parentItem = parentItem.getParent();
      }
    }
    return inPath;
  }

  /**
   * Recursively collapses all tree items.
   *
   * @param treeItem the item to start
   * @param <T> the item's value type
   */
  public <T> void collapseAll(TreeItem<T> treeItem) {
    if (treeItem != null && treeItem.isExpanded()) {
      treeItem.setExpanded(false);
      ObservableList<TreeItem<T>> children = treeItem.getChildren();
      if (children != null) {
        for (TreeItem<T> childItem : children) {
          collapseAll(childItem);
        }
      }
    }
  }

  /**
   * Recursively collapses all tree items.
   *
   * @param treeItems the list of items to collapse
   * @param <T> the item's value type
   * @see #collapseAll(TreeItem)
   */
  public <T> void collapseAll(Collection<TreeItem<T>> treeItems) {
    if (treeItems != null) {
      for (TreeItem<T> treeItem : treeItems) {
        collapseAll(treeItem);
      }
    }
  }


  /**
   * Gets the root source of a list.<br>
   * The items of a table view are usually wrapped in a chain of transformation lists,
   * for example a {@link javafx.collections.transformation.FilteredList} within a
   * {@link javafx.collections.transformation.SortedList}. Transformation lists are immutable,
   * so modifications must be applied to the root source.
   *
   * @param list the list; may be a transformation list
   * @param <E> the element type
   * @return the root source, the list itself if it is no transformation list
   */
  @SuppressWarnings("unchecked")
  public <E> ObservableList<E> getRootSource(ObservableList<E> list) {
    while (list instanceof TransformationList) {
      list = (ObservableList<E>) ((TransformationList<E, ?>) list).getSource();
    }
    return list;
  }

  /**
   * Maps a view index to the index within the root source.
   *
   * @param list the list; may be a transformation list
   * @param viewIndex the index within the given list
   * @param <E> the element type
   * @return the index within the root source, -1 if the element is not in the source
   * @see #getRootSource(ObservableList)
   */
  @SuppressWarnings("unchecked")
  public <E> int getSourceIndex(ObservableList<E> list, int viewIndex) {
    int index = viewIndex;
    while (list instanceof TransformationList<E, ?> transformationList && index >= 0) {
      index = transformationList.getSourceIndex(index);
      list = (ObservableList<E>) transformationList.getSource();
    }
    return index;
  }

  /**
   * Replaces an element addressed by a view index in the root source.
   *
   * @param list the list; may be a transformation list
   * @param viewIndex the index within the given list
   * @param element the new element
   * @param <E> the element type
   * @see #getSourceIndex(ObservableList, int)
   */
  public <E> void setSourceElement(ObservableList<E> list, int viewIndex, E element) {
    int sourceIndex = getSourceIndex(list, viewIndex);
    if (sourceIndex >= 0) {
      getRootSource(list).set(sourceIndex, element);
    }
  }

  /**
   * Prints a node.<br>
   * The user selects the printer, and the node is scaled down if too large for the paper.
   *
   * @param node the node to print
   */
  public void print(Node node) {
    PrinterJob job = PrinterJob.createPrinterJob();
    if (job != null && job.showPrintDialog(getStage(node))) {
      Scale scale = getScale(node, job);
      node.getTransforms().add(scale);
      boolean success = job.printPage(node);
      node.getTransforms().remove(scale);
      if (success) {
        job.endJob();
      }
    }
  }

  /**
   * Gets a binding option from an option string.
   *
   * @param options the binding option string
   * @param key the option key
   * @return the option, null if no such option
   */
  public String getBindingOption(String options, String key) {
    String option = null;
    StringTokenizer stok = new StringTokenizer(options.toUpperCase(Locale.ROOT), ",");
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken().trim();
      if (token.startsWith(key)) {
        option = token;
        break;
      }
    }
    return option;
  }

  /**
   * Applies the bindable options to a text component.
   *
   * @param comp the component
   * @param member the binding member
   * @param options the options from the @{@link Bindable} annotation
   */
  public void applyBindingOptions(FxTextComponent comp, BindingMember member, String options) {
    applyBindingOptions(comp, null, member, options);
  }

  /**
   * Applies the bindable options to a table column.
   *
   * @param columnConfiguration the column configuration
   * @param member the binding member
   * @param options the options from the @{@link Bindable} annotation
   */
  @SuppressWarnings("rawtypes")
  public void applyBindingOptions(TableColumnConfiguration columnConfiguration, BindingMember member, String options) {
    applyBindingOptions(null, columnConfiguration, member, options);
  }

  /**
   * Resizes the width of all columns of a table to fit the displayed content.<br>
   * Unlike the "double-click on column separator"-feature this method computes
   * the column widths according to the content currently displayed. For tables
   * with a large number of rows, this is much more efficient than scanning the whole table.
   *
   * @param table the table view
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void resizeColumnsToFitContent(TableView<?> table) {
    TableViewSkin<?> tableSkin = (TableViewSkin<?>) table.getSkin();
    VirtualFlow<?> virtualFlow = (VirtualFlow<?>) tableSkin.getChildren().get(1);
    IndexedCell<?> iCell = virtualFlow.getFirstVisibleCell();
    if (iCell != null) {
      int first = iCell.getIndex();
      iCell = virtualFlow.getLastVisibleCell();
      if (iCell != null) {
        int last = iCell.getIndex();
        for (TableColumn column: table.getColumns()) {
          if (column.isVisible()) {
            Callback cellFactory = column.getCellFactory();
            if (cellFactory != null) {
              TableCell cell = (TableCell) cellFactory.call(column);
              if (cell != null) {
                Font cellFont = ThemeUtilities.getInstance().getDefaultFont();
                // BOLD may not work if the header is styled with CSS, but that's ok for default configs
                Font headerFont = Font.font(cellFont.getFamily(), FontWeight.BOLD, cellFont.getSize());
                cell.getProperties().put("deferToParentPrefWidth", Boolean.TRUE); // see TableCellSkinBase
                double maxWidth = 20;   // don't go below this so that the column remains visible
                // apply header text first
                cell.updateTableColumn(column);
                cell.updateTableView(table);
                cell.setText(column.getText());
                // never set the column's graphic on the measuring cell: a node has only one parent,
                // and the header's graphic (a filter button, for example) would be moved to this cell!
                cell.setGraphic(null);
                cell.setFont(headerFont);
                maxWidth = maxColumnWidth(maxWidth, cell, tableSkin) + headerGraphicWidth(column.getGraphic());
                cell.setFont(cellFont);
                for (int row=first; row <= last; row++) {
                  cell.updateIndex(row);
                  maxWidth = maxColumnWidth(maxWidth, cell, tableSkin);
                }
                maxWidth += ThemeUtilities.getInstance().getAutoAdjustPadding();
                table.resizeColumn(column, maxWidth - column.getWidth());
              }
            }
          }
        }
      }
    }
  }

  /**
   * Resizes the width of all columns of a tree table to fit the displayed content.<br>
   * Unlike the "double-click on column separator"-feature this method computes
   * the column widths according to the content currently displayed. For tables
   * with a large number of rows, this is much more efficient than scanning the whole table.
   *
   * @param table the tree table view
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void resizeColumnsToFitContent(TreeTableView<?> table) {
    TreeTableViewSkin<?> tableSkin = (TreeTableViewSkin<?>) table.getSkin();
    VirtualFlow<?> virtualFlow = (VirtualFlow<?>) tableSkin.getChildren().get(1);
    IndexedCell<?> iCell = virtualFlow.getFirstVisibleCell();
    if (iCell != null) {
      int first = iCell.getIndex();
      iCell = virtualFlow.getLastVisibleCell();
      if (iCell != null) {
        int last = iCell.getIndex();
        boolean firstVisibleColumn = true;
        for (TreeTableColumn column: table.getColumns()) {
          if (column.isVisible()) {
            Callback cellFactory = column.getCellFactory();
            if (cellFactory != null) {
              TreeTableCell cell = (TreeTableCell) cellFactory.call(column);
              if (cell != null) {
                Font cellFont = ThemeUtilities.getInstance().getDefaultFont();
                // BOLD may not work if the header is styled with CSS, but that's ok for default configs
                Font headerFont = Font.font(cellFont.getFamily(), FontWeight.BOLD, cellFont.getSize());
                cell.getProperties().put("deferToParentPrefWidth", Boolean.TRUE); // see TableCellSkinBase
                double maxWidth = 20;   // don't go below this so that the column remains visible
                // apply header text first
                cell.updateTableColumn(column);
                cell.updateTreeTableView(table);
                cell.setText(column.getText());
                // never set the column's graphic on the measuring cell: a node has only one parent,
                // and the header's graphic (a filter button, for example) would be moved to this cell!
                cell.setGraphic(null);
                cell.setFont(headerFont);
                maxWidth = maxColumnWidth(maxWidth, cell, tableSkin) + headerGraphicWidth(column.getGraphic());
                cell.setFont(cellFont);
                for (int row=first; row <= last; row++) {
                  cell.updateIndex(row);
                  maxWidth = maxColumnWidth(maxWidth, cell, tableSkin);
                }
                if (firstVisibleColumn) {
                  maxWidth += 50;   // extra space for the navigation icon
                  firstVisibleColumn = false;
                }
                maxWidth += ThemeUtilities.getInstance().getAutoAdjustPadding();
                table.resizeColumn(column, maxWidth - column.getWidth());
              }
            }
          }
        }
      }
    }
  }

  /**
   * Delivers a lis of all showing stages.
   *
   * @return the stages
   */
  public ObservableList<Stage> getAllShowingStages() {
    ObservableList<Stage> stages = FXCollections.observableArrayList();
    Window.getWindows().forEach(w -> {
      if (w instanceof Stage) {
        stages.add((Stage) w);
      }
    });
    return stages;
  }

  /**
   * Calculates the location of a window so that it will be centered on the screen.
   *
   * @param window the window
   * @return the location (top left corner)
   */
  public Point2D determineCenteredLocation(Window window)  {
    Rectangle2D screenSize = Screen.getPrimary().getVisualBounds();
    // align sizes (for the computation below)
    double windowWidth = window.getWidth();
    double windowHeight = window.getHeight();
    if (window.getWidth() > screenSize.getWidth())  {
      windowWidth = screenSize.getWidth();
    }
    if (windowHeight > screenSize.getHeight())  {
      windowHeight = screenSize.getHeight();
    }
    return new Point2D((screenSize.getWidth()  - windowWidth) / 2.0,
                       (screenSize.getHeight() - windowHeight) / 2.0);
  }

  /**
   * Calculates the position of a stage on the screen so that
   * it is being display in an optimal manner.
   *
   * @param stage the stage to be positioned on the screen
   * @return the location
   */
  public Point2D determinePreferredStageLocation(Stage stage)  {

    Point2D location = new Point2D(stage.getX(), stage.getY());

    if (location.getX() == 0.0 && location.getY() == 0.0) {   // if no position yet
      // place in the middle of the owner if possible
      Window owner = stage.getOwner();
      if (owner != null) {
        location = new Point2D(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2.0,
                               owner.getY() + (owner.getHeight() - stage.getHeight()) / 2.0);
      }
      else  {
        // not much we can do: center it
        location = determineCenteredLocation(stage);
      }
    }

    return determineAlignedStageLocation(stage, location);
  }

  /**
   * Calculates the location of a stage so that it
   * is completely visible on the screen, using a "free" spot.
   *
   * @param stage the current stage
   * @param location the desired (not necessarily current!) location
   * @return the aligned location
   */
  public Point2D determineAlignedStageLocation(Stage stage, Point2D location) {

    Rectangle2D screenSize = Screen.getPrimary().getVisualBounds();

    double maxWidth  = screenSize.getWidth() - screenSize.getWidth() / XY_STEP_DIVISOR;
    double maxHeight = screenSize.getHeight() - screenSize.getHeight() / XY_STEP_DIVISOR;
    double minX      = screenSize.getWidth() / XY_STEP_DIVISOR;
    double minY      = screenSize.getHeight() / XY_STEP_DIVISOR;

    if (location.getX() + stage.getWidth() > maxWidth) {
      location = new Point2D(maxWidth - stage.getWidth(), location.getY());
    }
    if (location.getX() < 0.0) {
      location = new Point2D(0.0, location.getY());
    }
    else if (location.getX() < minX && location.getX() + stage.getWidth() < maxWidth) {
      location = new Point2D(minX, location.getY());
    }

    if (location.getY() + stage.getHeight() > maxHeight) {
      location = new Point2D(location.getX(), maxHeight - stage.getHeight());
    }
    if (location.getY() < 0.0) {
      location = new Point2D(location.getX(), 0.0);
    }
    else if (location.getY() < minY && location.getY() + stage.getHeight() < maxHeight) {
      location = new Point2D(location.getX(), minY);
    }

    return determineFreeStageLocation(stage, location);
  }

  /**
   * Gets the virtual flow for a given control.
   *
   * @param control the control
   * @return the virtual flow, null if no virtual flow associated with the control
   */
  @SuppressWarnings("rawtypes")
  public VirtualFlow getVirtualFlow(Skinnable control) {
    VirtualFlow flow = null;
    Skin skin = control.getSkin();
    if (skin instanceof SkinBase) {
      ObservableList children = ((SkinBase) skin).getChildren();
      if (children != null) {
        for (Object child : children) {
          if (child instanceof VirtualFlow) {
            flow = (VirtualFlow) child;
            break;
          }
        }
      }
    }
    return flow;
  }

  /**
   * Computes the row number to scrollTo to position the given row in the middle of the viewport.
   *
   * @param control the control, usually a table- or tree-view
   * @param row the row to position in the middle of the viewport, if possible
   * @param numRow the total number of rows
   * @return the row number to pass to the scrollTo method
   */
  @SuppressWarnings("rawtypes")
  public int computeScrollToCentered(Skinnable control, int row, int numRow) {
    VirtualFlow flow = getVirtualFlow(control);
    if (flow != null) {
      IndexedCell firstVisibleCell = flow.getFirstVisibleCell();
      IndexedCell lastVisibleCell = flow.getLastVisibleCell();
      if (firstVisibleCell != null && lastVisibleCell != null) {
        int viewSize = lastVisibleCell.getIndex() - firstVisibleCell.getIndex() + 1;
        row -= viewSize / 2;
        if (row + viewSize > numRow) {
          row = numRow - viewSize;
        }
        if (row < 0) {
          row = 0;
        }
      }
    }
    return row;
  }

  /**
   * Creates an auto-completion controller for a text component.
   *
   * @param component the component
   * @return the auto-completion
   */
  public AutoCompletionController createAutoCompletion(TextInputControl component, Function<String, List<List<SubString>>> suggestionGenerator) {
    return new AutoCompletionController(component, suggestionGenerator);
  }


  /**
   * Applies a single binding option to a text component.
   *
   * @param comp the component
   * @param member the binding member
   * @param option the option
   * @return true if option known and applied, false if an unknown option
   */
  protected boolean applyBindingOption(FxTextComponent comp, BindingMember member, String option) {

    boolean processed = false;

    if (Constants.BIND_UC.equals(option)) {
      comp.setCaseConversion(CaseConversion.UPPER_CASE);
      processed = true;
    }
    else if (Constants.BIND_LC.equals(option)) {
      comp.setCaseConversion(CaseConversion.LOWER_CASE);
      processed = true;
    }
    else if (Constants.BIND_AUTOSELECT.equals(option)) {
      comp.setAutoSelect(true);
      processed = true;
    }
    else if (("-" + Constants.BIND_AUTOSELECT).equals(option)) {
      comp.setAutoSelect(false);
      processed = true;
    }
    else if (Constants.BIND_UTC.equals(option)) {
      comp.setUTC(true);
      processed = true;
    }
    else if (("-" + Constants.BIND_UTC).equals(option)) {
      comp.setUTC(false);
      processed = true;
    }
    else if (Constants.BIND_UNSIGNED.equals(option)) {
      comp.setUnsigned(true);
      processed = true;
    }
    else if (("-" + Constants.BIND_UNSIGNED).equals(option)) {
      comp.setUnsigned(false);
      processed = true;
    }
    else if (Constants.BIND_DIGITS.equals(option)) {
      comp.setValidChars(Constants.DIGITS);
      processed = true;
    }
    else if (Constants.BIND_STRIP_NONE.equals(option)) {
      comp.setStripMode(StripMode.NONE);
      processed = true;
    }
    else if (Constants.BIND_STRIP_LEFT.equals(option)) {
      comp.setStripMode(StripMode.LEFT);
      processed = true;
    }
    else if (Constants.BIND_STRIP_RIGHT.equals(option)) {
      comp.setStripMode(StripMode.RIGHT);
      processed = true;
    }
    else if (Constants.BIND_STRIP_BOTH.equals(option)) {
      comp.setStripMode(StripMode.BOTH);
      processed = true;
    }
    else if (option.startsWith(Constants.BIND_COLS + "=")) {
      try {
        comp.setColumns(Integer.parseInt(option.substring(Constants.BIND_COLS.length() + 1)));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_COLS + " @Bindable option \"" +
                                   option + "\") in " + member, ex);
      }
    }
    else if (option.startsWith(Constants.BIND_MAXCOLS + "=")) {
      try {
        comp.setMaxColumns(Integer.parseInt(option.substring(Constants.BIND_MAXCOLS.length() + 1)));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_MAXCOLS + " @Bindable option \"" +
                                   option + "\") in " + member, ex);
      }
    }
    else if (option.startsWith(Constants.BIND_SCALE + "=")) {
      try {
        comp.setScale(Integer.parseInt(option.substring(Constants.BIND_SCALE.length() + 1)));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_SCALE + " @Bindable option \"" +
                                   option + "\") in " + member, ex);
      }
    }
    else if (option.startsWith(Constants.BIND_LINES + "=")) {
      if (comp instanceof TextArea) {
        try {
          ((TextArea) comp).setPrefRowCount(Integer.parseInt(option.substring(Constants.BIND_LINES.length() + 1)));
          processed = true;
        }
        catch (NumberFormatException ex) {
          throw new BindingException("invalid " + Constants.BIND_LINES + " @Bindable option \"" +
                                     option + "\") in " + member, ex);
        }
      }
      else {
        throw new BindingException(Constants.BIND_LINES + " @Bindable option \"" +
                                   option + "\") in " + member + " not applicable to " + comp.getClass().getName());
      }
    }
    else if (option.startsWith(Constants.BIND_FILLER + "=")) {
      try {
        String value = option.substring(Constants.BIND_FILLER.length() + 1);
        if (value.isBlank()) {
          comp.setFiller(' ');    // default
        }
        else {
          if (value.charAt(0) == '\'' && value.length() == 3 && value.charAt(2) == '\'') {
            comp.setFiller(value.charAt(1));
          }
          else {
            int fillCode = Integer.decode(value);
            comp.setFiller((char) fillCode);
          }
          processed = true;
        }
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_FILLER + " @Bindable option \"" +
                                   option + "\") in " + member, ex);
      }
    }

    return processed;
  }

  /**
   * Processes an option for a table binding.
   *
   * @param columnConfiguration the column config
   * @param member the binding member
   * @param option the option
   * @return true if option known and processed, false if an unknown option
   */
  @SuppressWarnings("rawtypes")
  protected boolean applyBindingOption(TableColumnConfiguration columnConfiguration, BindingMember member, String option) {

    boolean processed = false;

    if (Constants.BIND_UC.equals(option)) {
      columnConfiguration.setCaseConversion(Boolean.TRUE);
      processed = true;
    }
    else if (Constants.BIND_LC.equals(option)) {
      columnConfiguration.setCaseConversion(Boolean.FALSE);
      processed = true;
    }
    else if (Constants.BIND_AUTOSELECT.equals(option)) {
      columnConfiguration.setAutoSelect(Boolean.TRUE);
      processed = true;
    }
    else if (("-" + Constants.BIND_AUTOSELECT).equals(option)) {
      columnConfiguration.setAutoSelect(Boolean.FALSE);
      processed = true;
    }
    else if (Constants.BIND_UNSIGNED.equals(option)) {
      columnConfiguration.setUnsigned(true);
      processed = true;
    }
    else if (("-" + Constants.BIND_UNSIGNED).equals(option)) {
      columnConfiguration.setUnsigned(false);
      processed = true;
    }
    else if (Constants.BIND_DIGITS.equals(option)) {
      columnConfiguration.setValidChars(Constants.DIGITS);
      processed = true;
    }
    else if (Constants.BIND_STRIP_NONE.equals(option) ||
             Constants.BIND_STRIP_LEFT.equals(option) ||
             Constants.BIND_STRIP_RIGHT.equals(option) ||
             Constants.BIND_STRIP_BOTH.equals(option)) {
      // ignored for tables
      processed = true;
    }
    else if (option.startsWith(Constants.BIND_MAXCOLS + "=")) {
      try {
        columnConfiguration.setMaxColumns(Integer.parseInt(option.substring(Constants.BIND_MAXCOLS.length() + 1)));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_MAXCOLS + " @Bindable option \"" +
                                   option + "\") in " + member, ex);
      }
    }
    else if (option.startsWith(Constants.BIND_SCALE + "=")) {
      try {
        columnConfiguration.setScale(Integer.parseInt(option.substring(Constants.BIND_SCALE.length() + 1)));
        processed = true;
      }
      catch (NumberFormatException ex) {
        throw new BindingException("invalid " + Constants.BIND_SCALE + " @Bindable option \"" +
                                   option + "\") in " + member, ex);
      }
    }
    else if (option.startsWith(Constants.BIND_COLS + "=") ||
             option.startsWith(Constants.BIND_LINES + "=") ||
             option.startsWith(Constants.BIND_FILLER + "=")) {
      // COLS=, LINES= and FILLER= are not applicable to table cells: just ignore
      processed = true;
    }

    return processed;
  }


  /**
   * Updates the maximum column width.
   *
   * @param maxWidth the current max width
   * @param cell the cell
   * @param tableSkin the skin
   * @return the updated width
   */
  @SuppressWarnings("rawtypes")
  private double maxColumnWidth(double maxWidth, TableCell cell, TableViewSkin<?> tableSkin) {
    if ((cell.getText() != null && !cell.getText().isEmpty()) || cell.getGraphic() != null) {
      tableSkin.getChildren().add(cell);
      cell.applyCss();
      maxWidth = Math.max(maxWidth, cell.prefWidth(-1));
      tableSkin.getChildren().remove(cell);
    }
    return maxWidth;
  }

  /**
   * Updates the maximum column width.
   *
   * @param maxWidth the current max width
   * @param cell the cell
   * @param treeTableSkin the skin
   * @return the updated width
   */
  @SuppressWarnings("rawtypes")
  private double maxColumnWidth(double maxWidth, TreeTableCell cell, TreeTableViewSkin<?> treeTableSkin) {
    if ((cell.getText() != null && !cell.getText().isEmpty()) || cell.getGraphic() != null) {
      treeTableSkin.getChildren().add(cell);
      cell.applyCss();
      maxWidth = Math.max(maxWidth, cell.prefWidth(-1));
      treeTableSkin.getChildren().remove(cell);
    }
    return maxWidth;
  }

  /**
   * Determines the width a column header's graphic contributes to the column width.<br>
   * The graphic itself must not be added to the measuring cell, since a node has only one parent
   * and the graphic would be removed from the header.
   *
   * @param graphic the header's graphic, null if none
   * @return the width to add to the column width, 0.0 if no graphic
   */
  private double headerGraphicWidth(Node graphic) {
    return graphic == null ? 0.0 : graphic.prefWidth(-1) + HEADER_GRAPHIC_TEXT_GAP;
  }


  private static final double XY_STEP_DIVISOR = 32.0;
  private static final int MAX_LOCATION_LOOP = 4;

  /**
   * Determines a location for a stage that does not overlap with stages belonging to the same owner.
   *
   * @param stage the stage
   * @param startLocation the start location
   * @return the free location
   */
  private Point2D determineFreeStageLocation(Stage stage, Point2D startLocation)  {

    // get screen-size
    Rectangle2D screenSize = Screen.getPrimary().getVisualBounds();

    final double stepX = screenSize.getWidth() / XY_STEP_DIVISOR;
    final double stepY = screenSize.getHeight() / XY_STEP_DIVISOR;

    // compute the center of the window
    double x = startLocation.getX() + stage.getWidth() / 2.0;
    double y = startLocation.getY() + stage.getHeight() / 2.0;

    // initial diff-stepping
    double dx;
    double dy;

    if (x > screenSize.getWidth() / 2.0) {
      dx = -stepX;
    }
    else {
      dx = stepX;
    }

    if (y > screenSize.getHeight() / 2.0) {
      dy = -stepY;
    }
    else {
      dy = stepY;
    }

    for (int loop=0; loop < MAX_LOCATION_LOOP; loop++)  {

      boolean abort = false;
      Point2D location = startLocation;

      while (!abort && isStageOverlapping(stage, location, dx, dy))  {
        location = location.add(dx, dy);
        if (location.getX() < 0.0) {
          location = new Point2D(0.0, location.getY());
          abort = true;
        }
        if (location.getX() + stage.getWidth() > screenSize.getWidth())  {
          location = new Point2D(screenSize.getWidth() - stage.getWidth(), location.getY());
          abort = true;
        }
        if (location.getY() < 0.0) {
          location = new Point2D(location.getX(), 0.0);
          abort = true;
        }
        if (location.getY() + stage.getHeight() > screenSize.getHeight())  {
          location = new Point2D(location.getX(), screenSize.getHeight() - stage.getHeight());
          abort = true;
        }
      }

      if (!abort) {
        // !isStageOverlapping
        return location;
      }

      // try another direction clockwise
      if (dx > 0.0 && dy > 0.0) {
        dx = -stepX;
      }
      else if (dx < 0.0 && dy > 0.0) {
        dy = -stepY;
      }
      else if (dx < 0.0 && dy < 0.0) {
        dx = stepX;
      }
      else if (dx > 0 && dy < 0) {
        dy = stepY;
      }
    }

    return startLocation;
  }

  /**
   * Checks if a stage would overlay any other stage that belongs to the same owner.
   *
   * @param stage the stage
   * @param location the requested location for this stage
   * @param dx maximum overlapping width
   * @param dy maximum overlapping height
   * @return true if overlapping, false if on free space
   */
  private boolean isStageOverlapping(Stage stage, Point2D location, double dx, double dy)  {
    Window owner = stage.getOwner();
    if (dx < 0.0) {
      dx = -dx;
    }
    if (dy < 0.0) {
      dy = -dy;
    }
    for (Stage w: getAllShowingStages())  {
      Window o = w.getOwner();
      if (w != stage && o == owner &&
          ((location.getX() <= w.getX() + dx &&
            location.getX() + stage.getWidth() + dx >= w.getX() + w.getWidth()) ||
           (location.getY() <= w.getY() + dy &&
            location.getY() + stage.getHeight() + dy >= w.getY() + w.getHeight())))  {
        return true;
      }
    }
    return false;
  }

  /**
   * Computes the scale factor to fit the node onto the paper.
   *
   * @param node the node to print
   * @param job the printer job
   * @return the scale factor
   */
  private static Scale getScale(Node node, PrinterJob job) {
    PageLayout pageLayout = job.getJobSettings().getPageLayout();
    double scaleX = 1.0;
    if (pageLayout.getPrintableWidth() < node.getBoundsInParent().getWidth()) {
      scaleX = pageLayout.getPrintableWidth() / node.getBoundsInParent().getWidth();
    }
    double scaleY = 1.0;
    if (pageLayout.getPrintableHeight() < node.getBoundsInParent().getHeight()) {
      scaleY = pageLayout.getPrintableHeight() / node.getBoundsInParent().getHeight();
    }
    double scaleXY = Double.min(scaleX, scaleY);
    return new Scale(scaleXY, scaleXY);
  }

  /**
   * Applies the binding options to a text component.
   *
   * @param comp the text component, null if not a text component
   * @param columnConfiguration the table column configuration, null if not a table column
   * @param member the binding member
   * @param options the binding options
   */
  @SuppressWarnings("rawtypes")
  private void applyBindingOptions(FxTextComponent comp, TableColumnConfiguration columnConfiguration, BindingMember member, String options) {
    if (options != null) {
      StringTokenizer stok = new StringTokenizer(options.toUpperCase(Locale.ROOT), ",");
      while (stok.hasMoreTokens()) {
        String token = stok.nextToken().trim();
        boolean processed;
        if (comp != null) {
          processed = applyBindingOption(comp, member, token);
        }
        else if (columnConfiguration != null) {
          processed = applyBindingOption(columnConfiguration, member, token);
        }
        else {
          throw new BindingException("unsupported @Bindable target " + member);
        }
        if (!processed) {
          throw new BindingException("unsupported @Bindable option \"" + token + "\") in " + member);
        }
      }
    }
  }

}
