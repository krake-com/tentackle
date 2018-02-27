/*
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

package org.tentackle.fx.rdc;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.prefs.BackingStoreException;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;

interface FileUtilities$Singleton {
  FileUtilities INSTANCE = ServiceFactory.createService(FileUtilities.class);
}

/**
 * File-related utility methods.
 *
 * @author harald
 */
@Service(FileUtilities.class)    // defaults to self
public class FileUtilities {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static FileUtilities getInstance() {
    return FileUtilities$Singleton.INSTANCE;
  }

  private static final Logger LOGGER = Logger.get(FileUtilities.class);


  /**
   * Selects a file via filechooser dialog and remembers the decision in the user preferences.
   *
   * @param prefName the preferences name
   * @param prefKey the preferences key
   * @param fileExtension the filename extension (including the leading dot)
   * @param fileType the localized name of the file type
   * @param owner the dialog owner for the selection dialog, null if none
   * @return the selected file, null if aborted
   */
  public File selectFile(String prefName, String prefKey, String fileExtension, String fileType, Stage owner) {
    // retrieve the last filename from userspace
    PersistedPreferences prefs = PersistedPreferences.userRoot().node(prefName);
    String lastName = prefs.get(prefKey, null);

    FileChooser fc = new FileChooser();
    if (lastName != null) {
      fc.setInitialFileName(lastName);
    }
    fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(fileType, fileExtension));
    File selectedFile = fc.showSaveDialog(owner);
    if (selectedFile != null) {
      if (!selectedFile.getName().toLowerCase().endsWith(fileExtension)) {
        selectedFile = new File(selectedFile.getPath() + fileExtension);
      }
      // remember the last filename in userspace
      if (!PersistedPreferencesFactory.getInstance().isReadOnly()) {
        prefs.put(prefKey, selectedFile.getAbsolutePath());
        try {
          prefs.flush();
        }
        catch (BackingStoreException bx) {
          // does no harm if not succesful... (log only intentionally if level is FINE)
          LOGGER.fine("saving filename to preferences failed", bx);
        }
      }
    }
    return selectedFile;
  }


  /**
   * Edits/opens a given file via the Desktop utilities.<br>
   * The corresponding application will be chosen according to the mime/file type.
   *
   * @param file the file
   */
  public void editFile(File file) {
    new Thread(() -> {  // needs another thread because Desktop will block the FX-application thread
      try {
        LOGGER.info("launching EDIT action for {0}", file);
        Desktop.getDesktop().edit(file);
      }
      catch (UnsupportedOperationException | IOException ex) {
        LOGGER.info("{0} -> trying OPEN action for {1}", ex.getMessage(), file);
        try {
          Desktop.getDesktop().open(file);
          LOGGER.info("success!");
        }
        catch (IOException ex2) {
          LOGGER.severe("cannot open file", ex2);
        }
      }
    }).start();
  }

}
