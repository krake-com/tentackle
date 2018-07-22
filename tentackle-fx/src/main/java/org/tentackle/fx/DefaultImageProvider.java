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
 *
 */

package org.tentackle.fx;

import javafx.scene.image.Image;

import org.tentackle.log.Logger;
import org.tentackle.reflect.ReflectionHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default image provider for framework-related images.
 *
 * @author harald
 */
@ImageProviderService
public class DefaultImageProvider implements ImageProvider {

  private static final Logger LOGGER = Logger.get(DefaultImageProvider.class);

  /**
   * File type EXTENSIONS to try.
   */
  private static final String[] EXTENSIONS = new String[] { ".png", ".gif", ".jpg" };



  /**
   * The image cache.
   */
  private final Map<String, Image> imageMap;

  /**
   * Path leading to resources.
   */
  private final String imagePath;


  /**
   * Creates an image provider.
   */
  public DefaultImageProvider() {
    imageMap = new ConcurrentHashMap<>();
    imagePath = "/" + ReflectionHelper.getPackageName(getClass()).replace('.', '/') + "/images/";
  }


  /**
   * Gets the file EXTENSIONS supported by this image provider.
   *
   * @return the EXTENSIONS
   */
  public String[] getExtensions() {
    return EXTENSIONS;
  }


  @Override
  public Image getImage(String name) {
    // cut path, if any
    int ndx = name.lastIndexOf('/');
    if (ndx >= 0) {
      name = name.substring(ndx + 1);
    }
    // cut type extension, if any
    ndx = name.lastIndexOf('.');
    if (ndx >= 0) {
      name = name.substring(0, ndx);
    }

    Image image = imageMap.get(name);
    if (image == null) {
      for (String type: getExtensions()) {
        String url = imagePath + name + type;
        // with JPMS we must load the inputstream in our own module.
        try (InputStream is = getClass().getResourceAsStream(url)) {
          if (is != null) {
            image = new Image(is);
            imageMap.put(name, image);
            break;
          }
        }
        catch (IOException ix) {
          LOGGER.warning("closing input stream failed for " + url, ix);
        }
      }
      if (image == null) {
        throw new IllegalArgumentException("no such image '" + name + "'");
      }
    }
    return image;
  }

}
