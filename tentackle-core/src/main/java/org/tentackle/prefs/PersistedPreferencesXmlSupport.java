/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.prefs;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;



/**
 * Preferences XML support.
 * <p>
 * Unfortunately (and completely unnecessarily) java.util.prefs.XmlSupport is package scope.
 * The code in this file is largely copied from XmlSupport but modified to use the PersistedPreferences.
 *
 * <ul>
 * <li>XML import/export (cause java.util.prefs.XmlSupport is package scope)</li>
 * <li>probably more in the future ;-)</li>
 * </ul>
 *
 * This code is largely copied from Sun and only slightly modified,
 * so it's probably not legal to put it under LGPL...
 *
 * @author harald
 */

public class PersistedPreferencesXmlSupport {


  // The required DTD URI for exported preferences
  private static final String PREFS_DTD_URI =
      "http://java.sun.com/dtd/preferences.dtd";

  // The actual DTD corresponding to the URI
  private static final String PREFS_DTD =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +

      "<!-- DTD for preferences -->"               +

      "<!ELEMENT preferences (root) >"             +
      "<!ATTLIST preferences"                      +
      " EXTERNAL_XML_VERSION CDATA \"0.0\"  >"     +

      "<!ELEMENT root (map, node*) >"              +
      "<!ATTLIST root"                             +
      "          type (system|user) #REQUIRED >"   +

      "<!ELEMENT node (map, node*) >"              +
      "<!ATTLIST node"                             +
      "          name CDATA #REQUIRED >"           +

      "<!ELEMENT map (entry*) >"                   +
      "<!ATTLIST map"                              +
      "  MAP_XML_VERSION CDATA \"0.0\"  >"         +
      "<!ELEMENT entry EMPTY >"                    +
      "<!ATTLIST entry"                            +
      "          key CDATA #REQUIRED"              +
      "          value CDATA #REQUIRED >"          ;

  /**
   * Version number for the format exported preferences files.
   */
  private static final String EXTERNAL_XML_VERSION = "1.0";



  /**
   * Import preferences from the specified input stream, which is assumed
   * to contain an XML document in the format described in the Preferences
   * spec.
   *
   * @param is the input stream
   * @throws IOException if reading from the specified output stream
   *         results in an <tt>IOException</tt>.
   * @throws InvalidPreferencesFormatException Data on input stream does not
   *         constitute a valid XML document with the mandated document type.
   */
  static public void importPreferences(InputStream is) throws IOException, InvalidPreferencesFormatException {
    try {
      Document doc = loadPrefsDoc(is);
      String xmlVersion = ((Element) doc.getChildNodes().item(1)).getAttribute("EXTERNAL_XML_VERSION");
      if (xmlVersion.compareTo(EXTERNAL_XML_VERSION) > 0) {
        throw new InvalidPreferencesFormatException(
                "Exported preferences file format version " + xmlVersion +
                " is not supported. This java installation can read" +
                " versions " + EXTERNAL_XML_VERSION + " or older. You may need" +
                " to install a newer version of JDK.");
      }

      Element xmlRoot = (Element) doc.getChildNodes().item(1).getChildNodes().item(0);

      PersistedPreferences prefsRoot = (xmlRoot.getAttribute("type").equals("user") ?
              PersistedPreferences.userRoot() : PersistedPreferences.systemRoot());

      ImportSubtree(prefsRoot, xmlRoot);
    }
    catch (SAXException e) {
      throw new InvalidPreferencesFormatException(e);
    }
  }



  /**
   * Create a new prefs XML document.
   */
  private static Document createPrefsDoc (String qname) {
    try {
      DOMImplementation di = DocumentBuilderFactory.newInstance().newDocumentBuilder().getDOMImplementation();
      DocumentType dt = di.createDocumentType(qname, null, PREFS_DTD_URI);
      return di.createDocument(null, qname, dt);
    }
    catch (ParserConfigurationException e) {
      throw new AssertionError(e);
    }
  }


  private static class EH implements ErrorHandler {

    @Override
    public void error(SAXParseException x) throws SAXException {
      throw x;
    }

    @Override
    public void fatalError(SAXParseException x) throws SAXException {
      throw x;
    }

    @Override
    public void warning(SAXParseException x) throws SAXException {
      throw x;
    }
  }

