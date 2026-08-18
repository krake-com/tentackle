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

package org.tentackle.persist.wurblet;

import org.wurbelizer.wurbel.WurbelException;

import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.parse.Document;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;
import org.tentackle.wurblet.WurbletArgument;
import org.tentackle.wurblet.WurbletArgumentExpression;
import org.tentackle.wurblet.WurbletArgumentParser;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.testng.Assert.assertTrue;

/**
 * Common helpers for the persistence-wurblet tests.
 * <p>
 * The wurblets generate code from the application's model, so the tests build a real
 * {@link Entity} from the model-definition DSL rather than mocking the model API. The wurblet
 * itself is instantiated directly: everything covered here is a pure function of the model plus
 * the wurblet arguments, and needs neither the {@code Wurbler} container nor a source file to
 * weave the result into.
 */
public final class WurbletTestSupport {

  /**
   * A model covering the cases the generators branch on: a single-column type, a
   * multi-column type ({@code BMoney} maps to a value and a scale column), and a nullable one.
   */
  private static final String TEST_MODEL = """
      name := Blah
      id := 1
      table := blah

      long        txId      txid       the transaction id
      char        modType   modtype    the modification type
      String(32)  user      moduser    the user name
      Timestamp   processed processed  when it was processed
      BMoney      amount    amount     a two-column money type
      """;

  /**
   * Creates the test entity.
   *
   * @return the entity
   * @throws ModelException if the model is broken
   */
  public static Entity createEntity() throws ModelException {
    return Model.getInstance().getEntityFactory().createEntity(new Document(TEST_MODEL), null);
  }

  /**
   * Creates a bare model wurblet.
   * <p>
   * A wurblet is normally set up by the {@code Wurbler} before {@code run()} is invoked. The
   * methods under test here do not read that state, so the plain instance is enough - except for
   * the backend list, which is why {@link #createWurblet(String)} exists.
   *
   * @return the wurblet
   */
  public static DbModelWurblet createWurblet() {
    return new DbModelWurblet();
  }

  /**
   * Creates a model wurblet that generates for the given backends.
   * <p>
   * {@code getBackends()} is otherwise only populated by {@code run()} from the container's
   * {@code backends} property; overriding it is what lets the backend-specific features
   * (array operators) be tested.
   *
   * @param backendNames the comma-separated backend names, e.g. {@code "PostgreSQL"}
   * @return the wurblet
   */
  public static DbModelWurblet createWurblet(String backendNames) {
    Collection<Backend> backends = BackendFactory.getInstance().getBackends(backendNames);
    assertTrue(!backends.isEmpty(), "no backend for '" + backendNames + "'");
    return new DbModelWurblet() {
      @Override
      public Collection<Backend> getBackends() {
        return backends;
      }
    };
  }

  /**
   * Creates a model wurblet for the given wurblet arguments.
   * <p>
   * {@code getMethodArguments()} is otherwise populated by {@code run()} from the wurblet's
   * argument parser; overriding it is what lets the generated method signature be tested.
   *
   * @param entity the entity
   * @param args the wurblet arguments, one per token
   * @return the wurblet
   * @throws WurbelException if the arguments could not be parsed
   */
  public static DbModelWurblet createWurblet(Entity entity, String... args) throws WurbelException {
    WurbletArgumentParser parser = new WurbletArgumentParser(entity, false, Arrays.asList(args));
    return new DbModelWurblet() {
      @Override
      public List<WurbletArgument> getMethodArguments() {
        return parser.getMethodArguments();
      }
    };
  }

  /**
   * Parses a single wurblet argument against the test entity.
   *
   * @param entity the entity
   * @param text the argument text, e.g. {@code "user:<>"}
   * @return the argument
   * @throws WurbelException if the argument could not be parsed
   */
  public static WurbletArgument parseArgument(Entity entity, String text) throws WurbelException {
    return (WurbletArgument) parseExpression(entity, text).getOperands().getFirst();
  }

  /**
   * Parses wurblet arguments against the test entity.
   *
   * @param entity the entity
   * @param text the argument texts, one per token
   * @return the parsed expression
   * @throws WurbelException if the arguments could not be parsed
   */
  public static WurbletArgumentExpression parseExpression(Entity entity, String... text) throws WurbelException {
    return new WurbletArgumentParser(entity, false, Arrays.asList(text)).getExpression();
  }

  /**
   * Gets an attribute of the entity by its java name.
   *
   * @param entity the entity
   * @param name the java name
   * @return the attribute
   */
  public static Attribute attribute(Entity entity, String name) {
    List<Attribute> attributes = entity.getAttributes();
    return attributes.stream()
                     .filter(a -> a.getName().equals(name))
                     .findFirst()
                     .orElseThrow(() -> new AssertionError("no attribute '" + name + "' in " + attributes));
  }


  private WurbletTestSupport() {
  }

}
