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


package org.tentackle.app;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Properties;
import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.tentackle.common.Constants;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.ApplicationException;
import org.tentackle.misc.CommandLine;
import org.tentackle.misc.StringHelper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.LoginFailedException;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.Session;
import org.tentackle.pdo.SessionInfo;
import org.tentackle.pdo.SessionProvider;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.swing.FormError;
import org.tentackle.swing.FormInfo;
import org.tentackle.swing.FormUtilities;
import org.tentackle.swing.FormWindow;
import org.tentackle.swing.GUIExceptionHandler;
import org.tentackle.swing.plaf.PlafUtilities;
import org.tentackle.swing.rdc.DefaultLoginDialog;
import org.tentackle.swing.rdc.LoginDialog;
import org.tentackle.swing.rdc.PdoEditDialogPool;
import org.tentackle.swing.rdc.PdoSearchDialog;
import org.tentackle.swing.rdc.Rdc;



/**
 * Abstract class to handle the application's lifecycle.
 * Tentackle applications should extend this class and invoke {@link #start}.
 * To shutdown gracefully, application should invokd {@link #stop}.
 * At minimum, the method doCreateWindow() must be implemented.
 * <p>
 * The subclass just needs to provide a main-method, for example:
 * <pre>
 * public static void main(String args[]) {
 *   new MyAbstractApplication().start(args);
 * }
 * </pre>
 *
 * @author harald
 */
public abstract class DesktopApplication extends AbstractApplication {

  /**
   * Gets the running desktop application.<br>
   *
   * @return the application
   */
  public static DesktopApplication getDesktopApplication() {
    return (DesktopApplication) getRunningApplication();
  }

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(DesktopApplication.class);

  private static final String DEFAULT_LAF = MetalLookAndFeel.class.getName();


  // runnable to set the LAF
  private static class LafRunnable implements Runnable {

    private final String name;

    private LafRunnable(String name) {
      this.name = name;
    }

    @Override
    public void run() {
      if (name != null && UIManager.getLookAndFeel().getClass().getName().compareTo(name) != 0) {
        try {
          UIManager.setLookAndFeel(name);
          PlafUtilities.getInstance().triggerLookAndFeelUpdated();
          FormUtilities.getInstance().updateUIofAllWindows();
        }
        catch (UnsupportedLookAndFeelException ex) {
          FormError.show(AppSwingRdcBundle.getString("THIS LOOK-AND-FEEL IS NOT SUPPORTED ON THIS COMPUTER"));
        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
          FormError.showException(AppSwingRdcBundle.getString("CHANGING THE LOOK-AND-FEEL FAILED"), ex);
        }
      }
    }
  }



  private final Icon logo;                    // the application's logo icon
  private CommandLine cmdLine;                // command line
  private FormWindow window;                  // the application's window
  private LoginDialog loginDialog;            // the login dialog
  private String sessionPropsName;            // db properties name


  /**
   * Creates a swing desktop application.
   *
   * @param name the application's name
   * @param logo the application's logo icon
   */
  public DesktopApplication(String name, Icon logo) {
    super(name);
    this.logo = logo;
  }


  @Override
  public boolean isServer() {
    return false;
  }


