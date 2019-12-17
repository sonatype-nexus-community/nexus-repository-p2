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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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
import org.sonatype.nexus.repository.p2.internal.util.P2PathUtils;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.TempBlob;

import com.google.common.collect.Lists;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.sonatype.nexus.repository.p2.internal.util.P2DataAccess.HASH_ALGORITHMS;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.DIVIDER;

/**
 * Removes absolute URL entries from artifacts.xml
 *
 * @since 0.next
 */
@Named
@Singleton
public class ArtifactsXmlAbsoluteUrlRemover
    extends ComponentSupport
{
  private static final String XZ = "xml.xz";

  private static final String JAR = "jar";

  private static final String MIRRORS_URL_PROPERTY = "p2.mirrorsURL";

  private static final String ARTIFACTS_XML = "artifacts";

  private static final String REPOSITORY = "/repository/%s/%s";

  private static final XMLEventFactory XML_EVENT_FACTORY = XMLEventFactory.newInstance();

  private static final QName LOCATION_ATTR_NAME = new QName("location");

  public TempBlob removeMirrorUrlFromArtifactsXml(
      final TempBlob artifact,
      final Repository repository,
      final String extension) throws IOException
  {
    return transformXmlMetadata(artifact, repository, ARTIFACTS_XML, extension, this::streamXmlToWriterAndRemoveAbsoluteUrls);
  }

  public TempBlob editUrlPathForCompositeRepository(
      final TempBlob artifact,
      final URI remoteUrl,
      final Repository repository,
      final String file,
      final String extension) throws IOException
  {
    return transformXmlMetadata(artifact, repository, file, extension, (reader, writer) -> changeLocationToAbsoluteInCompositeRepository(
        reader, writer, file, remoteUrl, repository));
  }

  private TempBlob transformXmlMetadata(final TempBlob artifact,
                                        final Repository repository,
                                        final String file,
                                        final String extension,
                                        final XmlStreamTransformer transformer) throws IOException {

    Path tempFile = createTempFile("", ".xml");
    // This is required in the case that the input stream is a jar to allow us to extract a single file
    Path artifactsTempFile = createTempFile("", ".xml");
    try {
      try (InputStream xmlIn = xmlInputStream(artifact.get(), file + "." + "xml", extension, artifactsTempFile);
           OutputStream xmlOut = xmlOutputStream(file + "." + "xml", extension, tempFile)) {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        XMLEventReader reader = null;
        XMLEventWriter writer = null;
        //try-with-resources will be better here, but XMLEventReader and XMLEventWriter are not AutoCloseable
        try {
          reader = inputFactory.createXMLEventReader(xmlIn);
          writer = outputFactory.createXMLEventWriter(xmlOut);
          transformer.transform(reader, writer);
          writer.flush();
        }
        finally {
          if (reader != null) {
            reader.close();
          }
          if (writer != null) {
            writer.close();
          }
        }
      }
      catch (XMLStreamException ex) {
        log.error("Failed to rewrite metadata for file with extension {} and blob {} with reason: {} ",
            ex, artifact.getBlob().getId(), ex);
        return artifact;
      }
      return convertFileToTempBlob(tempFile, repository);
    }
    finally {
      delete(tempFile);
      delete(artifactsTempFile);
    }
  }

  private void changeLocationToAbsoluteInCompositeRepository(
      final XMLEventReader reader,
      final XMLEventWriter writer,
      final String file,
      final URI remoteUrl,
      final Repository nexusRepository) throws XMLStreamException, IOException
  {
    List<XMLEvent> buffer = new ArrayList<>();

    while (reader.hasNext()) {
      XMLEvent event = reader.nextEvent();

      if (isEndTagWithName(event, "child")) {
        continue;
      }
      if (isStartTagWithName(event, "child") || isStartTagWithName(event, "children")) {
        Optional<String> locationAttributeFromXmlEvent = getLocationAttributeFromXmlEvent((StartElement) event);
        if (!locationAttributeFromXmlEvent.isPresent()) {
          buffer.add(event);
        }
        else {
          // check location attribute for composite repository and rewrite it if needed
          List<XMLEvent> simpleRepositories = changeLocationAttribute(locationAttributeFromXmlEvent.get(), remoteUrl, nexusRepository, file).stream()
              .map(assetPath -> String.format(REPOSITORY, nexusRepository.getName(), assetPath))
              .flatMap((String locationValue) -> createLocationXmlEvent(locationValue).stream())
              .collect(Collectors.toList());
          buffer.addAll(simpleRepositories);
        }
      }
      else if (isEndTagWithName(event, "children")) {
        for (XMLEvent xmlEvent : buffer) {
          if (isStartTagWithName(xmlEvent, "children")) {
            xmlEvent = updateSize(xmlEvent.asStartElement(), countPropertyTags(buffer, "child"), XML_EVENT_FACTORY);
          }
          writer.add(xmlEvent);
        }
        buffer.clear();
      }
      else {
        writer.add(event);
      }
    }
  }

  private Optional<String> getLocationAttributeFromXmlEvent(final StartElement locationElement) {
    Attribute locationAttribute = locationElement.getAttributeByName(LOCATION_ATTR_NAME);
    if (locationAttribute != null) {
      return Optional.ofNullable(locationAttribute.getValue());
    }
    return Optional.empty();
  }

  private List<String> changeLocationAttribute(
      final String locationAttributeValue,
      final URI remoteUrl,
      final Repository nexusRepository,
      final String file) throws IOException, XMLStreamException
  {
    URI uri = URI.create(locationAttributeValue);
    return convertCompositeUrlToSimples(uri.isAbsolute() ? uri.toString() : remoteUrl.resolve(uri).toString(), nexusRepository, file).stream()
        .map(P2PathUtils::escapeUriToPath)
    .collect(Collectors.toList());
  }

  private List<XMLEvent> createLocationXmlEvent(String locationValue) {
    QName childNameAttr = new QName("child");
    return Lists.newArrayList(XML_EVENT_FACTORY.createStartElement(childNameAttr, Collections.singletonList(
        XML_EVENT_FACTORY
            .createAttribute(LOCATION_ATTR_NAME, locationValue))
            .iterator(),
        Collections.emptyIterator()), XML_EVENT_FACTORY.createEndElement(childNameAttr, Collections.singletonList(
        XML_EVENT_FACTORY
            .createAttribute(LOCATION_ATTR_NAME, locationValue))
        .iterator()));
  }

  private List<String> convertCompositeUrlToSimples(final String urlString, final Repository repository, final String file)
      throws IOException, XMLStreamException
  {
    AtomicReference<Path> compositeContentLoadedFile = new AtomicReference<>(null);
    Path compositeContentTempFileXml = null;
    try {
      String baseUrl = urlString.endsWith(DIVIDER) ? urlString : urlString + DIVIDER;
      getFileFromRemote(baseUrl, file, ".xml").ifPresent(compositeContentLoadedFile::set);
      getFileFromRemote(baseUrl, file, ".jar").ifPresent(compositeContentLoadedFile::set);

      if (compositeContentLoadedFile.get() == null) {
        return Collections.singletonList(urlString);
      }
      else {
        List<String> simpleRepositories = new ArrayList<>();
        compositeContentTempFileXml = createTempFile("", ".xml");
        try (InputStream inputStream = newInputStream(compositeContentLoadedFile.get());
             InputStream xmlIn = xmlInputStream(inputStream, file + ".xml",
                 FilenameUtils.getExtension(compositeContentLoadedFile.get().toString()), compositeContentTempFileXml)) {
          XMLInputFactory inputFactory = XMLInputFactory.newFactory();
          XMLEventReader reader = null;
          try {
            reader = inputFactory.createXMLEventReader(xmlIn);
            while (reader.hasNext()) {
              XMLEvent event = reader.nextEvent();

              if (isStartTagWithName(event, "child")) {
                Optional<String> locationAttributeFromXmlEvent = getLocationAttributeFromXmlEvent((StartElement) event);
                if (locationAttributeFromXmlEvent.isPresent()) {
                  simpleRepositories.addAll(
                      changeLocationAttribute(locationAttributeFromXmlEvent.get(), URI.create(urlString), repository,
                          file));
                }
              }
            }
          }
          finally {
            if (reader != null) {
              reader.close();
            }
          }
        }
        return simpleRepositories;
      }
    }
    catch (MalformedURLException e) {
      log.error("Invalid composite repository: {} ", urlString);
    }
    finally {
      if (compositeContentLoadedFile.get() != null) {
        delete(compositeContentLoadedFile.get());
      }
      if (compositeContentTempFileXml != null) {
        delete(compositeContentTempFileXml);
      }
    }
    return Collections.emptyList();
  }

  private Optional<Path> getFileFromRemote(
      final String baseUrl,
      final String filename,
      final String extension) throws IOException {
    Path tempFile = null;
    HttpPost post = new HttpPost(baseUrl + filename + extension);
    try (CloseableHttpClient client = HttpClients.createMinimal()) {
      HttpResponse httpResponse = client.execute(post);
      if (HttpURLConnection.HTTP_OK == httpResponse.getStatusLine().getStatusCode()) {
        tempFile = createTempFile(filename, extension);
        try (InputStream is = httpResponse.getEntity().getContent()) {
          Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }

    return Optional.ofNullable(tempFile);
  }

  private InputStream xmlInputStream(
      final InputStream inputStream,
      final String file,
      final String extension,
      final Path artifactsTempFile)
      throws IOException
  {
    InputStream in;
    if (isCompressedWithFormat(extension, JAR)) {
      extractFileFromJarToTempFile(inputStream, file, artifactsTempFile);
      in = Files.newInputStream(artifactsTempFile);
    }
    else if (isCompressedWithFormat(extension, XZ)) {
      in = new XZCompressorInputStream(inputStream);
    }
    else {
      in = inputStream;
    }
    return new BufferedInputStream(in);
  }

  private void extractFileFromJarToTempFile(final InputStream in, final String fileForExtract, final Path jarEntry)
      throws IOException
  {
    ZipEntry zipEntry;
    try (ZipInputStream zipInputStream = new ZipInputStream(in)) {
      try (OutputStream outputStream = newOutputStream(jarEntry)) {
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
          if (zipEntry.getName().equals(fileForExtract)) {
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

  private OutputStream xmlOutputStream(final String file, final String extension, final Path tempFile)
      throws IOException
  {
    OutputStream result = newOutputStream(tempFile);
    try {
      if (isCompressedWithFormat(extension, XZ)) {
        result = new XZCompressorOutputStream(result);
      }
      else if (isCompressedWithFormat(extension, JAR)) {
        ZipOutputStream zipOutputStream = new ZipOutputStream(result);
        zipOutputStream.putNextEntry(new ZipEntry(file));
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

  private void streamXmlToWriterAndRemoveAbsoluteUrls(
      final XMLEventReader reader,
      final XMLEventWriter writer) throws XMLStreamException
  {
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
            xmlEvent = updateSize(xmlEvent.asStartElement(), countPropertyTags(buffer, "property"), XML_EVENT_FACTORY);
          }
          writer.add(xmlEvent);
        }
        buffer.clear();
      }

      previous = event;
    }
  }

  private XMLEvent updateSize(
      final StartElement tag,
      final int size,
      final XMLEventFactory eventFactory)
  {
    Iterator updatedAttributes = updateSizeAttribute(tag.getAttributes(), size);
    return eventFactory.createStartElement(tag.getName().getPrefix(),
        tag.getName().getNamespaceURI(),
        tag.getName().getLocalPart(),
        updatedAttributes,
        null);
  }

  private Iterator updateSizeAttribute(
      final Iterator<Attribute> attributes,
      final int size)
  {
    List<Attribute> processedAttributes = new ArrayList<>();
    while (attributes.hasNext()) {
      Attribute attribute = attributes.next();
      if (attribute.getName().getLocalPart().equals("size")) {
        Attribute sizeAttribute = XML_EVENT_FACTORY.createAttribute(attribute.getName(), Integer.toString(size));
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

  private int countPropertyTags(final List<XMLEvent> buffer, final String tagForCounting) {
    int count = 0;
    for (XMLEvent xmlEvent : buffer) {
      if (isStartTagWithName(xmlEvent, tagForCounting)) {
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

  private interface XmlStreamTransformer {
    void transform(final XMLEventReader reader, final XMLEventWriter writer) throws XMLStreamException, IOException;
  }
}
