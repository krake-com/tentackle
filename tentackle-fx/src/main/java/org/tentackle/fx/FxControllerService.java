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

import org.tentackle.common.Analyze;
import org.tentackle.common.Constants;
import org.tentackle.common.Service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for Tentackle FX-controllers.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Service(FxController.class)
@Analyze("org.tentackle.buildsupport.FxControllerBundleAnalyzeHandler")
public @interface FxControllerService {

  /**
   * Disables loading of the resource bundle.<br>
   * Set <code>resources=FxControllerService.RESOURCES_NONE</code>.
   */
  String RESOURCES_NONE = Constants.NAME_NONE;

  /**
   * Disables the fxml-loader if the view is provided by the controller.<br>
   * Set <code>url=FxControllerService.FXML_NONE</code>.<br>
   * The controller needs one of the following constructors:
   * <ol>
   *   <li>If <code>resources=FxControllerService.RESOURCES_NONE</code>:
   *      <pre>
   *        public &lt;Controllername&gt; () {
   *
   *        }
   *      </pre>
   *   </li>
   *   <li>otherwise:
   *     <pre>
   *        public &lt;Controllername&gt; (ResourceBundle resources) {
   *
   *        }
   *      </pre>
   *      Notice that {@code resources} is never null.
   *   </li>
   * </ol>
   */
  String FXML_NONE = Constants.NAME_NONE;

  /**
   * Type of controller caching.
   */
  enum CACHING {

    /** no caching. */
    NO,

    /** controller is a singleton and cached. */
    YES,

    /** controller is a singleton, cached and preloaded in background. */
    PRELOAD

  }


  /**
   * Type of binding.
   */
  enum BINDING {

    /** no binding. */
    NO,

    /** standard binding. */
    YES,

    /** include inherited components. */
    COMPONENT_INHERITED,

    /** include inherited bindables. */
    BINDABLE_INHERITED,

    /** include inherited components and bindables. */
    ALL_INHERITED

  }



  /**
   * Gets the fxml-URL for annotated controller.
   *
   * @return the fxml-URL or empty string if <code>&lt;controller-classname&gt;.fxml</code>
   */
  String url() default "";

  /**
   * Gets the resources bundle for annotated controller.
   *
   * @return the bundle url, empty string if <code>&lt;controller-classname&gt;.properties</code>, no resources if {@link #RESOURCES_NONE}
   */
  String resources() default "";

  /**
   * The stylesheet.<br>
   * By default, a file <code>&lt;controller-classname&gt;.css</code> is searched for. Missing is ok.
   * @return the CSS string
   */
  String css() default "";

  /**
   * Returns whether controller should be unit tested.
   *
   * @return true if create and bind for test (default), false if no test
   */
  boolean test() default true;

  /**
   * Returns whether controller should be cached.
   *
   * @return the caching mode, CACHING.NO is default.
   */
  CACHING caching() default CACHING.NO;

  /**
   * Returns whether controller should be bound to the model.
   *
   * @return the binding mode, BINDING.YES is the default
   */
  BINDING binding() default BINDING.YES;

}
