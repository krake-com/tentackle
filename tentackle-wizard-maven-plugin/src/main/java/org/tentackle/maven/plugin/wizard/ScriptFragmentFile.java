/*
 * Tentackle - https://tentackle.org
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

package org.tentackle.maven.plugin.wizard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The file holding a script fragment.<br>
 * Reads and writes the fragment as UTF-8 and remembers the file most recently
 * loaded or saved in the user preferences, so that the next session can pick it up again.
 */
public class ScriptFragmentFile {

  /** The preferences key holding the absolute path of the file used last. */
  static final String LAST_FILE_KEY = "lastScriptFile";

  private final Preferences preferences;
  private final File defaultDirectory;

  private File file;    // the file currently associated with the fragment, null if none


  /**
   * Creates the fragment file.
   *
   * @param preferences the preferences to remember the file used last
   * @param defaultDirectory the directory to start browsing in if there is no file yet
   */
  public ScriptFragmentFile(Preferences preferences, File defaultDirectory) {
    this.preferences = Objects.requireNonNull(preferences, "missing preferences");
    this.defaultDirectory = defaultDirectory;
  }

  /**
   * Gets the file currently associated with the fragment.
   *
   * @return the file, null if the fragment has not been loaded from or saved to a file yet
   */
  public File getFile() {
    return file;
  }

  /**
   * Gets the directory to start browsing in.
   *
   * @return the directory of the current file, the default directory if there is no current file
   */
  public File getDefaultDirectory() {
    File directory = file == null ? null : file.getParentFile();
    if (directory == null) {
      directory = defaultDirectory;
    }
    return directory != null && directory.isDirectory() ? directory : null;
  }

  /**
   * Gets the file used last.
   *
   * @return the file, null if there is none or it is no longer a readable file
   */
  public File getLastFile() {
    String path = preferences.get(LAST_FILE_KEY, null);
    if (path != null) {
      File lastFile = new File(path);
      if (lastFile.isFile() && lastFile.canRead()) {
        return lastFile;
      }
    }
    return null;
  }

  /**
   * Loads a fragment from a file.<br>
   * The file becomes the current one and is remembered in the preferences.
   *
   * @param fragmentFile the file to load from
   * @return the fragment
   * @throws IOException if reading the file failed
   */
  public String load(File fragmentFile) throws IOException {
    String fragment = Files.readString(fragmentFile.toPath(), StandardCharsets.UTF_8);
    remember(fragmentFile);
    return fragment;
  }

  /**
   * Saves a fragment to a file.<br>
   * The file becomes the current one and is remembered in the preferences.
   *
   * @param fragmentFile the file to save to
   * @param fragment the fragment
   * @throws IOException if writing the file failed
   */
  public void save(File fragmentFile, String fragment) throws IOException {
    Files.writeString(fragmentFile.toPath(), fragment, StandardCharsets.UTF_8);
    remember(fragmentFile);
  }


  /**
   * Makes a file the current one and remembers it in the preferences.
   *
   * @param fragmentFile the file
   */
  private void remember(File fragmentFile) {
    file = fragmentFile;
    preferences.put(LAST_FILE_KEY, fragmentFile.getAbsolutePath());
    try {
      preferences.flush();
    }
    catch (BackingStoreException bx) {
      // does no harm: the fragment is on disk, only the convenience of reloading it is lost
    }
  }

}