  @Override
  protected void configurePdoTracker() {
    super.configurePdoTracker();
    Session trackerSession = getSession().clone();          // already open
    trackerSession.groupWith(getSession().getSessionId());  // build group
    PdoTracker.getInstance().setSession(trackerSession);
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to create a DomainContext with a thread-local session.
   * <p>
   * In deskop client apps there are 2 threads using their own session:
   * <ol>
   * <li>the AWT thread</li>
   * <li>the PdoTracker thread</li>
   * </ol>
   * By using the thread-local session, PDOs can be used from both threads
   * without having to worry about the correct session.
   *
   * @return the domain context
   */
  @Override
  public DomainContext createDomainContext(Session session) {
    return super.createDomainContext(null);   // thread-local immutable context
  }


  /**
   * Launches the application.
   *
   * @param args the arguments (usually from commandline)
   */
  public void start(String[] args) {

    synchronized (this) {

      cmdLine = new CommandLine(args);
      setProperties(cmdLine.getOptionsAsProperties());

      try {

        LOGGER.fine("register application");
        // make sure that only one application is running at a time
        register();

        LOGGER.fine("initialize application");
        // doInitialize environment
        doInitialize();

        LOGGER.fine("login to backend");
        // connect to database/application server
        if (doLogin() == null) {
          // no connection, doStop immediately
          System.exit(1);
        }

        LOGGER.fine("configure application");
        // configure the application
        doConfigureApplication();

        LOGGER.fine("show application");

        doInitializeGUI();

        final Session session = getSession();

        // show the application's window
        EventQueue.invokeAndWait(() -> {
          try {
            // the eventqueue will use the db as well
            session.makeCurrent();
            // createPdo the application window
            window = doCreateWindow();
            if (StringHelper.isAllWhitespace(window.getTitle())) {
              window.setTitle(getName());
            }
            doShowWindow();
          }
          catch (RuntimeException | ApplicationException e) {
            doStop(2, e);
          }
        });

        LOGGER.fine("waiting for all shown...");
        // wait for application to be shown
        FormUtilities.getInstance().waitForEmptyEventQueue();

        LOGGER.fine("finish startup");
        // finish startup
        doFinishStartup();

      }
      catch (ApplicationException | InterruptedException | InvocationTargetException | RuntimeException ex) {
        // print message to user, if GUI in window, else if headless to console
        FormError.showException(MessageFormat.format(AppSwingRdcBundle.getString("LAUNCHING {0} FAILED"), getName()), ex, false, LOGGER);
        // doStop with error
        doStop(3, ex);
      }
    }
  }


  /**
   * Gracefully terminates the application.
   * Usually invoked from an exit-Button or when window is closed.
   */
  public void stop() {
    try {
      unregister();   // not really necessary cause of System.exit in doStop...
      doStop(0);
    }
    catch (RuntimeException | ApplicationException e) {
      LOGGER.logStacktrace(e);
      // doStop with error
      doStop(4, e);
    }
  }


  /**
   * Gets the command line.
   *
   * @return the command line
   */
  public CommandLine getCommandLine() {
    return cmdLine;
  }


  /**
   * Gets the logo.
   *
   * @return the logo
   */
  public Icon getLogo() {
    return logo;
  }


  /**
   * Gets the application's window.
   *
   * @return the window, null if not yet defined.
   */
  public FormWindow getWindow() {
    return window;
  }


  /**
   * Sets the look and feel.
   *
   * @param name the name of the look and feel
   */
  public void setLookAndFeel(String name) {
    if (EventQueue.isDispatchThread()) {
      new LafRunnable(name).run();
    }
    else  {
      try {
        EventQueue.invokeAndWait(new LafRunnable(name));
      }
      catch (InterruptedException | InvocationTargetException | RuntimeException ex) {
        FormError.showException(AppSwingRdcBundle.getString("CHANGING THE LOOK-AND-FEEL FAILED"), ex);
      }
    }
  }


  /**
   * Changes the look and feel.
   * This method should be invoked if the user changed the look and feel intentionally.
   * The default implementation just invokes setLookAndFeel, but applications can override
   * it to save the setting, e.g. in the preferences.
   * @param name the name of the l&f
   */
  public void changeLookAndFeel(String name) {
    setLookAndFeel(name);
  }


  /**
   * Creates a menu to select the look and feel.
   * The current implementation returns only the tentackle look and feels.
   *
   * @return a JMenu with all available look and feels
   */
  public JMenu createLookAndFeelMenu() {

    final JMenu guiMenu = new JMenu(AppSwingRdcBundle.getString("GUI STYLE ..."));

    LookAndFeelInfo[] looks = PlafUtilities.getInstance().getInstalledTentackleLookAndFeels();

    // Menueintraege erzeugen
    if (looks != null)  {
      for (LookAndFeelInfo look : looks) {
        JRadioButtonMenuItem lnfitem = new JRadioButtonMenuItem(look.getName());
        lnfitem.setActionCommand(look.getClassName());
        lnfitem.addActionListener((ActionEvent e) -> {
          changeLookAndFeel(e.getActionCommand());
        });
        guiMenu.add(lnfitem);
      }
      guiMenu.addMenuListener(new MenuListener()  {
        @Override
        public void menuSelected(MenuEvent e) {
          String name = UIManager.getLookAndFeel().getClass().getName();
          // update radio buttons to show selecion
          Component[] items = guiMenu.getMenuComponents();
          for (Component item1 : items) {
            JRadioButtonMenuItem item = (JRadioButtonMenuItem) item1;
            item.setSelected(item.getActionCommand().equals(name));
          }
        }
        @Override
        public void menuDeselected(MenuEvent e) {}
        @Override
        public void menuCanceled(MenuEvent e) {}
      });
    }

    return guiMenu;
  }


  /**
   * Displays a message during login.
   * If the login dialog is visible, the message will be shown there.
   * Otherwise it will simply be logged.
   *
   * @param msg the status message
   */
  public void showLoginStatus(String msg) {
    if (loginDialog != null && loginDialog.isShowing()) {
      loginDialog.showStatus(msg);
    }
    else  {
      LOGGER.info(msg);
    }
  }



  /**
   * Brings up an edit dialog for a given object.
   *
   * @param <T> the PDO type
   * @param comp some component of the owner window, null if none
   * @param object the object to createPdo the dialog for
   * @param modal true if modal, else non-modal
   *
   * @return the edited object if modal, else null
   * @throws org.tentackle.misc.ApplicationException
   */
  @SuppressWarnings("unchecked")
  public <T extends PersistentDomainObject<T>> T showEditDialog (Component comp, T object, boolean modal) throws ApplicationException {
    if (modal)  {
      return PdoEditDialogPool.getInstance().editModal(object, comp);
    }
    else  {
      PdoEditDialogPool.getInstance().edit(object, comp, false);
      return null;
    }
  }


  /**
   * Brings up an edit dialog for a given class.
   *
   * @param <T> the PDO type
   * @param comp some component of the owner window, null if none
   * @param clazz is the object's class
   * @param modal true if modal, else non-modal
   *
   * @return the edited object if modal, else null
   * @throws org.tentackle.misc.ApplicationException
   */
  public <T extends PersistentDomainObject<T>> T showEditDialog (Component comp, Class<T> clazz, boolean modal) throws ApplicationException {
    return showEditDialog(comp, on(clazz), modal);
  }


  /**
   * Brings up a non-modal edit dialog for a given class.
   * The owner window is the application's frame.
   * This is the preferred method to be used in edit menus of application frames.
   *
   * @param <T> the PDO type
   * @param clazz is the object's class
   */
  public <T extends PersistentDomainObject<T>> void showEditDialog (Class<T> clazz) {
    try {
      showEditDialog((Window) window, clazz, false);
    }
    catch (ApplicationException ex) {
      FormError.showException(MessageFormat.format(AppSwingRdcBundle.getString("CANNOT CREATE EDIT DIALOG FOR CLASS {0}"), clazz), ex);
    }
  }


  /**
   * Brings up a search dialog for a given class.
   * Searchdialogs have no owner but use the owner as the related window.
   *
   * @param <T> the PDO type
   * @param w is the related window, null if none
   * @param clazz is the object's class
   */
  public <T extends PersistentDomainObject<T>> void showSearchDialog (FormWindow w, Class<T> clazz)  {
    PdoSearchDialog<T> sd = Rdc.createPdoSearchDialog(
            null, getDomainContext(), clazz, (o) -> clazz.isAssignableFrom(o.getClass()), false, false);
    // set the related window
    sd.setRelatedWindow(w);
    sd.showDialog();
  }


  /**
   * Brings up a search dialog for a given class.
   * The owner is the application frame.
   * This is the preferred method to be used in search menus of application frames.
   *
   * @param <T> the PDO type
   * @param clazz is the object's class
   */
  public <T extends PersistentDomainObject<T>> void showSearchDialog (Class<T> clazz)  {
    showSearchDialog(window, clazz);
  }



  /**
   * Installs the preferences backend.<br>
   * The option {@code "systemprefs"} forces usage of system preferences only.
   */
  @Override
  protected void configurePreferences() {
    showLoginStatus(AppSwingRdcBundle.getString("INSTALLING PREFERENCES..."));
    super.configurePreferences();
  }


  /**
   * Installs the available look and feels.
   * The default implementation installs all Tentackle Plafs.
   */
  protected void installLookAndFeels() {
    // get all supported tentackle look and feels
    showLoginStatus(AppSwingRdcBundle.getString("INSTALLING LOOK AND FEELS..."));
    PlafUtilities.getInstance().installTentackleLookAndFeels();
  }


  /**
   * Creates the login dialog.
   *
   * @param sessionInfo the session info
   * @param logo the application logo
   * @return the login dialog
   */
  public LoginDialog createLoginDialog(SessionInfo sessionInfo, Icon logo) {
    return new DefaultLoginDialog(sessionInfo, logo);
  }


  /**
   * Connects to the database backend (or application server).
   * <p>
   * Notice: if the application is started via JNLP (Java WebStart) and the
   * commandline option {@code "--db="} is given, it is interpreted as a URL
   * to the db-properties file relative to the JNLP codebase. If it starts with
   * {@code "&lt;protocol&gt;://"} it is taken as an absolute URL.<br>
   * Example:
   * <pre>
   * --db=http://www.tentackle.org/Invoicer/Db.properties
   *
   * is the same as:
   *
   * --db=Db.properties
   *
   * if the codebase is http://www.tentackle.org/Invoicer.
   * </pre>
   *
   * @return the connected context db, null if login aborted or authentication failed
   * @throws org.tentackle.misc.ApplicationException
   */
  protected DomainContext doLogin() throws ApplicationException {

    String username     = cmdLine.getOptionValue(Constants.BACKEND_USER);
    char[] password     = StringHelper.toCharArray(cmdLine.getOptionValue(Constants.BACKEND_PASSWORD));
    sessionPropsName    = cmdLine.getOptionValue(Constants.BACKEND_PROPS);

    SessionInfo sessionInfo = createSessionInfo(username, password, sessionPropsName);

    if (isDeployedByJNLP() && sessionPropsName != null) {
      // load the db properties relative to the JNLP codebase
      InputStream is = null;
      try {
        URL dbURL;
        if (sessionPropsName.indexOf("://") > 0) {
          dbURL = new URL(sessionPropsName);
        }
        else  {
          URL codeBase = ((BasicService) ServiceManager.lookup(BasicService.class.getName())).getCodeBase();
          dbURL = new URL(codeBase + "/" + sessionPropsName);
        }
        Properties sessionProps = new Properties();
        is = dbURL.openStream();
        sessionProps.load(is);
        sessionInfo.setProperties(sessionProps);
      }
      catch (MalformedURLException ex) {
        throw new ApplicationException("malformed URL for db properties", ex);
      }
      catch (IOException ex) {
        throw new ApplicationException(MessageFormat.format("loading db properties {0} failed", sessionPropsName), ex);
      }
      catch (RuntimeException | UnavailableServiceException ex) {
        throw new ApplicationException("cannot determine JNLP codebase", ex);
      }
      finally {
        if (is != null) {
          try {
            is.close();
          }
          catch (IOException ex) {}
        }
      }
    }

    /**
     * Reset to cross-platform laf for sure.
     * This will prevent buggy desktop lafs to irritate the user.
     */
    if (!UIManager.getLookAndFeel().isNativeLookAndFeel()) {
      try {
        UIManager.setLookAndFeel(DEFAULT_LAF);
      }
      catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException | RuntimeException ex) {
        LOGGER.warning("can not switch to default laf " + DEFAULT_LAF, ex);
      }
    }

    loginDialog = createLoginDialog(sessionInfo, logo);
    DomainContext context = null;
    Session session;
    int retry = 4;

    while (--retry >= 0) {

      if (username == null || password == null) {
        sessionInfo = loginDialog.getSessionInfo();
        if (sessionInfo == null) {
          return null;
        }
      }
      else  {
        loginDialog.setVisible(true);   // show only
        sessionInfo = createSessionInfo(username, password, sessionPropsName);
      }

      loginDialog.showStatus(AppSwingRdcBundle.getString("CONNECTING TO SERVER..."));

      if (sessionInfo.getApplicationName() == null) {
        sessionInfo.setApplicationName(ReflectionHelper.getClassBaseName(getClass()));
      }
      setSessionInfo(sessionInfo);

      session = createSession(sessionInfo);

      // open the database connection
      try {
        session.open();
      }
      catch (LoginFailedException lfx) {
        String msg;
        if      (retry == 2)    {
          msg = AppSwingRdcBundle.getString("LOGIN FAILED! (2 MORE RETRIES)");
        }
        else if (retry == 1)    {
          msg = AppSwingRdcBundle.getString("LOGIN FAILED! (LAST RETRY)");
        }
        else                    {
          msg = AppSwingRdcBundle.getString("LOGIN FAILED!");
        }
        sessionInfo.setPassword(null);
        LOGGER.info(msg, lfx);
        showLoginStatus(msg);
        continue;
      }
      catch (PersistenceException pex) {
        throw pex;
      }

      session.makeCurrent();

      // create the default context
      context = createDomainContext(session);
      if (context != null)  {
        break;    // login successful
      }

      // next round
      session.getSessionInfo().clearPassword();
      session.close();
    }

    if (retry < 0) {
      FormInfo.show(AppSwingRdcBundle.getString("LOGIN REFUSED! PLEASE CHECK YOUR USERNAME AND PASSWORD"));
      loginDialog.dispose();
      context = null;
    }

    setDomainContext(context);

    updateSessionInfoAfterLogin();

    return context;
  }