  /**
   * Load an XML document from specified input stream, which must
   * have the requisite DTD URI.
   */
  private static Document loadPrefsDoc(InputStream in) throws SAXException, IOException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setIgnoringElementContentWhitespace(true);
    dbf.setValidating(true);
    dbf.setCoalescing(true);
    dbf.setIgnoringComments(true);
    try {
      DocumentBuilder db = dbf.newDocumentBuilder();
      db.setEntityResolver(new Resolver());
      db.setErrorHandler(new EH());
      return db.parse(new InputSource(in));
    }
    catch (ParserConfigurationException e) {
      throw new AssertionError(e);
    }
  }



  /**
   * Recursively traverse the specified preferences node and store
   * the described preferences into the system or current user
   * preferences tree, as appropriate.
   */
  private static void ImportSubtree(PersistedPreferences prefsNode, Element xmlNode) {
    NodeList xmlKids = xmlNode.getChildNodes();
    int numXmlKids = xmlKids.getLength();
    /*
     * We first lock the node, import its contents and get
     * child nodes. Then we unlock the node and go to children
     * Since some of the children might have been concurrently
     * deleted we check for this.
     */
    PersistedPreferences[] prefsKids;
    /* Lock the node */
    synchronized (prefsNode) {
      // Import any preferences at this node
      Element firstXmlKid = (Element) xmlKids.item(0);
      ImportPrefs(prefsNode, firstXmlKid);
      prefsKids = new PersistedPreferences[numXmlKids - 1];

      // Get involved children
      for (int i = 1; i < numXmlKids; i++) {
        Element xmlKid = (Element) xmlKids.item(i);
        prefsKids[i - 1] = prefsNode.node(xmlKid.getAttribute("name"));
      }
    } // unlocked the node
    // import children
    for (int i = 1; i < numXmlKids; i++) {
      ImportSubtree(prefsKids[i - 1], (Element) xmlKids.item(i));
    }
  }

  /**
   * Import the preferences described by the specified XML element
   * (a map from a preferences document) into the specified
   * preferences node.
   */
  private static void ImportPrefs(PersistedPreferences prefsNode, Element map) {
    NodeList entries = map.getChildNodes();
    for (int i = 0, numEntries = entries.getLength(); i < numEntries; i++) {
      Element entry = (Element) entries.item(i);
      prefsNode.put(entry.getAttribute("key"), entry.getAttribute("value"));
    }
  }


  private static class Resolver implements EntityResolver {

    @Override
    public InputSource resolveEntity(String pid, String sid) throws SAXException {
      if (sid.equals(PREFS_DTD_URI)) {
        InputSource is;
        is = new InputSource(new StringReader(PREFS_DTD));
        is.setSystemId(PREFS_DTD_URI);
        return is;
      }
      throw new SAXException("Invalid system identifier: " + sid);
    }
  }


  /**
   * Export the specified preferences node and, if subTree is true, all
   * subnodes, to the specified output stream.  Preferences are exported as
   * an XML document conforming to the definition in the Preferences spec.
   *
   * @param os the output stream
   * @param prefs the preferences
   * @param subTree true if with subtree
   * @throws IOException if writing to the specified output stream
   *         results in an <tt>IOException</tt>.
   * @throws BackingStoreException if preference data cannot be read from
   *         backing store.
   * @throws IllegalStateException if this node (or an ancestor) has been
   *         removed with the {@link Preferences#removeNode()} method.
   */
  static public void export(OutputStream os, final PersistedPreferences prefs, boolean subTree)
          throws IOException, BackingStoreException {

    Document doc = createPrefsDoc("preferences");
    Element preferences = doc.getDocumentElement();
    preferences.setAttribute("EXTERNAL_XML_VERSION", EXTERNAL_XML_VERSION);
    Element xmlRoot = (Element) preferences.appendChild(doc.createElement("root"));
    xmlRoot.setAttribute("type", (prefs.isUserNode() ? "user" : "system"));

    // Get bottom-up list of nodes from p to root, excluding root
    List<PersistedPreferences> ancestors = new ArrayList<>();

    for (PersistedPreferences kid = prefs, dad = kid.parent(); dad != null;
            kid = dad, dad = kid.parent()) {
      ancestors.add(kid);
    }

    Element e = xmlRoot;
    for (int i = ancestors.size() - 1; i >= 0; i--) {
      e.appendChild(doc.createElement("map"));
      e = (Element) e.appendChild(doc.createElement("node"));
      e.setAttribute("name", ancestors.get(i).name());
    }
    putPreferencesInXml(e, doc, prefs, subTree);

    writeDoc(doc, os);
  }


  /**
   * Put the preferences in the specified Preferences node into the
   * specified XML element which is assumed to represent a node
   * in the specified XML document which is assumed to conform to
   * PREFS_DTD.  If subTree is true, create children of the specified
   * XML node conforming to all of the children of the specified
   * Preferences node and recurse.
   *
   * @throws BackingStoreException if it is not possible to read
   *         the preferences or children out of the specified
   *         preferences node.
   */
  private static void putPreferencesInXml(Element elt, Document doc,
             PersistedPreferences prefs, boolean subTree) throws BackingStoreException {

    PersistedPreferences[] kidsCopy = null;
    String[] kidNames = null;

    // Node is locked to export its contents and get a
    // copy of children, then lock is released,
    // and, if subTree = true, recursive calls are made on children
    synchronized (prefs) {
      // Put map in xml element
      String[] keys = prefs.keys();
      Element map = (Element) elt.appendChild(doc.createElement("map"));
      for (String key : keys) {
        Element entry = (Element) map.appendChild(doc.createElement("entry"));
        entry.setAttribute("key", key);
        // NEXT STATEMENT THROWS NULL PTR EXC INSTEAD OF ASSERT FAIL
        entry.setAttribute("value", prefs.get(key, null));
      }
      // Recurse if appropriate
      if (subTree) {
        /* Get a copy of kids while lock is held */
        kidNames = prefs.childrenNames();
        kidsCopy = new PersistedPreferences[kidNames.length];
        for (int i = 0; i < kidNames.length; i++) {
          kidsCopy[i] = prefs.node(kidNames[i]);
        }
      }
    }

    if (subTree) {
      for (int i = 0; i < kidNames.length; i++) {
        Element xmlKid = (Element) elt.appendChild(doc.createElement("node"));
        xmlKid.setAttribute("name", kidNames[i]);
        putPreferencesInXml(xmlKid, doc, kidsCopy[i], subTree);
      }
    }
  }


  /**
   * Write XML document to the specified output stream.
   */
  private static final void writeDoc(Document doc, OutputStream out) throws IOException {
    try {
      Transformer t = TransformerFactory.newInstance().newTransformer();
      t.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, doc.getDoctype().getSystemId());
      t.setOutputProperty(OutputKeys.INDENT, "yes");
      t.transform(new DOMSource(doc), new StreamResult(out));
    }
    catch (TransformerException e) {
      throw new AssertionError(e);
    }
  }



  private PersistedPreferencesXmlSupport() {
  }

}
