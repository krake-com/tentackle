/*
 * Tentackle - http://www.tentackle.org.
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import org.tentackle.prefs.PersistedPreferences;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.swing.FormError;
import org.tentackle.swing.WorkerThread;

/**
 * UI helper methods for preferences.
 *
 * @author harald
 */
public class PreferencesHelper {


  private static final String FILE_EXTENSION    = ".xml";
  private static final String FILE_DESCRIPTION  = "Preferences";



  /**
   * Exports the preferences (user or system) to XML
   * and prompts the user in a dialog for the file.
   *
   * @param system true if system-prefs, else userprefs
   */
  public static void exportPreferences(final boolean system)  {
    JFileChooser jfc = new JFileChooser();
    jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    jfc.setFileFilter(new FileFilter()  {
      @Override
      public boolean accept(File f) {
        return f.getName().toLowerCase().endsWith(FILE_EXTENSION) || f.isDirectory();
      }
      @Override
      public String getDescription()  {
        return FILE_DESCRIPTION;
      }
    });
    if (jfc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
      File outFile = jfc.getSelectedFile();
      if (!outFile.getName().toLowerCase().endsWith(FILE_EXTENSION))  {
        outFile = new File(outFile.getPath() + FILE_EXTENSION);
      }
      final String pathname = outFile.getPath();
      new WorkerThread(() -> {
        try {
          PersistedPreferences prefs = system ?
                  PersistedPreferences.systemRoot() :
                  PersistedPreferences.userRoot();
          prefs.sync();
          try (OutputStream os = new FileOutputStream(pathname)) {
            prefs.exportSubtree(os);
          }
        }
        catch (Exception ex)  {
          FormError.showException("exporting preferences failed", ex);
        }
      }, "exporting preferences...").start();
    }
  }



  /**
   * Imports the preferences (user or system) from XML and
   * prompts the user for a file.
   *
   * @param system true if system-prefs, else userprefs
   */
  public static void importPreferences(final boolean system)  {
    JFileChooser jfc = new JFileChooser();
    jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    jfc.setFileFilter(new FileFilter()  {
      @Override
      public boolean accept(File f) {
        return f.getName().toLowerCase().endsWith(FILE_EXTENSION) || f.isDirectory();
      }
      @Override
      public String getDescription()  {
        return FILE_DESCRIPTION;
      }
    });
    if (jfc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
      final File inFile = jfc.getSelectedFile();
      new WorkerThread(() -> {
        try {
          PersistedPreferencesFactory.getInstance().importPreferences(new BufferedInputStream(new FileInputStream(inFile)));
          // write back to persistant storage
          if (system) {
            PersistedPreferences.systemRoot().flush();
          }
          else  {
            PersistedPreferences.userRoot().flush();
          }
        }
        catch (Exception ex)  {
          FormError.showException("importing preferences failed", ex);
        }
      }, "importing preferences...").start();
    }
  }



  private PreferencesHelper() {}

}
