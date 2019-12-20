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
import javax.inject.Named;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
@Named
public class JarParser
    extends ComponentSupport
{
  private static final String XML_VERSION_PATH = "feature/@version";

  private static final String XML_PLUGIN_NAME_PATH = "feature/@plugin";

  private static final String XML_PLUGIN_ID_PATH = "feature/@id";

  private static final String XML_NAME_PATH = "feature/@label";

  private static final String XML_FILE_NAME = "feature.xml";

  private static final String MANIFEST_FILE_PREFIX = "META-INF/";

  private static final String FEATURE_PROPERTIES = "feature.properties";

  private final DocumentBuilderFactory factory;

  private final DocumentBuilder builder;

  private final TempBlobConverter tempBlobConverter;

  public JarParser(final TempBlobConverter tempBlobConverter) throws Exception {
    this.tempBlobConverter = checkNotNull(tempBlobConverter);
    this.factory = DocumentBuilderFactory.newInstance();
    factory.setValidating(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    this.builder = factory.newDocumentBuilder();
  }

  public Optional<P2Attributes> getAttributesFromManifest(final TempBlob tempBlob, final String extension)
      throws InvalidMetadataException
  {
    P2Attributes p2Attributes;
    Optional<Manifest> manifestJarEntity = getSpecificEntity(tempBlob, extension, MANIFEST_FILE_PREFIX,
        (jarInputStream, jarEntry) -> JarParser.this.getManifestFromJarInputStream(jarInputStream));
    if (!manifestJarEntity.isPresent()) {
      return Optional.empty();
    }

    Attributes mainManifestAttributes = manifestJarEntity.get().getMainAttributes();
    String bundleLocalizationValue = mainManifestAttributes.getValue("Bundle-Localization");
    Optional<PropertyResourceBundle> propertiesOpt =
        getSpecificEntity(tempBlob, extension, bundleLocalizationValue, ((jarInputStream, jarEntry) ->
            new PropertyResourceBundle(new ByteArrayInputStream(IOUtils.toByteArray(jarInputStream)))));

    p2Attributes = P2Attributes.builder()
        .componentName(normalizeName(extractValueFromProperty(mainManifestAttributes.getValue("Bundle-SymbolicName"), propertiesOpt)))
        .pluginName(extractValueFromProperty(mainManifestAttributes.getValue("Bundle-Name"), propertiesOpt))
        .componentVersion(extractValueFromProperty(mainManifestAttributes.getValue("Bundle-Version"), propertiesOpt))
        .build();

    return Optional.ofNullable(p2Attributes);
  }

  public Optional<P2Attributes> getAttributesFromFeatureXML(final TempBlob tempBlob, final String extension) throws InvalidMetadataException
  {
    P2Attributes p2Attributes;

    Optional<Document> featureXmlOpt =
        getSpecificEntity(tempBlob, extension, XML_FILE_NAME, ((jarInputStream, jarEntry) ->
            toDocument(jarInputStream)));

    if (!featureXmlOpt.isPresent()) {
      return Optional.empty();
    }

    Optional<PropertyResourceBundle> propertiesOpt =
        getSpecificEntity(tempBlob, extension, FEATURE_PROPERTIES, ((jarInputStream, jarEntry) ->
            new PropertyResourceBundle(new ByteArrayInputStream(IOUtils.toByteArray(jarInputStream)))));

    Document document = featureXmlOpt.get();

    String pluginId = extractValueFromDocument(XML_PLUGIN_NAME_PATH, document);
    if (pluginId == null) {
      pluginId = extractValueFromDocument(XML_PLUGIN_ID_PATH, document);
    }

    String componentName = normalizeComponentName(extractValueFromProperty(pluginId, propertiesOpt));
    p2Attributes = P2Attributes.builder()
        .componentName(componentName)
        .pluginName(extractValueFromProperty(extractValueFromDocument(XML_NAME_PATH, document), propertiesOpt))
        .componentVersion(extractValueFromDocument(XML_VERSION_PATH, document))
        .build();

    return Optional.ofNullable(p2Attributes);
  }

  private Manifest getManifestFromJarInputStream(final JarInputStream jarInputStream) throws IOException {
    Manifest manifest = jarInputStream.getManifest();
    if (manifest != null) {
      return manifest;
    }
    return new Manifest(jarInputStream);
  }

  private <T> Optional<T> getSpecificEntity(
      final TempBlob tempBlob,
      final String extension,
      @Nullable final String startNameForSearch,
      ThrowingBiFunction<JarInputStream, JarEntry, T> getEntityFunction
  ) throws InvalidMetadataException
  {
    try (JarInputStream jis = getJarStreamFromBlob(tempBlob, extension)) {
      JarEntry jarEntry;
      while ((jarEntry = jis.getNextJarEntry()) != null) {
        if (startNameForSearch != null && jarEntry.getName().startsWith(startNameForSearch)) {
          return Optional.ofNullable(getEntityFunction.apply(jis, jarEntry));
        }
      }
    }
    catch (Exception ex) {
      throw new InvalidMetadataException(ex);
    }

    return Optional.empty();
  }

  private String extractValueFromProperty(String value, Optional<PropertyResourceBundle> propertyResourceBundleOpt) {
    if (!propertyResourceBundleOpt.isPresent() || value == null || !propertyResourceBundleOpt.get().containsKey(value.substring(1))) {
      return value;
    }

    // get property key for bundle name without '%' character in start
    return propertyResourceBundleOpt.get().getString(value.substring(1));
  }

  private String normalizeName(final String name) {
    String resultName = name;
    //handle org.tigris.subversion.clientadapter.svnkit;singleton:=true
    if (name != null) {
      resultName = name.split(";")[0];
    }
    return normalizeComponentName(resultName);
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

  @VisibleForTesting
  public JarInputStream getJarStreamFromBlob(final TempBlob tempBlob, final String extension) throws IOException {
    if (extension.equals("jar")) {
      return new JarInputStream(tempBlob.get());
    }
    else {
      return new JarInputStream(tempBlobConverter.getJarFromPackGz(tempBlob));
    }
  }
}
