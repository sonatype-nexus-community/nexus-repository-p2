/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2017-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.p2.internal.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.repository.p2.internal.exception.InvalidMetadataException;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static javax.xml.xpath.XPathConstants.NODE;

/**
 * Utility methods for working with Jar (Jar Binks, worst character) files
 */
@Named
public class JarParser
    extends ComponentSupport
{
  private static final String XML_VERSION_PATH = "feature/@version";

  private static final String XML_PLUGIN_NAME_PATH = "feature/@plugin";

  private static final String XML_GROUP_NAME_PATH = "feature/@id";

  private static final String XML_NAME_PATH = "feature/@label";

  private static final String XML_FILE_NAME = "feature.xml";

  private static final String MANIFEST_FILE_PREFIX = "META-INF/";

  private final DocumentBuilderFactory factory;

  private final DocumentBuilder builder;

  public JarParser() throws Exception {
    this.factory = DocumentBuilderFactory.newInstance();
    factory.setValidating(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    this.builder = factory.newDocumentBuilder();
  }

  public Optional<P2Attributes> getAttributesFromManifest(final JarInputStream jis) throws InvalidMetadataException
  {
    P2Attributes p2Attributes = null;
    JarEntry jarEntry;
    try {
      while (p2Attributes == null && (jarEntry = jis.getNextJarEntry()) != null) {
        if (jarEntry.getName().startsWith(MANIFEST_FILE_PREFIX)) {

          Manifest manifest = jis.getManifest();
          if (manifest == null) {
            manifest = new Manifest(jis);
          }
          Attributes mainManifestAttributes = manifest.getMainAttributes();
          String name = normalizeName(mainManifestAttributes.getValue("Bundle-SymbolicName"));

          p2Attributes = P2Attributes.builder()
              .groupName(name)
              .componentName(mainManifestAttributes
                  .getValue("Bundle-Name"))
              .componentVersion(mainManifestAttributes
                  .getValue("Bundle-Version"))
              .build();
        }
      }
    }
    catch (IOException | NullPointerException ex) {
      throw new InvalidMetadataException();
    }

    return Optional.ofNullable(p2Attributes);
  }

  private String normalizeName(final String name) {
    String resultName = name;
    //handle org.tigris.subversion.clientadapter.svnkit;singleton:=true
    resultName = name.split(";")[0];
    return resultName;
  }

  public Optional<P2Attributes> getAttributesFromFeatureXML(final JarInputStream jis) throws InvalidMetadataException
  {
    P2Attributes p2Attributes = null;
    JarEntry jarEntry;
    try {
      while (p2Attributes == null && (jarEntry = jis.getNextJarEntry()) != null) {
        if (XML_FILE_NAME.equals(jarEntry.getName())) {
          Document document = toDocument(jis);

          String groupName = extractValueFromDocument(XML_PLUGIN_NAME_PATH, document);
          if (groupName == null) {
            groupName = extractValueFromDocument(XML_GROUP_NAME_PATH, document);
          }
          p2Attributes = P2Attributes.builder()
              .groupName(groupName)
              .componentName(extractValueFromDocument(XML_NAME_PATH, document))
              .componentVersion(extractValueFromDocument(XML_VERSION_PATH, document))
              .build();
        }
      }
    }
    catch (IOException | NullPointerException | SAXException ex) {
      throw new InvalidMetadataException();
    }
    return Optional.ofNullable(p2Attributes);
  }

  private Document toDocument(final InputStream is) throws IOException, SAXException
  {
    return builder.parse(is);
  }

  @Nullable
  private String extractValueFromDocument(
      final String path,
      final Document from)
  {
    try {
      XPath xPath = XPathFactory.newInstance().newXPath();
      Node node = (Node) xPath.evaluate(path, from, NODE);
      if (node != null) {
        return node.getNodeValue();
      }
    }
    catch (XPathExpressionException e) {
      log.warn("Could not extract value, failed with exception: {}", e.getMessage());
    }
    return null;
  }
}
