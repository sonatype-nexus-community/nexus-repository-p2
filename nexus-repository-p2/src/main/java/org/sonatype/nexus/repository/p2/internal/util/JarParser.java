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
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static javax.xml.xpath.XPathConstants.NODE;

/**
 * Utility methods for working with Jar (Jar Binks, worst character) files
 */
@Named
public class JarParser
    extends ComponentSupport
{
  private static final String XML_VERSION_PATH = "feature/@version";

  private static final String XML_NAME_PATH = "feature/@id";

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

  public Optional<P2Attributes> getAttributesFromManifest(final JarInputStream jis) throws Exception
  {
    P2Attributes p2Attributes = null;
    JarEntry jarEntry;
    while (p2Attributes == null && (jarEntry = jis.getNextJarEntry()) != null) {
      if (jarEntry.getName().startsWith(MANIFEST_FILE_PREFIX)) {

        Manifest manifest = jis.getManifest();
        if (manifest == null) {
          manifest = new Manifest(jis);
        }
        Attributes mainManifestAttributes = manifest.getMainAttributes();
        String name = normalizeName(mainManifestAttributes.getValue("Bundle-SymbolicName"));

        p2Attributes = P2Attributes.builder()
            .componentName(name)
            .componentVersion(mainManifestAttributes
                .getValue("Bundle-Version"))
            .build();
      }
    }

    return Optional.ofNullable(p2Attributes);
  }

  private String normalizeName(final String name) {
    String resultName = name;
    //handle org.tigris.subversion.clientadapter.svnkit;singleton:=true
    if (name.contains(";")) {
      resultName = name.substring(0, name.indexOf(";"));
    }
    return resultName;
  }

  public Optional<P2Attributes> getAttributesFromFeatureXML(final JarInputStream jis) throws Exception
  {
    P2Attributes p2Attributes = null;
    JarEntry jarEntry;
    while (p2Attributes == null && (jarEntry = jis.getNextJarEntry()) != null) {
      if (jarEntry.getName().equals(XML_FILE_NAME)) {
        Document document = toDocument(jis);

        p2Attributes = P2Attributes.builder()
            .componentName(extractValueFromDocument(XML_NAME_PATH, document))
            .componentVersion(extractValueFromDocument(XML_VERSION_PATH, document))
            .build();
      }
    }
    return Optional.ofNullable(p2Attributes);
  }

  private Document toDocument(final InputStream is) throws Exception
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
      return node.getNodeValue();
    }
    catch (XPathExpressionException e) {
      log.warn("Could not extract value, failed with exception: {}", e.getMessage());
      return null;
    }
  }
}
