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
package org.sonatype.nexus.repository.p2.internal.metadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.TempBlob;

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.repository.p2.internal.util.P2DataAccess.HASH_ALGORITHMS;

/**
 * Removes absolute URL entries from artifacts.xml
 *
 * @since 3.4
 */
@Named
@Singleton
public class ArtifactsXmlRewriter
    extends ComponentSupport
{
  private static final String MIRRORS_URL_XPATH = "repository/properties/property[@name=\"p2.mirrorsURL\"]";

  public TempBlob removeMirrorUrlFromArtifactsXml(final TempBlob artifact,
                                                  final Repository repository,
                                                  final String extension)
  {
    try {
      Document artifactsXml;

      if (Objects.equals(extension, "xml")) {
        artifactsXml = readArtifactsXml(artifact);
        rewriteXml(artifactsXml);
        return convertArtifactsXmlIntoBlob(repository, artifactsXml);
      }
      else if (Objects.equals(extension, "xml.xz")) {
        artifactsXml = extractXmlFromXz(artifact.get());
        rewriteXml(artifactsXml);
        return convertArtifactsXmlXzIntoBlob(repository, artifactsXml);
      }
      else if (Objects.equals(extension, "jar")) {
        artifactsXml = extractXmlFromJar(artifact.get());
        rewriteXml(artifactsXml);
      }
      else {
        throw new IllegalStateException();
      }

      return null;
    }
    catch(ParserConfigurationException | IOException | XPathExpressionException | TransformerException | SAXException ex) {
      log.error("Failed to rewrite mirrorURL entries from artifacts.{} with reason {} ", extension, ex);
      return artifact;
    }
  }

  private Document extractXmlFromXz(final InputStream is) throws IOException {
    checkNotNull(is);
    Document xml = null;
    try {
      XZCompressorInputStream xzis = new XZCompressorInputStream(is);
      xml = readArtifactsXmlFromInputStream(xzis);
    }
    catch(ParserConfigurationException | SAXException ex) {
      log.error("Woops");
    }

    return xml;
  }

  private Document extractXmlFromJar(final InputStream is) throws IOException {
    checkNotNull(is);
    ZipInputStream zis = new ZipInputStream(is);
    Document xml = null;
    try {
      ZipEntry zipEntry;
      while ( (zipEntry = zis.getNextEntry()) != null) {
        if (zipEntry.getName() == "artifacts.xml") {
          xml = readArtifactsXmlFromInputStream(zis);
        }
      }
    }
    catch(ParserConfigurationException | SAXException ex) {
      log.error("Woops");
    }

    return xml;
  }

  private Document readArtifactsXml(final TempBlob artifact) throws ParserConfigurationException, IOException,
                                                                    SAXException
  {
    return readArtifactsXmlFromInputStream(artifact.get());
  }

  private Document readArtifactsXmlFromInputStream(final InputStream is) throws ParserConfigurationException, IOException,
                                                                                SAXException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setValidating(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(is);
  }

  private void rewriteXml(final Document artifactsXml) throws XPathExpressionException {
    XPathFactory xpf = XPathFactory.newInstance();
    XPath xPath = xpf.newXPath();
    XPathExpression expression = xPath.compile(MIRRORS_URL_XPATH);

    Node mirrorsUrl = (Node) expression.evaluate(artifactsXml, XPathConstants.NODE);
    if (mirrorsUrl != null) {
      mirrorsUrl.getParentNode().removeChild(mirrorsUrl);
    }
  }

  private TempBlob convertArtifactsXmlIntoBlob(final Repository repository,
                                               final Document artifact) throws TransformerException
  {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Source xmlSource = new DOMSource(artifact);
    Result outputTarget = new StreamResult(outputStream);
    TransformerFactory.newInstance().newTransformer().transform(xmlSource, outputTarget);
    InputStream is = new ByteArrayInputStream(outputStream.toByteArray());
    StorageFacet storageFacet = repository.facet(StorageFacet.class);
    return storageFacet.createTempBlob(is, HASH_ALGORITHMS);
  }

  private TempBlob convertArtifactsXmlXzIntoBlob(final Repository repository,
                                                 final Document artifact) throws TransformerException, IOException
  {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Source xmlSource = new DOMSource(artifact);
    Result outputTarget = new StreamResult(outputStream);
    TransformerFactory.newInstance().newTransformer().transform(xmlSource, outputTarget);
    XZCompressorOutputStream xzCompressorOutputStream = new XZCompressorOutputStream(outputStream);
    xzCompressorOutputStream.write(outputStream.toByteArray());
    InputStream is = new ByteArrayInputStream(outputStream.toByteArray());
    StorageFacet storageFacet = repository.facet(StorageFacet.class);
    return storageFacet.createTempBlob(is, HASH_ALGORITHMS);
  }
}
