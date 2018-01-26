/*
 * Copyright (C) 2015 by:
 *
 * Hydro Aluminium Rolled Products GmbH
 * Koblenzer Strasse 122
 * D-41468 Neuss
 * Germany
 * Tel. +49 2131 382 0
 * http://www.hydro.com/Deutschland
 *
 * All Rights Reserved.
 */

package org.tentackle.test.fx.rdc;

import com.sun.javafx.application.PlatformImpl;
import java.awt.GraphicsEnvironment;
import javafx.scene.control.Label;
import org.reflections.Reflections;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.test.pdo.TestApplication;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

/**
 * Tests whether GUI-providers could be loaded and bound.<br>
 * Needs a db-connection to initialize all providers properly.<br>
 * Simply extend this class and put that in a test source folder.
 * <p>
 * Example:
 * <pre>
  public class TestGuiProviders extends GuiProviderTest {
    TestGuiProviders() {
      super("my.package.name");
    }
  }
 </pre>
 *
 * @author harald
 */
public abstract class GuiProviderTest extends TestApplication {

  private final String packagePrefix;

  public GuiProviderTest(String packagePrefix) {
    super("fx-rdc-provider-test");
    this.packagePrefix = packagePrefix;
  }

  @Test(alwaysRun = true)
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testGuiProviders() {

    if (getDomainContext().getSession() == null) {
      Reporter.log("no backend found -> no FX tests<br>");
      return;
    }

    if (GraphicsEnvironment.isHeadless()) {
      Reporter.log("JVM is headless -> no FX tests<br>");
      return;
    }

    try {
      PlatformImpl.startup(() -> Reporter.log("FX started<br>", true));
    }
    catch(RuntimeException rex) {
      Reporter.log("FX not supported -> no FX tests<br>");
      return;
    }

    Reflections reflections = new Reflections(packagePrefix);
    reflections.getSubTypesOf(PersistentDomainObject.class).forEach(c -> {
      try {
        PersistentDomainObject pdo = PdoFactory.getInstance().create(c);
        GuiProvider provider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
        Reporter.log("testing GuiProvider for PDO " + c.getName() + "<br>");
        if (provider.editorExists()) {
          provider.createEditor();
        }
        if (provider.finderExists()) {
          provider.createFinder();
        }
        provider.createIcon();
        provider.createTableView();
        provider.createTreeCell();
        provider.createTreeItem();
      }
      catch (PdoRuntimeException nx) {
        if (!(nx.getCause() instanceof ClassNotFoundException)) {
          Assert.fail("creating GuiProvider for " + c.getName() + " failed", nx);
        }
        else {
          Reporter.log("no GuiProvider for " + c.getName() + "<br>");
        }
      }
      catch (RuntimeException ex) {
        Assert.fail("Testing GuiProvider for " + c.getName() + " failed", ex);
      }
    });

  }

}