  /**
   * Do anything what's necessary after the connection has been established.
   * Setup preferences, etc...
   * The default creates the modification thread (but does not start it),
   * installs the Preferences, tentackle's SecurityManager and the look and feels.
   *
   * @throws org.tentackle.misc.ApplicationException
   */
  @Override
  protected void doConfigureApplication() throws ApplicationException {
    super.doConfigureApplication();
    installLookAndFeels();
  }


  /**
   * Creates the top level window.<br>
   * The method must not setVisible(true) and
   * is executed from within the EventQueue.
   *
   * @return the toplevel window, ready for showing
   * @throws org.tentackle.misc.ApplicationException
   */
  protected abstract FormWindow doCreateWindow() throws ApplicationException;


  /**
   * Initializes the GUI.<br>
   * The default implementation initializes the Tentackle-EventQueue.
   *
   * @see FormUtilities#getEventQueue()
   * @throws ApplicationException
   */
  protected void doInitializeGUI() throws ApplicationException {
    FormUtilities fu = FormUtilities.getInstance();
    fu.activate();        // activate (hook in)
    fu.getEventQueue();   // initialize EventQueue
  }


  /**
   * Shows the window.
   * The default implementation invokes setVisible(true).
   * If the window is a FormContainer, setFormValue() and saveValues() will be invoked as well.
   * @throws org.tentackle.misc.ApplicationException
   */
  protected void doShowWindow() throws ApplicationException {
    window.setFormValues();
    window.saveValues();
    ((Window)window).setVisible(true);
    if (loginDialog != null && loginDialog.isShowing()) {
      loginDialog.dispose();
    }
  }


