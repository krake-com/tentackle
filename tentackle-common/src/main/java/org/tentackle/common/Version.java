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


package org.tentackle.common;

import java.io.IOException;
import java.util.Properties;


/**
 * Holds Tentackle versioning information.
 * <p>
 * The values are read from {@code org/tentackle/common/Version.properties}, which is filtered
 * by Maven at build time. Running this class as a main class prints the version.
 *
 * @author harald
 */
public class Version {

  private static Properties getProperties() {
    try {
      return FileHelper.loadProperties(Version.class.getName().replace('.', '/'));
    }
    catch (IOException iox) {
      return new Properties();    // avoid NPE
    }
  }

  private static final Properties properties = getProperties();


  /** the artifact version. */
  public static final String RELEASE = properties.getProperty("version", "<no-version>");

  /** the build timestamp. */
  public static final String DATE = properties.getProperty("date", "<no-date>");


  /**
   * Prints the build version and license.
   *
   * @param args command line args (ignored)
   */
  public static void main(String[] args) {
    System.out.println(
"\nTentackle - https://tentackle.org" +
"\n\nVersion " + RELEASE + " (" + DATE + ")\n\n" +
"This library is free software; you can redistribute it and/or\n" +
"modify it under the terms of the GNU Lesser General Public\n" +
"License as published by the Free Software Foundation; either\n" +
"version 2.1 of the License, or (at your option) any later version.\n\n" +
"This library is distributed in the hope that it will be useful,\n" +
"but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
"MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU\n" +
"Lesser General Public License for more details.\n\n" +
"You should have received a copy of the GNU Lesser General Public\n" +
"License along with this library; if not, write to the Free Software\n" +
"Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA\n");
  }

  private Version() {}

}
