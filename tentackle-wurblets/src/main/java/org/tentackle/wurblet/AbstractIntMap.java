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


package org.tentackle.wurblet;


import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.wurbelizer.wurbel.WurbelUtilities;
import org.wurbelizer.wurbel.WurbelException;
import org.wurbelizer.wurblet.AbstractWurblet;


/**
 * Extends the AbstractWurblet by methods to parse the integer mapping model.
 *
 * @author  harald
 */
public class AbstractIntMap extends AbstractWurblet {

  private String          modelName;      // name of the model
  private List<MapEntry>  mapEntryList;   // List of MapEntrys


  public AbstractIntMap() {
    super();
  }


  /**
   * {@inheritDoc}
   * <p>
   * Overridden to load the model.
   */
  @Override
  public void run() throws WurbelException {

    super.run();

    mapEntryList = new ArrayList<>();

    for (String arg: getContainer().getArgs()) {
      if (!arg.startsWith("--")) {
        if (modelName == null) {
          modelName = arg;
          break;
        }
      }
    }

    if (modelName == null) {
      throw new WurbelException("attributefile file not specified");
    }

    // read the model
    try (BufferedReader reader = new BufferedReader(WurbelUtilities.openReader(modelName))) {
      int lineno = 0;
      for (;;)  {
        String line = reader.readLine();
        if (line == null) {
          break;    // end of file
        }
        lineno++;
        if (line.startsWith("#")) {
          continue;   // comment line
        }
        line = line.trim();
        if (line.length() > 0)  {
          try {
            mapEntryList.add(new MapEntry(line));
          }
          catch (Exception ex)  {
            throw new WurbelException(ex.getMessage() + " in line " + lineno + " of " + modelName, ex);
          }
        }
      }
    }
    catch (IOException ex) {
      throw new WurbelException("reading " + modelName + " failed", ex);
    }
  }



  /**
   * Gets the attributes.
   *
   * @return the attributes
   */
  public List<MapEntry> getMapEntryList() {
    return mapEntryList;
  }




  /**
   * The mapping entry.
   */
  public static class MapEntry {

    private String name;       // the index name
    private String value;      // the mapped value

    /**
     * Creates an entry from a source line.
     * The first word is the name. The rest (trimmed) forms the value.
     *
     * @param text the source line
     */
    public MapEntry(String text) throws Exception {

      StringTokenizer stok = new StringTokenizer(text, " \t" );

      // parse tokens
      while (stok.hasMoreTokens())  {
        String token = stok.nextToken();
        if (name == null)  {
          name = token;
          value = text.substring(name.length()).trim();    // rest of the line (trimmed)
          break;
        }
      }

      if (name == null || value == null) {
        throw new Exception ("syntax error");
      }
    }


    /**
     * @return the attribute's name
     */
    public String getName() {
      return name;
    }

    /**
     * @return the value
     */
    public String getValue() {
      return value;
    }
  }

}
