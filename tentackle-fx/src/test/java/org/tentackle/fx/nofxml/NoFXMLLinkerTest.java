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

package org.tentackle.fx.nofxml;

import org.testng.annotations.Test;

import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.FxRuntimeException;

import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@link NoFXMLLinker}.<p>
 * No control is created, so that the tests run without a JavaFX toolkit.
 * The views are plain panes, and the linked nodes are regions and shapes.
 */
@SuppressWarnings("missing-explicit-ctor")
public class NoFXMLLinkerTest {

  /** the resources injected into view and controller. */
  public static class TestBundle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] { { "hello", "world" } };
    }
  }


  private static Region region(String id) {
    Region region = new Region();
    region.setId(id);
    return region;
  }


  /** view with all supported features. */
  static class FullView extends Pane {

    private final List<String> invocations;

    @NoFXML
    private final Region first = new Region();

    @NoFXML
    private final Rectangle second = new Rectangle();

    @NoFXML
    private final Region renamed = region("custom");

    @NoFXML
    private final Region viewOnly = new Region();     // not bound by the controller

    @NoFXML
    private ResourceBundle resources;

    @NoFXML
    private String unsupported = "ignored";

    FullView(List<String> invocations) {
      this.invocations = invocations;
    }

    @NoFXML
    private void initialize() {
      invocations.add("view");
    }

    @NoFXML
    private void unsupportedMethod() {
      invocations.add("unsupportedMethod");
    }
  }

  /** controller for the full view. */
  static class FullController extends AbstractFxController {

    private final List<String> invocations;

    @FXML
    private Region first;

    @FXML
    private Rectangle second;

    @FXML
    private Region custom;

    @FXML
    private ResourceBundle resources;

    FullController(List<String> invocations) {
      this.invocations = invocations;
    }

    @FXML
    private void initialize() {
      invocations.add("controller");
    }

    @FXML
    private void initialize(int unsupported) {
      invocations.add("initialize(int)");
    }
  }


  /** view without resources. */
  static class SimpleView extends Pane {
    @NoFXML
    private final Region first = new Region();
  }

  /** controller without resources. */
  static class SimpleController extends AbstractFxController {
    @FXML
    private Region first;
  }


  /** view with a duplicate node id. */
  static class DuplicateIdView extends Pane {
    @NoFXML
    private final Region first = new Region();

    @NoFXML
    private final Region other = region("first");
  }

  /** view with a null node. */
  static class NullNodeView extends Pane {
    @NoFXML
    private Region first;
  }

  /** view requesting resources. */
  static class ResourceView extends Pane {
    @NoFXML
    private final Region first = new Region();

    @NoFXML
    private ResourceBundle resources;
  }

  /** controller requesting resources. */
  static class ResourceController extends AbstractFxController {
    @FXML
    private Region first;

    @FXML
    private ResourceBundle resources;
  }

  /** controller with a field not provided by the view. */
  static class UnboundController extends AbstractFxController {
    @FXML
    private Region first;

    @FXML
    private Region missing;
  }

  /** controller with a final field. */
  static class FinalFieldController extends AbstractFxController {
    @FXML
    private final Region first = new Region();
  }

  /** view whose initialize method fails. */
  static class FailingView extends Pane {
    @NoFXML
    private final Region first = new Region();

    @NoFXML
    private void initialize() {
      throw new IllegalStateException("failed");
    }
  }


  @Test
  public void testGetInstance() {
    NoFXMLLinker linker = NoFXMLLinker.getInstance();
    assertNotNull(linker);
    assertTrue(linker instanceof DefaultNoFXMLLinker, linker.getClass().getName());
    assertSame(NoFXMLLinker.getInstance(), linker);
  }

  @Test
  public void testLink() {
    List<String> invocations = new ArrayList<>();
    FullView view = new FullView(invocations);
    FullController controller = new FullController(invocations);
    ResourceBundle resources = new TestBundle();

    new DefaultNoFXMLLinker().link(view, controller, resources);

    assertSame(controller.first, view.first);
    assertSame(controller.second, view.second);
    assertSame(controller.custom, view.renamed);
    assertEquals(view.first.getId(), "first");
    assertEquals(view.second.getId(), "second");
    assertEquals(view.renamed.getId(), "custom");
    assertEquals(view.viewOnly.getId(), "viewOnly");
    assertEquals(view.unsupported, "ignored");

    assertSame(view.resources, resources);
    assertSame(controller.resources, resources);

    // controller first, unsupported methods ignored
    assertEquals(invocations, List.of("controller", "view"));

    assertSame(controller.getView(), view);
    assertNull(controller.getContainer());
  }

  @Test
  public void testNullResourcesWithoutResourceFields() {
    SimpleView view = new SimpleView();
    SimpleController controller = new SimpleController();
    new DefaultNoFXMLLinker().link(view, controller, null);
    assertSame(controller.first, view.first);
    assertSame(controller.getView(), view);
  }

  @Test
  public void testDuplicateId() {
    FxRuntimeException ex = expectThrows(FxRuntimeException.class,
        () -> new DefaultNoFXMLLinker().link(new DuplicateIdView(), new SimpleController(), null));
    assertTrue(ex.getMessage().contains("declared more than once"), ex.getMessage());
  }

  @Test
  public void testNullNode() {
    FxRuntimeException ex = expectThrows(FxRuntimeException.class,
        () -> new DefaultNoFXMLLinker().link(new NullNodeView(), new SimpleController(), null));
    assertTrue(ex.getMessage().contains("node is null"), ex.getMessage());
  }

  @Test
  public void testUnboundFxmlField() {
    UnboundController controller = new UnboundController();
    FxRuntimeException ex = expectThrows(FxRuntimeException.class,
        () -> new DefaultNoFXMLLinker().link(new SimpleView(), controller, null));
    assertTrue(ex.getMessage().contains("unbound @FXML fields"), ex.getMessage());
    assertTrue(ex.getMessage().endsWith(": missing"), ex.getMessage());
    assertNull(controller.getView());
  }

  @Test
  public void testMissingResourcesInView() {
    FxRuntimeException ex = expectThrows(FxRuntimeException.class,
        () -> new DefaultNoFXMLLinker().link(new ResourceView(), new SimpleController(), null));
    assertTrue(ex.getMessage().contains("resources are null"), ex.getMessage());
  }

  @Test
  public void testMissingResourcesInController() {
    FxRuntimeException ex = expectThrows(FxRuntimeException.class,
        () -> new DefaultNoFXMLLinker().link(new SimpleView(), new ResourceController(), null));
    assertTrue(ex.getMessage().contains("resources are null"), ex.getMessage());
  }

  @Test
  public void testFinalControllerField() {
    FxRuntimeException ex = expectThrows(FxRuntimeException.class,
        () -> new DefaultNoFXMLLinker().link(new SimpleView(), new FinalFieldController(), null));
    assertTrue(ex.getMessage().contains("cannot inject into final field"), ex.getMessage());
  }

  @Test
  public void testInitializeThrows() {
    FxRuntimeException ex = expectThrows(FxRuntimeException.class,
        () -> new DefaultNoFXMLLinker().link(new FailingView(), new SimpleController(), null));
    assertTrue(ex.getCause() instanceof InvocationTargetException, String.valueOf(ex.getCause()));
    assertTrue(ex.getCause().getCause() instanceof IllegalStateException);
  }

}
