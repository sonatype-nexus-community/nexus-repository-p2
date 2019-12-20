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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.repository.p2.internal.exception.InvalidMetadataException;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.storage.TempBlob;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.xml.xpath.XPathConstants.NODE;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.normalizeComponentName;

/**
 * Utility methods for working with Jar (Jar Binks, worst character) files
 *
 * @since 0.next
 */
public interface JarExtractor<T>
{
  default Optional<T> getSpecificEntity(
      final TempBlob tempBlob,
      final String extension,
      @Nullable final String startNameForSearch)
  {
    try (JarInputStream jis = getJarStreamFromBlob(tempBlob, extension)) {
      JarEntry jarEntry;
      while ((jarEntry = jis.getNextJarEntry()) != null) {
        if (startNameForSearch != null && jarEntry.getName().startsWith(startNameForSearch)) {
          return Optional.ofNullable(createSpecificEntity(jis, jarEntry));
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    return Optional.empty();
  }

  T createSpecificEntity(final JarInputStream jis, final JarEntry jarEntry)
      throws IOException, ParserConfigurationException, SAXException;

  default JarInputStream getJarStreamFromBlob(final TempBlob tempBlob, final String extension) throws IOException {
    if (extension.equals("jar")) {
      return new JarInputStream(tempBlob.get());
    }
    else {
      return null;
      //return new JarInputStream(tempBlobConverter.getJarFromPackGz(tempBlob));
    }
  }
}
