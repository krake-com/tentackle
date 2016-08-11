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

package org.tentackle.security.pdo;

import java.util.ResourceBundle;
import org.tentackle.common.Bundle;
import org.tentackle.common.BundleFactory;
import org.tentackle.common.LocaleProvider;

/**
 * Bundle for validation messages.
 *
 * @author harald
 */
@Bundle
public class ValidationBundle {

  /**
   * Gets the bundle.
   *
   * @return the resource bundle
   */
  public static ResourceBundle getBundle() {
    return BundleFactory.getBundle(ValidationBundle.class.getName(), LocaleProvider.getInstance().getLocale());
  }

  /**
   * Gets a string for the given key.
   *
   * @param key the key
   * @return the string from the bundle
   */
  public static String getString(String key) {
    return getBundle().getString(key);
  }

  private ValidationBundle() {
  }

}
