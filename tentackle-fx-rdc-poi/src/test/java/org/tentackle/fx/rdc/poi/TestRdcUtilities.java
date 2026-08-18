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

package org.tentackle.fx.rdc.poi;

import org.tentackle.common.Service;
import org.tentackle.fx.rdc.RdcUtilities;

import javafx.stage.Stage;
import java.io.File;

/**
 * The {@link RdcUtilities} used throughout the tests of this module.
 * <p>
 * It is registered via {@code @Service} so that {@link RdcUtilities#getInstance()} resolves to it.
 * {@link #selectFile} never opens a {@code FileChooser} (which would require a running JavaFX toolkit
 * and a user), but records its arguments and returns a preconfigured file instead.
 */
@Service(RdcUtilities.class)
public class TestRdcUtilities extends RdcUtilities {

  /**
   * Gets the registered singleton.
   *
   * @return the singleton, cast to the test implementation
   */
  public static TestRdcUtilities instance() {
    return (TestRdcUtilities) RdcUtilities.getInstance();
  }


  private String prefName;
  private String prefKey;
  private String fileExtension;
  private String fileType;
  private Stage owner;
  private File selectedFile;

  /**
   * Creates the test utilities.
   */
  public TestRdcUtilities() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public File selectFile(String prefName, String prefKey, String fileExtension, String fileType, Stage owner) {
    this.prefName = prefName;
    this.prefKey = prefKey;
    this.fileExtension = fileExtension;
    this.fileType = fileType;
    this.owner = owner;
    return selectedFile;
  }

  /**
   * Sets the file returned by {@link #selectFile}.
   *
   * @param selectedFile the file, null if the user aborts the selection
   */
  public void setSelectedFile(File selectedFile) {
    this.selectedFile = selectedFile;
  }

  /**
   * Gets the preferences node name passed to {@link #selectFile}.
   *
   * @return the node name
   */
  public String getPrefName() {
    return prefName;
  }

  /**
   * Gets the preferences key passed to {@link #selectFile}.
   *
   * @return the key
   */
  public String getPrefKey() {
    return prefKey;
  }

  /**
   * Gets the file extension passed to {@link #selectFile}.
   *
   * @return the extension
   */
  public String getFileExtension() {
    return fileExtension;
  }

  /**
   * Gets the file type description passed to {@link #selectFile}.
   *
   * @return the file type
   */
  public String getFileType() {
    return fileType;
  }

  /**
   * Gets the owner stage passed to {@link #selectFile}.
   *
   * @return the owner, null if none
   */
  public Stage getOwner() {
    return owner;
  }

}