  /**
   * Finishes the startup.<br>
   * Invoked after all has been displayed.
   * The default implementation starts the modification thread, unless
   * {@code "--nomodthread"} given.
   *
   * @throws org.tentackle.misc.ApplicationException
   */
  @Override
  protected void doFinishStartup() throws ApplicationException {

    super.doFinishStartup();

    // add a shutdown handler in case the modthread terminates unexpectedly
    PdoTracker.getInstance().addShutdownRunnable(() -> {
      if (!PdoTracker.getInstance().isTerminationRequested()) {
        LOGGER.severe("*** emergency shutdown ***");
        stop();
      }
    });

  }


  /**
   * Terminates the application gracefully.
   * (this is the only do.. method that does not throw AbstractApplicationException)
   *
   * @param exitValue the doStop value for System.exit()
   * @param ex an exception causing the termination, null if none
   */
  protected void doStop(int exitValue, Exception ex) {
    if (exitValue != 0 || ex != null) {
      LOGGER.log(Level.SEVERE,
                 "application " + getName() + " abnormally terminated with exit code " + exitValue,
                 ex);
    }
    else {
      LOGGER.info("application {0} terminated", getName());
    }

    try {
      // dispose the main window if not yet done
      if (window != null && ((Window)window).isShowing()) {
        ((Window)window).dispose();
      }

      // close all dialogs (clears editedBy/Since as well)
      PdoEditDialogPool.getInstance().disposeAllDialogs();

      // run final closing of dialogs. if any
      GUIExceptionHandler.runSaveState();

      // terminate all helper threads
      Pdo.terminateHelperThreads();

      // close db
      DomainContext context = getDomainContext();
      if (context != null) {
        Session session = context.getSession();
        if (session != null) {
          session.close();
        }
      }
    }
    catch (Exception anyEx) {
      LOGGER.severe("desktop application stopped ungracefully", anyEx);
    }

    // terminate runtime
    System.exit(exitValue);
  }


  /**
   * Terminates the application gracefully.
   *
   * @param exitValue the doStop value for System.exit()
   */
  protected void doStop(int exitValue) {
    doStop(exitValue, null);
  }

}
