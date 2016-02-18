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


package org.tentackle.swing;

import java.awt.AWTKeyStroke;
import java.awt.ActiveEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.PaintEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.BackingStoreException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.EventListenerList;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import org.tentackle.bind.Binding;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.swing.bind.DefaultFormBindingFactory;
import org.tentackle.swing.bind.FormBindingFactory;
import org.tentackle.swing.bind.FormComponentBinding;


interface FormUtilities$Singleton {
  FormUtilities INSTANCE = ServiceFactory.createService(FormUtilities.class, FormUtilities.class);
}


/**
 * Utilities for the UI layer.
 *
 * @author harald
 */
@Service(FormUtilities.class)    // defaults to self
public class FormUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static FormUtilities getInstance() {
    return FormUtilities$Singleton.INSTANCE;
  }



  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(FormUtilities.class);

  /**
   * Instance counter.
   */
  private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();

  /**
   * one instance of the focus traversal policy for SwingForms-Containers
   */
  private final FormFocusTraversalPolicy defaultFocusTraversalPolicy = new FormFocusTraversalPolicy();

  /**
   * Set containing all windows (dialogs and frames, visible or iconified)
   */
  private final Set<Window> windows = new HashSet<>();

  /**
   * The EventListenerList assigned to this FormUtilities instance.
   */
  private EventListenerList windowListeners;

  /**
   * List of registered modal dialogs.
   * Modal Dialogs in order of creation
   */
  private List<Dialog> modalDialogs;

  /**
   * The UI version used to track LookAndFeel changes.
   */
  private int uiVersion;

  /**
   * The help url.
   * prefix for online help, e.g.: http://localhost/manual/index.html
   */
  private String helpURL;

  /**
   * Tentacle uses an extended EventQueue.
   * Reference to the FormEventQueue
   */
  private FormEventQueue eventQueue;           //

  /**
   * true if a request is pending
   */
  private boolean requestFocusLaterPending;

  /**
   * The binding factory.
   */
  private FormBindingFactory bindingFactory;

  /**
   * This is only true if activated (see activate()).
   * Otherwise we assume "tool mode", for example within a GUI builder.
   */
  private boolean activated;



  /**
   * C'tor for this class.
   * This class is supposed to have only one single instance.
   * @see #getInstance()
   */
  public FormUtilities() {
    if (INSTANCE_COUNT.incrementAndGet() > 1) {
      throw new RuntimeException("Singleton! Only one single instance allowed.");
    }

    // create the window-Set
    windowListeners = new EventListenerList();
    modalDialogs = new ArrayList<>();

    try {
      // with fallback to DefaultFBF because of tools like Netbeans Matisse where META-INF/services does not work
      bindingFactory = ServiceFactory.createService(FormBindingFactory.class, DefaultFormBindingFactory.class);
    }
    catch (Exception ex) {
      throw new GUIRuntimeException("cannot create form binding factory", ex);
    }
  }


  /**
   * Activates the utility features.
   */
  public void activate() {
    if (!activated) {
      GUIExceptionHandler.install(false);    // install GUI Exception handler if not yet done by app

      // register the multiline tooltip
      try {
        String multiLineToolTipUIClassName = "org.tentackle.swing.plaf.MultiLineToolTipUI";    // NOI18N
        UIManager.put("ToolTipUI", multiLineToolTipUIClassName);  // NOI18N
        UIManager.put(multiLineToolTipUIClassName, Class.forName(multiLineToolTipUIClassName));
      }
      catch (ClassNotFoundException cnfe) {
        throw new GUIRuntimeException("MultiLine ToolTip UI class not found", cnfe);
      }

      activated = true;
    }
  }


  /**
   * Creates a text-dump of the component hierarchy.
   *
   * @param comp the deepest component
   * @param me the optional mouse event (necessary for table headers only)
   * @return the dumped hierarchy
   */
  public String dumpComponentHierarchy(Component comp, MouseEvent me) {

    boolean showing = true;
    StringBuilder buf = new StringBuilder();

    while (comp != null) {

      if (showing && !comp.isShowing()) {
        showing = false;
      }

      buf.append(comp.getClass().getName());

      Point location = showing ? comp.getLocationOnScreen() : new Point();
      buf.append("@[");
      buf.append(location.x);
      buf.append('/');
      buf.append(location.y);
      buf.append(',');
      buf.append(comp.getWidth());
      buf.append('/');
      buf.append(comp.getHeight());
      buf.append(']');

      String name = comp.getName();
      if (name != null) {
        buf.append(" (");
        buf.append(name);
        buf.append(')');
      }

      String text = null;
      if (comp instanceof JTextComponent) {
        text = ((JTextComponent) comp).getText();
      }
      else if (comp instanceof JLabel) {
        text = ((JLabel) comp).getText();
      }
      else if (comp instanceof FormTable) {
        text = ((FormTable) comp).getPreferencesName();
      }
      else if (comp instanceof JTableHeader && me != null) {
        JTableHeader header = (JTableHeader) comp;
        MouseEvent me2 = SwingUtilities.convertMouseEvent((Component) me.getSource(), me, header);
        int columnIndex = header.getColumnModel().getColumnIndexAtX(me2.getX());
        if (columnIndex >= 0) {
          Object headerValue = header.getColumnModel().getColumn(columnIndex).getHeaderValue();
          if (headerValue != null) {
            text = headerValue.toString();
          }
        }
      }
      if (text != null) {
        buf.append(" = \"");
        buf.append(text);
        buf.append('"');
      }

      buf.append('\n');

      // walk up
      comp = comp.getParent();
    }

    return buf.toString();
  }


  /**
   * Creates a new event queue.
   * @return the created queue
   */
  protected FormEventQueue createEventQueue() {
    return new FormEventQueue();
  }


  /**
   * Gets the eventqueue.<br>
   * The method installs the FormEventQueue if not yet done.
   * This is the preferred method to get the event queue in an application.
   *
   * @return the form event queue
   */
  public synchronized FormEventQueue getEventQueue()  {
    // install special EventQueue:
    if (eventQueue == null) {
      eventQueue = createEventQueue();
      Toolkit.getDefaultToolkit().getSystemEventQueue().push(eventQueue);
    }
    return eventQueue;
  }



  /**
   * Gets the binding factory (singleton).
   *
   * @return the binding factory
   */
  public FormBindingFactory getBindingFactory() {
    return bindingFactory;
  }



  /**
   * Waits for the event queue to become empty.<br>
   * This method must not be invoked from the GUI thread!
   * The method waits until the q is empty.
   */
  public void waitForEmptyEventQueue() {
    EventQueue q = getEventQueue();
    if (EventQueue.isDispatchThread()) {
      throw new Error("waitForEmptyEventQueue() invoked from within dispatch thread!"); // NOI18N
    }
    boolean queueEmpty = false;
    while (!queueEmpty) {
      // post a new event with lowest priority
      EmptyEvent e = new EmptyEvent();
      q.postEvent(e);
      synchronized(e) {
        while (!e.isDispatched()) {
          try {
            e.wait();   // wait for being dispatched
          }
          catch (InterruptedException ie) {
            // ignore
          }
        }
        // event has been dispatched
        queueEmpty = e.isEventQueueEmpty();
      }
    }
  }

  /**
   * for windows with autoClose feature enabled
   */
  private long autoClose;                      // global autoclose for all FormWindows, default is off.
  private AutoCloseThread autoCloseThread;     // only started if at least one window enables autoclose

  private class AutoCloseThread extends Thread {
    @Override
    public void run() {
      try {
        boolean keepRunning;
        do  {
          sleep(1000);
          keepRunning = false;
          synchronized (windows)  {
            for (Window w: windows) {
              if (w.isVisible() &&
                  w instanceof FormWindow && ((FormWindow)w).isAutoCloseable())  {
                keepRunning = true;
                final FormWindow fw = (FormWindow)w;
                if (fw.checkAutoClose())  {
                  EventQueue.invokeLater(() -> {
                    ((Window)fw).dispose();
                    LOGGER.fine("autoclosing {0}", fw);
                  });
                }
              }
            }
          }
        } while (keepRunning);
      }
      catch (InterruptedException e) {
        /* ignore */
      }
      // terminate
      synchronized(windows) {   // sync with addWindow()
        LOGGER.fine("autoclose-thread stopped");
        autoCloseThread = null;
      }
    }
  }


  /**
   * Sets the global autoclose feature for all newly created Windows.
   *
   * @param ms timeout in milliseconds. Default is 0.
   */
  public void setAutoClose(long ms) {
    autoClose = ms;    // for all newly created dialogs
  }


  /**
   * Gets the global autoclose feature for all newly created Windows.
   * @return the timeout in milliseconds. Default is 0.
   */
  public long getAutoClose() {
    return autoClose;
  }

  /**
   * Brings the current modal dialogs toFront.
   */
  public void modalToFront() {
    int size = modalDialogs.size();
    if (size > 0) {
      final Dialog d = modalDialogs.get(size-1);
      // works with KDE only if KDE-focus is set to "focus under mouse".
      // works with windows perfectly.
      // works with gnome and sawfish but not with metacity.

      /**
       * get it painted last to avoid loops
       */
      EventQueue.invokeLater(d::toFront);
    }
  }

  /**
   * notified all window-listeners
   */
  private void fireWindowActionPerformed(ActionEvent e)  {
      Object[] lList = windowListeners.getListenerList();
      for (int i = lList.length-2; i>=0; i-=2) {
          if (lList[i] == ActionListener.class) {
              ((ActionListener)lList[i+1]).actionPerformed(e);
          }
      }
  }


  /**
   * Adds window to the set of windows (must be visible or iconified).
   *
   * @param w the window to add
   */
  public void addWindow(Window w) {
    synchronized (windows)  {
      if (windows.add(w)) {
        if (w instanceof Dialog && ((Dialog)w).isModal()) {
          modalDialogs.add((Dialog)w);
        }
        FormWindow fw = w instanceof FormWindow ? (FormWindow)w : null;
        if (fw != null && fw.isAutoCloseable()) {
          // start timer for autoclose for this window
          fw.setTimeOfLastValuesChanged(System.currentTimeMillis());
          // start the autoclose-thread if not running
          if (autoCloseThread == null) {
            autoCloseThread = new AutoCloseThread();
            autoCloseThread.start();
            LOGGER.fine("autoclose-thread started");
          }
        }
        fireWindowActionPerformed(new ActionEvent(w, Event.ACTION_EVENT, "add")); // NOI18N
      }
    }
  }

  /**
   * Removes a window from the set (i.e. window is hidden or closed now)
   * @param w the window to remove
   */
  public void removeWindow(Window w) {
    synchronized (windows)  {
      if (windows.remove(w))  {
        if (w instanceof Dialog && ((Dialog) w).isModal()) {
          modalDialogs.remove(w);
        }
        fireWindowActionPerformed(new ActionEvent(w, Event.ACTION_EVENT, "remove")); // NOI18N
      }
    }
  }


  /**
   * Gets the current windows which are visible or iconified.
   *
   * @return the array of windows
   */
  public Window[] getWindows() {
    synchronized (windows)  {
      Window[] remainingWindows = new Window[0];
      /**
       * for some reason, on some linux-desktops, a disposing/hiding/closing window does
       * not always deliver the event, which leaves the window in the list.
       * So we check again for "isShowing"
       */
      for (Iterator<Window> iter = windows.iterator(); iter.hasNext(); )  {
        Window w = iter.next();
        if (!w.isShowing()) {
          iter.remove();
        }
      }
      return windows.toArray(remainingWindows);
    }
  }


  /**
   * Checks whether a modal dialog is showing.
   *
   * @return true if at least one modal dialog is showing
   */
  public boolean isModalDialogShowing() {
    return !modalDialogs.isEmpty();
  }


  /**
   * Sets the initial uiversion of a {@link FormWindow}.
   *
   * @param formWindow the created window
   */
  public void setUIVersionOfFormWindow(FormWindow formWindow) {
    formWindow.setUIVersion(uiVersion);
  }



  /**
   * Updates the UI of a given window.
   *
   * @param w the window
   */
  public void updateUIofWindow(Window w) {
    if (w instanceof JDialog)  {
      SwingUtilities.updateComponentTreeUI(((JDialog)w).getRootPane());
    }
    if (w instanceof JFrame)  {
      SwingUtilities.updateComponentTreeUI(((JFrame)w).getRootPane());
    }
    if (w instanceof FormWindow)  {
      setUIVersionOfFormWindow((FormWindow) w);
    }
  }



  /**
   * Updates the UI of all registered windows
   */
  public void updateUIofAllWindows () {
    uiVersion++;
    for (Window w: windows) {
      updateUIofWindow(w);
    }
  }



  /**
   * Process a window event (from a FormDialog or FormFrame).
   * Update the list of registered windows.
   *
   * @param e the event to process
   */
  @SuppressWarnings("fallthrough")
  public void processWindowEvent(WindowEvent e)  {

    Window w = e.getWindow();

    switch (e.getID())  {

      case WindowEvent.WINDOW_OPENED:
        // we get this if window is newly opened
      case WindowEvent.WINDOW_ACTIVATED:
        // we get this if window was setVisible(false)
        addWindow(w);
        // fallthrough

      case WindowEvent.WINDOW_DEICONIFIED:
        if (w instanceof FormWindow && ((FormWindow)w).getUIVersion() != uiVersion)  {
          updateUIofWindow(w);
        }
        break;

      case WindowEvent.WINDOW_CLOSING:
        // we get this if user-app did not process this event
      case WindowEvent.WINDOW_CLOSED:
        // we get this if user app dispose()'d the window
        removeWindow(w);
        break;
    }

  }



  /**
   * Adds a listener being notified whenever the state of the
   * current windows-list has changed.
   *
   * @param listener the action listener
   */
  public void addWindowActionListener(ActionListener listener)  {
    windowListeners.add(ActionListener.class, listener);
  }

  /**
   * Removes a listener.
   *
   * @param listener the listener to be removed
   */
  public void removeWindowActionListener(ActionListener listener) {
    windowListeners.remove(ActionListener.class, listener);
  }

  /**
   * Sets the focus-policy for a container.
   * The method does nothing if the given container is not
   * a FormContainer.
   *
   * @param container the container.
   */
  public void setDefaultFocusTraversalPolicy(Container container) {
    if (container instanceof FormContainer) {
      container.setFocusTraversalPolicy(defaultFocusTraversalPolicy);
    }
  }


  /**
   * Recursively walk down and setAutoUpdate.
   *
   * @param c the component
   * @param autoUpdate the autoUpdate flag
   */
  public void setAutoUpdate (Component c, boolean autoUpdate)  {
    if (c instanceof Container) {
      for (Component next : ((Container) c).getComponents()) {
        if (next instanceof FormComponent)  {
          // initialize form field with values
          ((FormComponent)next).setAutoUpdate(autoUpdate);
        }
        else if (next instanceof FormContainer) {
          ((FormContainer)next).setAutoUpdate(autoUpdate);
        }
        else {
          // go down the component tree recursively
          setAutoUpdate(next, autoUpdate);
        }
      }
    }
  }


  /**
   * Notifies all ValueListeners (usually only one!) that the field is
   * going to be displayed and thus needs the data what to display.
   *
   * @param c the component
   * @param listeners the listener array
   */
  public void doFireValueChanged(FormComponent c, Object[] listeners) {
    if (!c.isFireRunning()) {
      c.setFireRunning(true);
      if (listeners != null) {
        ValueEvent evt = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
          if (listeners[i] == ValueListener.class) {
            if (evt == null) {
              evt = new ValueEvent(c, ValueEvent.SET);
            }
            ((ValueListener) listeners[i + 1]).valueChanged(evt);
          }
        }
      }
      Binding binding = c.getBinding();
      if (binding != null) {
        binding.setViewValue(binding.getModelValue());
      }
      c.setFireRunning(false);
    }
  }


  /**
   * Notifies all ValueListeners (usually only one!) that the field contents
   * should be moved to the actual data object.
   *
   * @param c the component
   * @param listeners the listener array
   */
  public void doFireValueEntered(FormComponent c, Object[] listeners) {
    if (!c.isFireRunning()) {
      c.setFireRunning(true);
      Binding binding = c.getBinding();
      if (binding != null) {
        binding.setModelValue(binding.getViewValue());
      }
      if (listeners != null) {
        ValueEvent evt = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
          if (listeners[i] == ValueListener.class) {
            if (evt == null) {
              evt = new ValueEvent(c, ValueEvent.GET);
            }
            ((ValueListener) listeners[i + 1]).valueEntered(evt);
          }
        }
      }
      c.setFireRunning(false);
      // trigger possible value changed to container
      c.triggerValueChanged();
    }
  }



  /**
   * Runs the validation if there is a binding and there
   * are validation annotations.
   *
   * @param c the component
   */
  public void doValidate(FormComponent c) {
    if (c.isChangeable()) {
      Binding binding = c.getBinding();
      if (binding != null) {
        // run the optional validation (and invoke all validation listeners)
        binding.validate();
      }
    }
  }


  /**
   * Recursively walk down and fireValueChanged().
   *
   * @param c the component
   */
  public void setFormValue (Component c)  {
    if (c instanceof Container) {
      for (Component next : ((Container) c).getComponents()) {
        if (next instanceof FormComponent)  {
          // initialize form field with values
          ((FormComponent)next).fireValueChanged();
        }
        else if (next instanceof FormContainer) {
          ((FormContainer)next).setFormValues();
        }
        else {
          // go down the component tree recursively
          setFormValue (next);
        }
      }
    }
  }


  /**
   * Recursively walk down and fireValueChanged(),
   * but only fields that have *NOT* been changed by the user.<br>
   * Nice to mask out unchanged fields.
   * @param c the component
   */
  public void setFormValueKeepChanged (Component c)  {
    if (c instanceof Container) {
      for (Component next : ((Container)c).getComponents()) {
        if (next instanceof FormComponent)  {
          if (!((FormComponent)next).isValueChanged())  {
            // initialize form field with values
            ((FormComponent)next).fireValueChanged();
          }
        }
        else if (next instanceof FormContainer) {
          ((FormContainer)next).setFormValuesKeepChanged();
        }
        else {
          // go down the component tree recursively
          setFormValueKeepChanged(next);
        }
      }
    }
  }


  /**
   * Recursively walk down and fireValueEntered().
   * @param c the component
   */
  public void getFormValue (Component c)  {
    if (c instanceof Container) {
      for (Component next : ((Container)c).getComponents()) {
        if (next instanceof FormComponent)  {
          // initialize form field with values
          ((FormComponent)next).fireValueEntered();
        }
        else if (next instanceof FormContainer) {
          ((FormContainer)next).getFormValues();
        }
        else {
          // go down the component tree recursively
          getFormValue (next);
        }
      }
    }
  }



  /**
   * Recursively walk down and saveValue().
   *
   * @param c the component
   */
  public void saveValue (Component c)  {
    if (c instanceof Container) {
      for (Component next : ((Container)c).getComponents()) {
        if (next instanceof FormComponent)  {
          // initialize form field with values
          ((FormComponent)next).saveValue();
        }
        else if (next instanceof FormContainer) {
          ((FormContainer)next).saveValues();
        }
        else {
          // go down the component tree recursively
          saveValue (next);
        }
      }
    }
  }



  /**
   * Recursively walk down and check for value changed.
   *
   * @param c the component
   * @param triggerValueChangedEnabledOnly true if check only components with triggerValueChangedEnabled
   * @return true if data has changed in some component
   */
  public boolean isValueChanged(Component c, boolean triggerValueChangedEnabledOnly)  {
    if (c instanceof Container) {
      for (Component next : ((Container)c).getComponents()) {
        if (next instanceof FormComponent) {
          if ((!triggerValueChangedEnabledOnly || ((FormComponent) next).isTriggerValueChangedEnabled()) &&
                  ((FormComponent) next).isValueChanged())  {
            return true;
          }
        }
        else if (next instanceof FormContainer) {
          if (((FormContainer) next).areValuesChanged())  {
            return true;
          }
        }
        else if (next instanceof FormTable) {
          if (((FormTable) next).isDataChanged()) {
            return true;
          }
          // check editing component
          Component editor = ((FormTable)next).getEditorComponent();
          if (editor instanceof FormComponent) {
            if ((!triggerValueChangedEnabledOnly || ((FormComponent) editor).isTriggerValueChangedEnabled()) &&
                    ((FormComponent) editor).isValueChanged())  {
              return true;
            }
          }
        }
        else {
          // go down the component tree recursively
          if (isValueChanged(next, triggerValueChangedEnabledOnly)) {
            return true;
          }
        }
      }
    }
    return false;
  }



  /**
   * Recursively walk up and trigger value changed in FormContainers.
   *
   * @param c the component
   */
  public void triggerValueChanged (Component c)  {
    while (c != null) {
      if (c instanceof FormContainer) {
        if (((FormContainer) c).isTriggerValuesChangedEnabled()) {
          ((FormContainer) c).triggerValuesChanged();
        }
        else  {
          return;   // stop walking up
        }
      }
      c = c.getParent();
    }
  }



  /**
   * Recursively walk down and setChangeable().
   *
   * @param c the component
   * @param changeable true if changeable
   */
  public void setChangeable(Component c, boolean changeable) {
    if (c instanceof Container) {
      for (Component next : ((Container) c).getComponents()) {
        // order FormContainer, FormChangeable is important cause of FormComponentPanel
        if (next instanceof FormContainer) {
          // if special FormContainer: allow method to be overridden
          ((FormContainer) next).setChangeable(changeable);
          if (next instanceof FormChangeableComponent) {
            ((FormChangeableComponent) next).updateAllChangeable(changeable);
          }
        }
        else if (next instanceof FormChangeableComponent) {
          ((FormChangeableComponent) next).updateAllChangeable(changeable);
        }
        else {
          // go down the component tree recursively for standard elements
          setChangeable(next, changeable);
        }
      }
    }
  }


  /**
   * Recursively walk down and setBackground().
   *
   * @param c the component
   * @param background the background color
   */
  public void setBackground (Component c, Color background)  {
    c.setBackground(background);
    if (c instanceof Container) {
      for (Component component : ((Container) c).getComponents()) {
        // go down the component tree recursively for standard elements
        setBackground(component, background);
      }
    }
  }


  /**
   * Recursively walk down and setForeground().
   *
   * @param c the component
   * @param foreground the foreground color
   */
  public void setForeground (Component c, Color foreground)  {
    c.setForeground(foreground);
    if (c instanceof Container) {
      for (Component component : ((Container) c).getComponents()) {
        // go down the component tree recursively for standard elements
        setForeground(component, foreground);
      }
    }
  }



  /**
   * Determines the parent-window of a component.<br>
   * Much the same as getTopLevelAncestor in JComponent, this does method
   * not return Applets.
   * @param comp the component
   * @return the parent window, null if none
   */
  public Window getParentWindow(Component comp) {
    while (comp != null && !(comp instanceof Window)) {
      comp = comp.getParent();
    }
    // either comp is null or comp is a Window
    return (Window)comp;
  }


  /**
   * Gets the parent window of the given component.
   * If the parent is not visible, gets the related window.
   * @param comp the component
   * @return the parent or related window, null if neither nor
   */
  public Window getVisibleParentOrRelatedWindow(Component comp) {
    Window w = getParentWindow(comp);
    if (w != null && !w.isVisible() && w instanceof FormWindow) {
      // try to use the related window instead
      FormWindow fw = ((FormWindow)w).getRelatedWindow();
      if (fw instanceof Window) {
        w = (Window)fw;
      }
    }
    return w;
  }



  /**
   * Determines whether parent window a modal dialog.
   *
   * @param comp the component
   * @return true if parent window is a modal dialog, false if no parent, not
   * a dialog or not modal
   */
  public boolean isParentWindowModal(Component comp) {
    Window w = getParentWindow(comp);
    return w instanceof Dialog && ((Dialog)w).isModal();
  }


  /**
   * Packs the window containing the given component.
   *
   * @param comp the component
   * @return true if packed, false if no window
   */
  public boolean packParentWindow(Component comp)  {
    Window w;
    if (comp instanceof FormComponent)  {
      w = ((FormComponent)comp).getParentWindow();
    }
    else if (comp instanceof FormContainer) {
      w = ((FormContainer)comp).getParentWindow();
    }
    else  {
      w = getParentWindow(comp);
    }
    if (w != null)  {
      w.pack();
      return true;
    }
    return false;
  }


  /**
   * Recursively walk down and invalidate the parentInfo.
   *
   * @param c the component
   */
  public void invalidateParentInfo(Component c) {
    if (c instanceof Container) {
      for (Component next : ((Container) c).getComponents()) {
        if (next instanceof FormComponent)  {
          // initialize form field with values
          ((FormComponent)next).invalidateParentInfo();
        }
        else if (next instanceof FormContainer) {
          ((FormContainer)next).invalidateParentInfo();
        }
        else {
          // go down the component tree recursively
          invalidateParentInfo(next);
        }
      }
    }
  }


  /**
   * Gets all subcontainers.
   *
   * @param container the parent container
   * @return the sub containers, never null
   */
  public Collection<FormContainer> getSubContainer(FormContainer container) {
    Set<FormContainer> containers = new HashSet<>();
    if (container != null) {
      findSubContainer(containers, container);
    }
    return containers;
  }

  private void findSubContainer(Set<FormContainer> containers, FormContainer container) {
    for (Component component: ((Container)container).getComponents()) {
      if (component instanceof FormContainer) {
        if (containers.add((FormContainer)component)) { // avoid reference loops
          findSubContainer(containers, (FormContainer)component);
        }
      }
    }
  }


  /**
   * Gets the binding for a given component in the given container and
   * all its sub-containers.
   * <p>
   * The method differs from {@link org.tentackle.bind.Binder#getBinding}
   * because the latter only finds bindings that were explicitly bound to the container.
   * This method, however, also finds bindings bound on sub containers.
   *
   * @param container the parent container
   * @param component the component
   * @return the binding, null if component is not bound
   */
  public FormComponentBinding getBinding(FormContainer container, FormComponent component) {
    for (FormContainer subContainer: getSubContainer(container)) {
      FormComponentBinding binding = subContainer.getBinder().getBinding(component);
      if (binding != null) {
        return binding;
      }
    }
    return null;
  }

  /**
   * Gets the binding for a given binding path in the given container and
   * all its sub-containers.
   * <p>
   * The method differs from {@link org.tentackle.bind.Binder#getBinding}
   * because the latter only finds bindings that were explicitly bound to the container.
   * This method, however, also finds bindings bound on sub containers.
   *
   * @param container the parent container
   * @param bindingPath the opject's binding path
   * @return the binding, null if binding path is not bound
   */
  public FormComponentBinding getBinding(FormContainer container, String bindingPath) {
    for (FormContainer subContainer: getSubContainer(container)) {
      FormComponentBinding binding = subContainer.getBinder().getBinding(bindingPath);
      if (binding != null) {
        return binding;
      }
    }
    return null;
  }


  /**
   * Requests focus by EventQueue.invokeLater().
   * Also sets a flag that such a request is pending.
   * @param c the component
   */
  public void requestFocusLater(final Component c) {
    requestFocusLaterPending = true;
    EventQueue.invokeLater(c::requestFocusInWindow);
  }



  /**
   * Sets the wait-cursor.<br>
   * Determines the parent or related window and applies the cursor.
   *
   * @param comp the related component
   */
  public void setWaitCursor(Component comp) {
    Window w = getVisibleParentOrRelatedWindow(comp);
    Cursor c = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
    if (w instanceof RootPaneContainer) {
      Component glassPane = ((RootPaneContainer) w).getGlassPane();
      if (glassPane != null) {
        glassPane.setCursor(c);
        // make it visible if not visible yet
        glassPane.setVisible(true);
      }
    }
    if (w != null) {
      // sometimes glassPane-trick doesn't work
      w.setCursor(c);
    }
  }


  /**
   * Sets the default-cursor.<br>
   * Determines the parent or related window and applies the cursor.
   *
   * @param comp the related component
   */
  public void setDefaultCursor(Component comp) {
    Window w = getVisibleParentOrRelatedWindow(comp);
    Cursor c = Cursor.getDefaultCursor();
    if (w instanceof RootPaneContainer) {
      Component glassPane = ((RootPaneContainer) w).getGlassPane();
      if (glassPane != null) {
        glassPane.setCursor(null);
        // leave glasspane visible in case application draws on it
      }
    }
    if (w != null) {
      // sometimes glassPane-trick doesn't work
      w.setCursor(c);
    }
  }




  // there is only one popup at a time (or none)
  private Popup errorPopup;
  private JComponent popupComponent;

  private final ComponentListener popupComponentListener = new ComponentListener() {

    @Override
    public void componentResized(ComponentEvent e) {
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentShown(ComponentEvent e) {
    }

    @Override
    public void componentHidden(ComponentEvent e) {
      hideErrorPopup();
    }
  };


  private final HierarchyListener popupHierarchyListener = (HierarchyEvent e) -> hideErrorPopup();


  /**
   * Hides the currently displayed error popup, if any.
   *
   * @throws IllegalStateException if not invoked from within the dispatch thread
   */
  public void hideErrorPopup() {
    if (EventQueue.isDispatchThread()) {
      if (errorPopup != null) {
        errorPopup.hide();
        errorPopup = null;
      }
      if (popupComponent != null) {
        popupComponent.removeComponentListener(popupComponentListener);
        popupComponent.removeHierarchyListener(popupHierarchyListener);
        popupComponent = null;
      }
    }
    else  {
      throw new IllegalStateException("operation only valid from dispatch-thread");
    }
  }


  /**
   * Shows an error popup for a given component and message.<br>
   * Any currently displayed error popup will be hidden first.
   * There is only one error popup displayed at a time.
   * The method must be invoked from within the event dispatch thread.
   *
   * @param formComponent the component
   * @param message the message, null to hide
   * @throws IllegalStateException if not invoked from within the dispatch thread
   */
  public void showErrorPopup(FormComponent formComponent, String message) {
    hideErrorPopup();
    if (message != null && !message.isEmpty() &&
        formComponent instanceof JComponent && ((JComponent)formComponent).isShowing()) {
      JComponent comp = (JComponent) formComponent;
      ErrorToolTip toolTip = new ErrorToolTip(comp, message);
      Point loc = comp.getLocationOnScreen();
      errorPopup = PopupFactory.getSharedInstance().getPopup(
              comp, toolTip, loc.x + (comp.getWidth() >> 1), loc.y + comp.getHeight() + 2);
      popupComponent = comp;
      popupComponent.addComponentListener(popupComponentListener);
      popupComponent.addHierarchyListener(popupHierarchyListener);
      errorPopup.show();
    }
  }




  /**
   * Calculates the location of a window so that it will be centered on the screen.
   * @param window the window
   * @return the location (top left corner)
   */
  public Point getCenteredLocation(Window window)  {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension windowSize = window.getSize();
    // align sizes (for computation below)
    if (windowSize.width > screenSize.width)  {
      windowSize.width = screenSize.width;
    }
    if (windowSize.height > screenSize.height)  {
      windowSize.height = screenSize.height;
    }
    // center
    return new Point ((screenSize.width  - windowSize.width)  / 2,
                      (screenSize.height - windowSize.height) / 2);
  }


  /**
   * Calculates the position of a window on the screen so that
   * it is being display in an optimal manner.
   *
   * @param window the window to be positioned on the screen
   * @param owner the window to which the window will be related to
   * @return the location
   */
  public Point getPreferredLocation(Window window, Window owner)  {

    Point location;   // the returned top left corner

    // place in the middle of the owner if possibe
    if (owner != null && owner.isShowing()) {   // isShowing cause of SwingUtilities.SharedOwnerFrame
      Dimension windowSize = window.getSize();
      Dimension ownerSize  = owner.getSize();
      location    = owner.getLocation();
      location.x += (ownerSize.width - windowSize.width) / 2;
      location.y += (ownerSize.height - windowSize.height) / 2;
    }
    else  {
      // not much we can do: center it
      location = getCenteredLocation(window);
    }

    return getAlignedLocation(window, location);
  }


  /**
   * Calculates the location of a window so that it
   * is completely visible on the screen, using a "free" spot.
   *
   * @param window the current window
   * @param location the desired (not necessarily current!) location
   * @return the location
   */
  public Point getAlignedLocation(Window window, Point location) {

    Dimension windowSize = window.getSize();
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

    int maxWidth  = screenSize.width * 19 / 20;   // leave 5% off from right/bottom
    int maxHeight = screenSize.height * 19 / 20;
    int minX      = screenSize.width / 20;
    int minY      = screenSize.height / 20;

    if (location.x + windowSize.width > maxWidth) {
      location.x = maxWidth - windowSize.width;
    }
    if (location.x < 0) {
      location.x = 0;
    }
    else if (location.x < minX && location.x + windowSize.width < maxWidth) {
      location.x = minX;
    }

    if (location.y + windowSize.height > maxHeight) {
      location.y = maxHeight - windowSize.height;
    }
    if (location.y < 0) {
      location.y = 0;
    }
    else if (location.y < minY && location.y + windowSize.height < maxHeight) {
      location.y = minY;
    }

    return getFreeLocation(window, location);
  }



  /**
   * steps the window ne/so/sw/se if it would overlay with another.
   */
  private static final int STEP_X = 32;
  private static final int STEP_Y = 24;

  private Point getFreeLocation(Window window, Point startLocation)  {

    // get screensize
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

    // compute the center of the window
    int x = startLocation.x + window.getWidth()/2;
    int y = startLocation.y + window.getHeight()/2;

    // initial diff-stepping
    int dx;
    int dy;

    if (x > screenSize.width/2) {
      dx = -STEP_X;
    }
    else {
      dx = STEP_X;
    }

    if (y > screenSize.height/2) {
      dy = -STEP_Y;
    }
    else {
      dy = STEP_Y;
    }

    for (int loop=0; loop < 4; loop++)  {

      boolean abort = false;
      Point location = new Point(startLocation);

      while (!abort && isWindowOverlaying(window, location, dx, dy))  {
        location.x += dx;
        location.y += dy;
        if (location.x < 0) {
          location.x = 0;
          abort = true;
        }
        if (location.x + window.getWidth() > screenSize.width)  {
          location.x = screenSize.width - window.getWidth();
          abort = true;
        }
        if (location.y < 0) {
          location.y = 0;
          abort = true;
        }
        if (location.y + window.getHeight() > screenSize.height)  {
          location.y = screenSize.height - window.getHeight();
          abort = true;
        }
      }

      if (!abort) {
        // !isWindowOverlaying
        return location;
      }

      // try other direction clockwise
      if (dx > 0 && dy > 0) {
        dx = -STEP_X;
      }
      else if (dx < 0 && dy > 0) {
        dy = -STEP_Y;
      }
      else if (dx < 0 && dy < 0) {
        dx = STEP_X;
      }
      else if (dx > 0 && dy < 0) {
        dy = STEP_Y;
      }
    }

    return startLocation;
  }




  /**
   * checks if window would overlay another that belongs to the same owner
   */
  private boolean isWindowOverlaying(Window window, Point location, int dx, int dy)  {
    Window owner = window.getOwner();
    if (dx < 0) {
      dx = -dx;
    }
    if (dy < 0) {
      dy = -dy;
    }
    for (Window w: windows)  {
      if (w.isShowing()) {
        Window o = w.getOwner();
        if (w != window && o == owner &&
            ((location.x <= w.getX() + dx &&
              location.x + window.getWidth() + dx >= w.getX() + w.getWidth()) ||
             (location.y <= w.getY() + dy &&
              location.y + window.getHeight() + dy >= w.getY() + w.getHeight())))  {
          return true;
        }
      }
    }
    return false;
  }




  /**
   * Registers some default Keyboard Actions for Components.
   * Will also replace some standard actions!
   * @param comp the component
   */
  public void setupDefaultBindings (final JComponent comp)  {

    // clear focus traversal keys to catch key events even for TAB
    // focus traversal is handled via ENTER and TAB.
    HashSet<AWTKeyStroke> set = new HashSet<>();
    comp.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, set);
    set = new HashSet<>();
    comp.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, set);

    // ENTER = to next component (override old action!)
    Action enterAction = new AbstractAction ("focusNextFormComponent") { // NOI18N
      private static final long serialVersionUID = 1L;
      @Override
      public void actionPerformed (ActionEvent e)  {
        if (comp instanceof FormFieldComponent)  {
          ((FormFieldComponent)comp).postActionEvent();
        }
        if (comp instanceof FormTextArea) {
          ((FormTextArea)comp).doSmartEnter();
        }
        else  {
          if (comp instanceof FormComponent)  {
            requestFocusLaterPending = false;   // to detect requests during prepareFocusLost()
            ((FormComponent)comp).prepareFocusLost();
            if (!requestFocusLaterPending)  {
              /**
               * in case the component issued a requestFocusLater(), we should
               * not transfer focus to the next component!
               */
              comp.transferFocus();
            }
          }
          else  {
            comp.transferFocus();
          }
        }
      }
    };
    comp.getActionMap().put(enterAction.getValue(Action.NAME), enterAction);
    comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                           enterAction.getValue(Action.NAME));
    comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
                           enterAction.getValue(Action.NAME));

    // Shift-ENTER = to previous component
    Action enterShiftAction = new AbstractAction ("focusPreviousFormComponent") { // NOI18N
      private static final long serialVersionUID = 1L;
      @Override
      public void actionPerformed (ActionEvent e)  {
        if (comp instanceof FormFieldComponent)  {
          ((FormFieldComponent)comp).postActionEvent();
        }
        if (comp instanceof FormComponent)  {
          requestFocusLaterPending = false;   // to detect requests during prepareFocusLost()
          ((FormComponent)comp).prepareFocusLost();
          if (!requestFocusLaterPending)  {
            comp.transferFocusBackward();
          }
        }
        else  {
          comp.transferFocusBackward();
        }
      }
    };
    comp.getActionMap().put(enterShiftAction.getValue(Action.NAME), enterShiftAction);
    comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Event.SHIFT_MASK),
                           enterShiftAction.getValue(Action.NAME));
    comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, Event.SHIFT_MASK),
                           enterShiftAction.getValue(Action.NAME));


    if (comp instanceof FormComponent)  {
      // help
      final FormComponent fc = (FormComponent)comp;
      Action helpAction = new AbstractAction ("showHelp") { // NOI18N
        private static final long serialVersionUID = 1L;
        @Override
        public void actionPerformed (ActionEvent e)  {
          fc.showHelp();
        }
      };
      comp.getActionMap().put(helpAction.getValue(Action.NAME), helpAction);
      comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0),
                             helpAction.getValue(Action.NAME));
      comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
                             helpAction.getValue(Action.NAME));
    }

    if (comp instanceof FormFieldComponent) {

      final FormFieldComponent fc = (FormFieldComponent)comp;
      // clear field
      Action clearAllAction = new AbstractAction ("clearText") { // NOI18N
        private static final long serialVersionUID = 1L;
        @Override
        public void actionPerformed (ActionEvent e)  {
          if (fc.isChangeable()) {
            fc.clearText();
          }
        }
      };
      comp.getActionMap().put(clearAllAction.getValue(Action.NAME), clearAllAction);
      comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, Event.SHIFT_MASK),
                             clearAllAction.getValue(Action.NAME));

      // toggle insert/override
      Action toggleInsertAction = new AbstractAction ("toggleInsert") { // NOI18N
        private static final long serialVersionUID = 1L;
        @Override
        public void actionPerformed (ActionEvent e)  {
          fc.setOverwrite(!fc.isOverwrite());
        }
      };
      comp.getActionMap().put(toggleInsertAction.getValue(Action.NAME), toggleInsertAction);
      comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0),
                             toggleInsertAction.getValue(Action.NAME));


      // home field (leftmost position)
      Action caretLeftAction = new AbstractAction ("caretLeft") { // NOI18N
        private static final long serialVersionUID = 1L;
        @Override
        public void actionPerformed (ActionEvent e)  {
          fc.setCaretLeft();
        }
      };
      comp.getActionMap().put(caretLeftAction.getValue(Action.NAME), caretLeftAction);
      comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0),
                             caretLeftAction.getValue(Action.NAME));
      comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_BEGIN, 0),
                             caretLeftAction.getValue(Action.NAME));

      // rightmost position
      Action caretRightAction = new AbstractAction ("caretRight") { // NOI18N
        private static final long serialVersionUID = 1L;
        @Override
        public void actionPerformed (ActionEvent e)  {
          fc.setCaretRight();
        }
      };
      comp.getActionMap().put(caretRightAction.getValue(Action.NAME), caretRightAction);
      comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0),
                             caretRightAction.getValue(Action.NAME));

      // override existing action
      Action upLeftAction = new AbstractAction ("upLeft") { // NOI18N
        private static final long serialVersionUID = 1L;
        @Override
        public void actionPerformed (ActionEvent e)  {
          fc.upLeft();
        }
      };
      comp.getActionMap().put(upLeftAction.getValue(Action.NAME), upLeftAction);
      comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
                             upLeftAction.getValue(Action.NAME));

      // override existing action
      Action downRightAction = new AbstractAction ("downRight") { // NOI18N
        private static final long serialVersionUID = 1L;
        @Override
        public void actionPerformed (ActionEvent e)  {
          fc.downRight();
        }
      };
      comp.getActionMap().put(downRightAction.getValue(Action.NAME), downRightAction);
      comp.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
                             downRightAction.getValue(Action.NAME));
    }
  }



  /**
   * Saves the preferences of a component.<br>
   * Use it whenever there is no table in it (FormTable provide setting too)
   * or some other scrolling regions need to be preset.
   * The method scans for all scrolling regions contained in component
   * and stores their sizes.
   *
   * @param comp is the component (usually a panel, srolling area or window)
   * @param prefName is the preferences name
   * @param system is true if store to system-preferences, else store in userprefs
   */
  public void savePreferredSizes(Component comp, String prefName, boolean system) {
    int[] counter = new int[] { 0 };
    try {
      PersistedPreferences prefs = system ?
              PersistedPreferences.systemRoot().node(prefName) :
              PersistedPreferences.userRoot().node(prefName);
      savePreferredSizes(comp, prefs, counter);
    }
    catch (BackingStoreException | RuntimeException ex)  {
      FormError.showException(SwingSwingBundle.getString("PREFERENCES COULD NOT BE SAVED"), ex);
    }
  }

  private void savePreferredSizes(Component comp, PersistedPreferences prefs, int[] counter) throws BackingStoreException {
    if (comp instanceof JScrollPane)  {
      counter[0]++;
      prefs.putInt("height_" + counter[0] , comp.getHeight()); // NOI18N
      prefs.putInt("width_" + counter[0], comp.getWidth()); // NOI18N
      prefs.flush();
    }
    else if (comp instanceof Container) {
      for (Component component : ((Container) comp).getComponents()) {
        // go down the component tree recursively for standard elements
        savePreferredSizes(component, prefs, counter);
      }
    }
  }



  /**
   * Loads the preferences of a component.<br>
   * Use it whenever there is no table in it (FormTable provide setting too)
   * or some other scrolling regions need to be preset.
   * The method scans for all scrolling regions contained in component
   * and sets their preferred-sizes.
   *
   * @param comp is the component (usually a panel, srolling area or window)
   * @param prefName is the preferences name
   * @param system is true if load from system-preferences, else try userprefs first
   */
  public void loadPreferredSizes(Component comp, String prefName, boolean system) {
    int[] counter = new int[] { 0 };
    try {
      PersistedPreferences sysPrefs  = PersistedPreferences.systemRoot().node(prefName);
      PersistedPreferences userPrefs = system ? null : PersistedPreferences.userRoot().node(prefName);
      loadPreferredSizes(comp, sysPrefs, userPrefs, counter);
    }
    catch (BackingStoreException | RuntimeException ex)  {
      FormError.showException(SwingSwingBundle.getString("PREFERENCES COULD NOT BE LOADED"), ex);
    }
  }

  private void loadPreferredSizes(Component comp, PersistedPreferences sysPrefs, PersistedPreferences userPrefs,
          int[] counter) throws BackingStoreException {
    if (comp instanceof JScrollPane)  {
      counter[0]++;
      String key = "width_" + counter[0]; // NOI18N
      int width = -1;
      if (userPrefs != null)  {
        width = userPrefs.getInt(key,  width);
      }
      if (width == -1)  {
        width = sysPrefs.getInt(key,  width);
      }
      key = "height_" + counter[0]; // NOI18N
      int height = -1;
      if (userPrefs != null)  {
        height = userPrefs.getInt(key,  height);
      }
      if (height == -1)  {
        height = sysPrefs.getInt(key,  height);
      }
      if (width > 0 && height > 0)  {
        comp.setPreferredSize(new Dimension(width, height));
      }
    }
    else if (comp instanceof Container) {
      for (Component component : ((Container) comp).getComponents()) {
        // go down the component tree recursively for standard elements
        loadPreferredSizes(component, sysPrefs, userPrefs, counter);
      }
    }
  }





  /**
   * Installs a menu for setting/retrieving the preferred sizes.
   *
   * @param comp is the component
   * @param prefName is the preferences name
   */
  public void installPreferredSizeMenu(Component comp, String prefName) {
    PreferredSizeMouseListener mouseListener = new PreferredSizeMouseListener(prefName);
    if (comp instanceof JScrollPane)  {
      comp.addMouseListener(mouseListener);
    }
    else if (comp instanceof Container) {
      for (Component component : ((Container) comp).getComponents()) {
        // go down the component tree recursively for standard elements
        installPreferredSizeMenu(component, prefName);
      }
    }
  }


  /**
   * Determines the Preferences-name for a class.<br>
   * The name is built from the classname and a componentname.
   * plus the name of the table. E.g.: "/de/krake/bixworx/common/OpAusziffTableEntry/opAusziffOpTable"
   *
   * @param clazz the class
   * @param compName the name of the component
   * @return the preferences name
   */
  public String getPreferencesName(Class<?> clazz, String compName) {
    return "/" + clazz.getName().replace('.', '/') + "/" + compName;
  }

  /**
   * Sets the global help url prefix.
   * The prefix will be prepended to all help requests.
   *
   * @param aHelpURL the prefix
   */
  public void setHelpURL(String aHelpURL) {
    helpURL = aHelpURL;
  }

  /**
   * Gets the global help url prefix.
   *
   * @return the prefix
   */
  public String getHelpURL() {
    return helpURL;
  }


  /**
   * Opens the online help for a given component.
   *
   * @param comp the component
   */
  public void openHelpURL(Component comp)  {

    String url = null;

    if (comp instanceof FormComponent)  {
      url = ((FormComponent) comp).getHelpURL();
    }
    else if (comp instanceof FormContainer) {
      url = ((FormContainer) comp).getHelpURL();
    }
    else if (comp instanceof FormButton)  {
      url = ((FormButton) comp).getHelpURL();
    }
    else if (comp instanceof FormTable) {
      url = ((FormTable) comp).getHelpURL();
    }

    if (helpURL != null) {
      if (url != null)  {
        url = helpURL + url;
      }
      else  {
        url = helpURL;
      }
    }

    if (url != null)  {
      try {
        FormInfo.show(MessageFormat.format(SwingSwingBundle.getString("OPENING HELP FOR <{0}> ..."), url), true, 5000); // show for 5 seconds
        Desktop.getDesktop().browse(URI.create(url));
      }
      catch (IOException | RuntimeException ex) {
        FormError.showException(MessageFormat.format(SwingSwingBundle.getString("CAN'T OPEN HELP FOR <{0}>"), url), ex);
      }
    }
  }



  // ------------------------------------ Inner classes used by Formhelper instance -------------------------------

  /**
   * A mouse listener to open a popup menu for setting/getting the preferred sizes.
   */
  private class PreferredSizeMouseListener implements MouseListener {

    private final String prefName;

    public PreferredSizeMouseListener(String prefName) {
      this.prefName = prefName;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      /* ignored */
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      /* ignored */
    }

    @Override
    public void mouseExited(MouseEvent e) {
      /* ignored */
    }

    @Override
    public void mousePressed(MouseEvent e) {
      processMouseEvent(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      processMouseEvent(e);
    }


    private void processMouseEvent(MouseEvent evt)  {
      if (evt.isPopupTrigger())  {
        final Component comp = evt.getComponent();
        if (comp != null) {
          // determine topmost window
          final Window window = getParentWindow(comp);
          final Component parent = window == null ? comp : window;
          // build menu
          JPopupMenu menu = new JPopupMenu();
          if (PersistedPreferencesFactory.getInstance().isSystemOnly()) {
            if (!PersistedPreferencesFactory.getInstance().isReadOnly())  {
              JMenuItem saveItem = new JMenuItem(SwingSwingBundle.getString("SAVE SYSTEM PREFERENCES"));
              saveItem.addActionListener((ActionEvent e) -> {
                if (FormQuestion.yesNo(SwingSwingBundle.getString("SAVE SYSTEM PREFERENCES FOR THIS WINDOW?"))) {
                  savePreferredSizes(parent, prefName, true);
                }
              });
              menu.add(saveItem);
            }

            JMenuItem restoreItem = new JMenuItem(SwingSwingBundle.getString("LOAD SYSTEM PREFERENCES"));
            restoreItem.addActionListener((ActionEvent e) -> {
              loadPreferredSizes(parent, prefName, true);
              if (window != null) {
                window.pack();
              }
            });
            menu.add(restoreItem);
          }
          else  {
            if (!PersistedPreferencesFactory.getInstance().isReadOnly())  {
              JMenuItem saveItem = new JMenuItem(SwingSwingBundle.getString("SAVE USER PREFERENCES"));
              saveItem.addActionListener((ActionEvent e) -> {
                if (FormQuestion.yesNo(SwingSwingBundle.getString("SAVE USER PREFERENCES FOR THIS WINDOW?"))) {
                  savePreferredSizes(parent, prefName, false);
                }
              });
              menu.add(saveItem);
            }

            JMenuItem restoreItem = new JMenuItem(SwingSwingBundle.getString("LOAD USER PREFERENCES"));
            restoreItem.addActionListener((ActionEvent e) -> {
              loadPreferredSizes(comp, prefName, false);
              if (window != null) {
                window.pack();
              }
            });
            menu.add(restoreItem);

            JMenuItem restoreSysItem = new JMenuItem(SwingSwingBundle.getString("LOAD SYSTEM PREFERENCES"));
            restoreSysItem.addActionListener((ActionEvent e) -> {
              loadPreferredSizes(parent, prefName, true);
              if (window != null) {
                window.pack();
              }
            });
            menu.add(restoreSysItem);
          }
          menu.show(comp, evt.getX(), evt.getY());
        }
      }
    }
  }

  /**
   * A simple event to detect when the event queue becomes empty.
   * We're using a PaintEvent which has the lowest priority, so we're
   * sure that InvocationEvents (i.e. from invokeLater()) will have been
   * processed as well.
   */
  @SuppressWarnings("serial")
  private class EmptyEvent extends PaintEvent implements ActiveEvent {

    private boolean dispatched = false;
    private boolean queueEmpty = false;

    EmptyEvent() {
      // UPDATE does the trick, see EventQueue.getPriority()
      super(new JLabel(), PaintEvent.UPDATE, new Rectangle());
    }

    boolean isDispatched() {
      synchronized(this) {
        return dispatched;
      }
    }

    boolean isEventQueueEmpty() {
      synchronized(this) {
        return queueEmpty;
      }
    }

    @Override
    public void dispatch() {
      synchronized(this) {
        queueEmpty = getEventQueue().peekEvent() == null;
        dispatched = true;
        notifyAll();
      }
    }
  }

}