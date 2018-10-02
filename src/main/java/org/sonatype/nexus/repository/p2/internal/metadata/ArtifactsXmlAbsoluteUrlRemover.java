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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.TempBlob;

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static java.util.UUID.randomUUID;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.sonatype.nexus.repository.p2.internal.util.P2DataAccess.HASH_ALGORITHMS;

/**
 * Removes absolute URL entries from artifacts.xml
 *
 * @since 3.4
 */
@Named
@Singleton
public class ArtifactsXmlAbsoluteUrlRemover
    extends ComponentSupport
{

  private static final String XZ = "xml.xz";

  private static final String JAR = "jar";

  private static final String MIRRORS_URL_PROPERTY = "p2.mirrorsURL";

  private static final String ARTIFACTS_XML = "artifacts.xml";

  public TempBlob removeMirrorUrlFromArtifactsXml(final TempBlob artifact,
                                                  final Repository repository,
                                                  final String extension)
  {
    try {
      Path tempFile = createTempFile("p2-artifacts-" + randomUUID().toString(), "xml");
      // This is required in the case that the input stream is a jar to allow us to extract a single file
      Path artifactsTempFile = createTempFile("jar-entry-" + randomUUID().toString(), "xml");
      try {
        try (InputStream xmlIn = xmlInputStream(artifact, extension, artifactsTempFile)) {
          try (OutputStream xmlOut = xmlOutputStream(extension, tempFile)) {
            XMLInputFactory inputFactory = XMLInputFactory.newFactory();
            XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
            XMLEventReader reader = null;
            XMLEventWriter writer = null;
            //try-with-resources will be better here, but XMLEventReader and XMLEventWriter are not AutoCloseable
            try {
              reader = inputFactory.createXMLEventReader(xmlIn);
              writer = outputFactory.createXMLEventWriter(xmlOut);
              streamXmlToWriterAndRemoveAbsoluteUrls(reader, writer);
              writer.flush();
            } finally {
              if (reader != null) {
                reader.close();
              }
              if (writer != null) {
                writer.close();
              }
            }
          }
          return convertFileToTempBlob(tempFile, repository);
        }
      }
      finally {
        delete(tempFile);
        delete(artifactsTempFile);
      }
    }
    catch (IOException | XMLStreamException ex) {
      log.error("Failed to fix absolute urls for file with extension {} and blob {} with reason: {} ",
          ex, artifact.getBlob().getId(), ex);
      return artifact;
    }
  }

  private InputStream xmlInputStream(final TempBlob artifact, final String extension, final Path artifactsTempFile)
      throws IOException
  {
    InputStream in;
    if (isCompressedWithFormat(extension, JAR)) {
      extractArtifactsFromJarToTempFile(artifact.get(), artifactsTempFile);
      in = Files.newInputStream(artifactsTempFile);
    }
    else if (isCompressedWithFormat(extension, XZ)) {
      in = new XZCompressorInputStream(artifact.get());
    }
    else {
      in = artifact.get();
    }
    return new BufferedInputStream(in);
  }

  private void extractArtifactsFromJarToTempFile(final InputStream in, final Path jarEntry) throws IOException {
    ZipEntry zipEntry;
    try (ZipInputStream zipInputStream = new ZipInputStream(in)) {
      try (OutputStream outputStream = newOutputStream(jarEntry)) {
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
          if (zipEntry.getName().equals(ARTIFACTS_XML)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = zipInputStream.read(buffer)) != -1) {
              outputStream.write(buffer, 0, len);
            }
          }
        }
      }
    }
  }

  private OutputStream xmlOutputStream(final String extension, final Path tempFile) throws IOException {
    OutputStream result = newOutputStream(tempFile);
    try {
      if (isCompressedWithFormat(extension, XZ)) {
        result = new XZCompressorOutputStream(result);
      }
      else if (isCompressedWithFormat(extension, JAR)) {
        ZipOutputStream zipOutputStream = new ZipOutputStream(result);
        zipOutputStream.putNextEntry(new ZipEntry(ARTIFACTS_XML));
        result = zipOutputStream;
      }
    }
    catch (IOException ex) {
      result.close();
      throw ex;
    }
    return new BufferedOutputStream(result);
  }

  private boolean isCompressedWithFormat(final String extension, final String format) {
    return extension.equals(format);
  }

  private void streamXmlToWriterAndRemoveAbsoluteUrls(final XMLEventReader reader,
                                                      final XMLEventWriter writer) throws XMLStreamException
  {
    final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
    XMLEvent previous = null;

    // We need to buffer events so that we can also update properties size when removing the mirrorsUrl property
    List<XMLEvent> buffer = new ArrayList<>();
    while (reader.hasNext()) {
      XMLEvent event = reader.nextEvent();

      // If xml tag is "properties" then start buffering. If started buffering then keep buffering until the buffer
      // is cleared
      if (!buffer.isEmpty() || isStartTagWithName(event, "properties")) {
        //Exclude the mirrorsURL property
        if (!(isMirrorsUrlProperty(event) || isMirrorsUrlProperty(previous))) {
          buffer.add(event);
        }
      }

      if (buffer.isEmpty()) {
        writer.add(event);
      }

      // When reached end of <properties> section, update count and flush buffer to writer.
      if (isEndTagWithName(event, "properties")) {
        for (XMLEvent xmlEvent : buffer) {
          if (isStartTagWithName(xmlEvent, "properties")) {
            xmlEvent = updateSize(xmlEvent.asStartElement(), countPropertyTags(buffer), eventFactory);
          }
          writer.add(xmlEvent);
        }
        buffer.clear();
      }

      previous = event;
    }
  }

  private XMLEvent updateSize(final StartElement tag,
                              final int size,
                              final XMLEventFactory eventFactory)
  {
    Iterator updatedAttributes = updateSizeAttribute(tag.getAttributes(), size, eventFactory);
    return eventFactory.createStartElement(tag.getName().getPrefix(),
        tag.getName().getNamespaceURI(),
        tag.getName().getLocalPart(),
        updatedAttributes,
        null);
  }

  private Iterator updateSizeAttribute(final Iterator<Attribute> attributes,
                                       final int size,
                                       final XMLEventFactory eventFactory)
  {
    List<Attribute> processedAttributes = new ArrayList<>();
    while (attributes.hasNext()) {
      Attribute attribute = attributes.next();
      if (attribute.getName().getLocalPart().equals("size")) {
        Attribute sizeAttribute = eventFactory.createAttribute(attribute.getName(), Integer.toString(size));
        if (sizeAttribute != null) {
          processedAttributes.add(sizeAttribute);
        }
      }
      else {
        processedAttributes.add(attribute);
      }
    }
    return processedAttributes.iterator();
  }

  private int countPropertyTags(final List<XMLEvent> buffer) {
    int count = 0;
    for (XMLEvent xmlEvent : buffer) {
      if (isStartTagWithName(xmlEvent, "property")) {
        count++;
      }
    }
    return count;
  }

  private boolean isStartTagWithName(@Nullable final XMLEvent tag, final String name) {
    if (tag != null && tag.getEventType() == START_ELEMENT) {
      StartElement startElement = tag.asStartElement();
      if (startElement.getName().getLocalPart().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isEndTagWithName(@Nullable final XMLEvent tag, final String name) {
    if (tag != null && tag.getEventType() == END_ELEMENT) {
      EndElement endElement = tag.asEndElement();
      if (endElement.getName().getLocalPart().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isMirrorsUrlProperty(@Nullable final XMLEvent event) {
    if (isStartTagWithName(event, "property")) {
      Attribute name = event.asStartElement().getAttributeByName(new QName("name"));
      if (name.getValue().equals(MIRRORS_URL_PROPERTY)) {
        return true;
      }
    }
    return false;
  }

  private TempBlob convertFileToTempBlob(final Path tempFile, final Repository repository) throws IOException {
    StorageFacet storageFacet = repository.facet(StorageFacet.class);
    try (InputStream tempFileInputStream = newInputStream(tempFile)) {
      return storageFacet.createTempBlob(tempFileInputStream, HASH_ALGORITHMS);
    }
  }
}
