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

import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

/**
 * Tests the plugin descriptor generated from the mojo annotations.
 * <p>
 * The {@code @Mojo} annotations are not retained at runtime. They are therefore asserted against
 * {@code META-INF/maven/plugin.xml}, which is what maven actually reads.
 * <p>
 * The descriptor is generated in the {@code process-classes} phase, hence it is available to
 * the tests of the very same build.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PluginDescriptorTest {

  private Map<String, Element> mojos;


  @BeforeClass
  public void setUp() throws Exception {
    URL url = getClass().getResource("/META-INF/maven/plugin.xml");
    if (url == null) {
      throw new SkipException("no plugin descriptor: run the tests as part of the maven build");
    }

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    Document document = factory.newDocumentBuilder().parse(url.toString());

    List<Element> mojosElement = children(document.getDocumentElement(), "mojos");
    assertEquals(mojosElement.size(), 1, "malformed plugin descriptor");

    mojos = new HashMap<>();
    for (Element mojo : children(mojosElement.get(0), "mojo")) {
      mojos.put(text(mojo, "goal"), mojo);
    }
  }


  /**
   * The goals of this plugin.
   *
   * @return the goal name and implementation
   */
  @DataProvider(name = "goals")
  public Object[][] goals() {
    return new Object[][] {
      { "init", InitMojo.class },
      { "pdo", PdoMojo.class },
      { "script", ScriptMojo.class },
      { "browse", BrowseMojo.class },
      { "operation", OperationMojo.class }
    };
  }

  @Test(dataProvider = "goals")
  public void goalIsDeclaredAsExpected(String goal, Class<?> mojoClass) {
    Element mojo = mojos.get(goal);
    assertNotNull(mojo, "no such goal: " + goal + ", got " + mojos.keySet());

    assertEquals(text(mojo, "implementation"), mojoClass.getName());
    assertEquals(text(mojo, "aggregator"), "true");
    assertEquals(text(mojo, "threadSafe"), "true", "goal " + goal + " is not thread-safe");
  }

  /**
   * All goals share one lock, because they work on JVM-wide state such as the model singleton.
   * Hence, they are serialized in parallel builds.
   */
  @Test
  public void goalsShareExecutionLock() {
    List<AbstractWizardMojo> mojos = List.of(new InitMojo(), new PdoMojo(), new ScriptMojo(), new BrowseMojo(), new OperationMojo());
    Lock lock = mojos.getFirst().getExecutionLock();
    assertNotNull(lock);
    for (AbstractWizardMojo mojo : mojos) {
      assertSame(mojo.getExecutionLock(), lock, mojo.getClass().getName());
    }
  }


  private String text(Element parent, String name) {
    List<Element> elements = children(parent, name);
    return elements.isEmpty() ? null : elements.get(0).getTextContent();
  }

  private List<Element> children(Element parent, String name) {
    List<Element> elements = new ArrayList<>();
    NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (node instanceof Element element && element.getTagName().equals(name)) {
        elements.add(element);
      }
    }
    return elements;
  }

}
