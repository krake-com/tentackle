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

import org.tentackle.misc.Holder;
import org.tentackle.reflect.ReflectionHelper;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base implementation of a graphic provider based on image files.
 *
 * @author harald
 */
public abstract class ImageGraphicProvider implements GraphicProvider {

  /**
   * The file extensions to try, in this order.
   */
  private static final String[] EXTENSIONS = new String[] { ".png", ".gif", ".jpg" };



  /**
   * The image cache.
   */
  private final Map<String, Holder<Image>> imageMap;

  /**
   * Path leading to resources.
   */
  private final String imagePath;


  /**
   * Creates an image provider.
   *
   * @param imagePath the image path, null for {@code "<package>/images/"}
   */
  public ImageGraphicProvider(String imagePath) {
    imageMap = new ConcurrentHashMap<>();
    String pkgPath = ReflectionHelper.getPackagePathName(getClass());
    this.imagePath = imagePath == null ? (("/".equals(pkgPath) ? "" : pkgPath) + "/images/") : imagePath;
  }

  /**
   * Creates an image provider for {@code "<package>/images/"}.
   */
  public ImageGraphicProvider() {
    this(null);
  }


  /**
   * Gets the file extensions supported by this image provider.
   *
   * @return the extensions
   */
  public String[] getExtensions() {
    return EXTENSIONS;
  }


  @Override
  public ImageView createGraphic(String name) {
    Image image = getImage(name);
    if (image == null) {
      throw new FxRuntimeException("no such image '" + name + "'");
    }
    return new ImageView(image);
  }

  /**
   * Gets an image.<br>
   * Path and file extension of the name, if any, are ignored.
   * The image is loaded on first access and cached.
   *
   * @param name the image name
   * @return the image, null if no such image found
   */
  protected Image getImage(String name) {
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

    Holder<Image> imageHolder = imageMap.get(name);

    if (imageHolder == null) {
      imageHolder = new Holder<>();
      for (String type : getExtensions()) {
        String url = imagePath + name + type;
        // with JPMS we must load the input stream in our own module.
        try (InputStream is = getClass().getResourceAsStream(url)) {
          if (is != null) {
            imageHolder.accept(new Image(is));
            break;
          }
        }
        catch (IOException ix) {
          throw new FxRuntimeException("loading image failed for " + url, ix);
        }
      }
      imageMap.put(name, imageHolder);
    }

    return imageHolder.get();
  }

}
