/*
 * Tentackle - a framework for java desktop applications
 * http://www.tentackle.org
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

package org.tentackle.tools.fixprefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tentackle.app.ConsoleApplication;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.persist.Db;
import org.tentackle.persist.DbPreferencesKey;
import org.tentackle.persist.DbPreferencesNode;

/**
 * A tool to fix the persisted preferences stored in a database.
 *
 * @author harald
 */
public class FixPreferences extends ConsoleApplication {

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(FixPreferences.class);


  private int nodeCount;                                // number of modified nodes
  private int keyCount;                                 // number of modified keys
  private final Map<Long, DbPreferencesNode> nodeMap;   // map ID:Node
  private final Set<DbPreferencesNode> processedNodes;  // already processed nodes


  public FixPreferences() {
    super("Fix Preferences");
    nodeMap = new HashMap<>();
    processedNodes = new HashSet<>();
  }

  @Override
  public <U extends PersistentDomainObject<U>> U getUser(DomainContext context, long userId) {
    return null;    // not used
  }


  private void run() {
    Db db = (Db) getSession();
    db.assertNotRemote();   // must be a direct JDBC link
    db = db.clone();
    long tv = db.begin();
    try {
      List<? extends DbPreferencesNode> nodes = new DbPreferencesNode(db).selectAllObjects();
      List<? extends DbPreferencesKey> keys = new DbPreferencesKey(db).selectAllObjects();
      LOGGER.info("processing {0} nodes and {1} keys", nodes.size(), keys.size());
      for (DbPreferencesNode node: nodes) {
        nodeMap.put(node.getId(), node);
      }
      processPreferences(nodes, keys);
      db.commit(tv);
      LOGGER.info("{0} nodes modified, {1} keys modified", nodeCount, keyCount);
    }
    finally {
      db.close();
    }
  }



  private DbPreferencesNode findRoot(DbPreferencesNode node) {
    while (node != null && node.getParentId() != 0) {
      node = nodeMap.get(node.getParentId());
    }
    return node;
  }

  private List<DbPreferencesNode> findChilds(List<? extends DbPreferencesNode> allNodes, DbPreferencesNode node) {
    List<DbPreferencesNode> childs = new ArrayList<>();
    for (DbPreferencesNode child: allNodes) {
      if (child.getParentId() == node.getId()) {
        childs.add(child);
      }
    }
    return childs;
  }

  private List<DbPreferencesKey> findKeys(List<? extends DbPreferencesKey> allKeys, DbPreferencesNode node) {
    List<DbPreferencesKey> keys = new ArrayList<>();
    for (DbPreferencesKey key: allKeys) {
      if (key.getNodeId() == node.getId()) {
        keys.add(key);
      }
    }
    return keys;
  }


  private void processPreferences(List<? extends DbPreferencesNode> allNodes, List<? extends DbPreferencesKey> allKeys) {
    // check root nodes
    for (DbPreferencesNode node: allNodes) {
      if (node.getParentId() == 0 && node.getRootNodeId() == 0) {
        node.setRootNodeId(node.getId());
        node.saveObject();
        nodeCount++;
      }
    }

    // process parent chain to root
    for (DbPreferencesNode node: allNodes) {
      if (node.getParentId() != 0) {
        DbPreferencesNode root = findRoot(node);
        if (root == null) {
          LOGGER.severe("missing root for node {0}", node.getId());
        }
        else {
          // go down all subnodes and keys and set the rootNodeId if missing
          processNode(allNodes, allKeys, root);
        }
      }
    }
  }


  private void processNode(List<? extends DbPreferencesNode> allNodes, List<? extends DbPreferencesKey> allKeys, DbPreferencesNode node) {
    if (!processedNodes.contains(node)) {
      for (DbPreferencesKey key: findKeys(allKeys, node)) {
        if (key.getRootNodeId() == 0) {
          key.setRootNodeId(node.getRootNodeId());
          key.saveObject();
          keyCount++;
        }
      }
      for (DbPreferencesNode child: findChilds(allNodes, node)) {
        if (child.getRootNodeId() == 0) {
          child.setRootNodeId(node.getRootNodeId());
          child.saveObject();
          nodeCount++;
        }
        processNode(allNodes, allKeys, child);
      }
      processedNodes.add(node);
    }
  }


  public static void main(String[] args) {
    FixPreferences app = new FixPreferences();
    app.start(args);
    try {
      app.run();
    }
    catch (RuntimeException rex) {
      LOGGER.severe("FAILED!", rex);
    }
    finally {
      app.stop();
    }
  }

}
