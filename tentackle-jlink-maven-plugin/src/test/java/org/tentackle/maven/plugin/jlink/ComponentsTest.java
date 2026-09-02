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

package org.tentackle.maven.plugin.jlink;

import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the plexus components that define the {@code jlink} and {@code jpackage} packagings.
 * <p>
 * These are what {@code <packaging>jlink</packaging>} plus {@code <extensions>true</extensions>}
 * resolves to in a project's pom. The file is filtered during the build, so it also has to be
 * checked that every property was actually substituted - an unresolved {@code ${...}} would only
 * surface when a downstream project runs the lifecycle.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class ComponentsTest {

  private List<Element> components;


  @BeforeClass
  public void setUp() throws Exception {
    URL url = getClass().getResource("/META-INF/plexus/components.xml");
    if (url == null) {
      throw new SkipException("no components descriptor: run the tests as part of the maven build");
    }

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    Document document = factory.newDocumentBuilder().parse(url.toString());

    List<Element> componentsElement = children(document.getDocumentElement(), "components");
    assertEquals(componentsElement.size(), 1, "malformed components descriptor");
    components = children(componentsElement.get(0), "component");
  }


  /**
   * The jlink packaging produces a zip file.
   */
  @Test
  public void theJlinkPackagingIsAZip() {
    List<Element> handlers = artifactHandlers("jlink");

    assertEquals(handlers.size(), 1);
    assertEquals(configuration(handlers.getFirst(), "extension"), "zip");
    assertEquals(configuration(handlers.getFirst(), "addedToClasspath"), "false");
    assertEquals(configuration(handlers.getFirst(), "includesDependencies"), "true");
  }

  /**
   * The jpackage packaging produces one of the platform specific installers.
   */
  @Test
  public void theJpackagePackagingCoversEveryInstallerFormat() {
    List<String> extensions = new ArrayList<>();
    artifactHandlers("jpackage").forEach(handler -> extensions.add(configuration(handler, "extension")));
    extensions.sort(null);

    assertEquals(extensions, List.of("deb", "dmg", "exe", "msi", "pkg", "rpm"));
  }

  /**
   * Each packaging binds its own goal to the package phase, and leaves the rest of the default
   * lifecycle in place.
   */
  @Test
  public void thePackagingsBindTheirGoalToThePackagePhase() {
    assertTrue(lifecyclePhase("jlink", "package").endsWith(":jlink"), lifecyclePhase("jlink", "package"));
    assertTrue(lifecyclePhase("jpackage", "package").endsWith(":jpackage"), lifecyclePhase("jpackage", "package"));

    for (String packaging : List.of("jlink", "jpackage")) {
      assertTrue(lifecyclePhase(packaging, "compile").contains("maven-compiler-plugin"), packaging);
      assertTrue(lifecyclePhase(packaging, "test").contains("maven-surefire-plugin"), packaging);
      assertTrue(lifecyclePhase(packaging, "install").contains("maven-install-plugin"), packaging);
      assertTrue(lifecyclePhase(packaging, "deploy").contains("maven-deploy-plugin"), packaging);
    }
  }

  /**
   * The plugin binds its own current version, or a project would silently build with another one.
   */
  @Test
  public void thePackagingsBindThisVeryPlugin() {
    String version = JLinkMojo.class.getPackage().getImplementationVersion();
    for (String packaging : List.of("jlink", "jpackage")) {
      String binding = lifecyclePhase(packaging, "package");
      assertTrue(binding.startsWith("org.tentackle:tentackle-jlink-maven-plugin:"), binding);
      if (version != null) {    // only set when running against the packaged jar
        assertTrue(binding.contains(":" + version + ":"), binding);
      }
    }
  }

  /**
   * The descriptor is filtered during the build: an unresolved property would break the lifecycle
   * of every project using the packaging.
   */
  @Test
  public void everyPropertyWasSubstituted() {
    for (Element component : components) {
      String text = component.getTextContent();
      assertFalse(text.contains("${"), "unresolved property in " + roleHint(component) + ": " + text);
    }
  }


  private List<Element> artifactHandlers(String roleHint) {
    List<Element> handlers = new ArrayList<>();
    for (Element component : components) {
      if ("org.apache.maven.artifact.handler.ArtifactHandler".equals(text(component, "role")) &&
          roleHint.equals(roleHint(component))) {
        handlers.add(component);
      }
    }
    return handlers;
  }

  /**
   * Gets the plugin bound to a phase of the default lifecycle of a packaging.
   *
   * @param packaging the packaging, i.e. the role hint of the lifecycle mapping
   * @param phase the lifecycle phase
   * @return the trimmed plugin coordinates
   */
  private String lifecyclePhase(String packaging, String phase) {
    for (Element component : components) {
      if ("org.apache.maven.lifecycle.mapping.LifecycleMapping".equals(text(component, "role")) &&
          packaging.equals(roleHint(component))) {
        Element lifecycles = children(configuration(component), "lifecycles").getFirst();
        Element lifecycle = children(lifecycles, "lifecycle").getFirst();
        Element phases = children(lifecycle, "phases").getFirst();
        List<Element> phaseElements = children(phases, phase);
        assertEquals(phaseElements.size(), 1, packaging + " has no phase " + phase);
        return phaseElements.getFirst().getTextContent().trim();
      }
    }
    throw new IllegalArgumentException("no lifecycle mapping for " + packaging);
  }

  private String roleHint(Element component) {
    return text(component, "role-hint");
  }

  private Element configuration(Element component) {
    List<Element> configurations = children(component, "configuration");
    assertEquals(configurations.size(), 1, "malformed component " + roleHint(component));
    return configurations.getFirst();
  }

  private String configuration(Element component, String name) {
    return text(configuration(component), name);
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
