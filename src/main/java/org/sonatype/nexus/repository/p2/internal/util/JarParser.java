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
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.sonatype.goodies.common.ComponentSupport;

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
  private static final String XML_FILE_NAME = "feature.xml";
  public static final String UNKNOWN_VERSION = "unknown";
  private final DocumentBuilderFactory factory;
  private final DocumentBuilder builder;

  public JarParser() throws Exception {
    this.factory = DocumentBuilderFactory.newInstance();
    factory.setValidating(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    this.builder = factory.newDocumentBuilder();
  }

  public String getVersionFromJarFile(final JarInputStream jis) throws Exception
  {
    try {
      JarEntry jarEntry;
      while ((jarEntry = jis.getNextJarEntry()) != null) {
        if (jarEntry.getName().equals(XML_FILE_NAME)) {
          return getValueFromJarEntry(jis, XML_VERSION_PATH);
        }
      }
      return getVersionFromManifest(jis);
    }
    catch (IOException ex) {
      log.warn("Could not get version from file due to following exception: {}", ex.getMessage());
    }
    return UNKNOWN_VERSION;
  }

  private String getVersionFromManifest(final JarInputStream jis) throws Exception
  {
    try {
      return jis.getManifest().getMainAttributes().getValue("Bundle-Version");
    }
    catch (Exception ex) {
      return UNKNOWN_VERSION;
    }
  }

  @Nullable
  private String getValueFromJarEntry(final JarInputStream jis,
                                      final String path) throws Exception
  {
    return extractValueFromDocument(path, toDocument(jis));
  }

  private Document toDocument(final InputStream is) throws Exception
  {
    return builder.parse(is);
  }

  @Nullable
  private String extractValueFromDocument(final String path,
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
