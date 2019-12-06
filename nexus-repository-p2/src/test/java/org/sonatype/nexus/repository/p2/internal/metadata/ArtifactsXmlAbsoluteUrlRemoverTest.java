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
import java.io.OutputStream;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.TempBlob;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.xmlunit.diff.Diff;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.xmlunit.builder.DiffBuilder.compare;

public class ArtifactsXmlAbsoluteUrlRemoverTest
    extends TestSupport
{
  static final String ARTIFACTS_XML = "artifacts.xml";

  static final String ARTIFACTS_XML_XZ = "artifacts.xml.xz";

  static final String ARTIFACTS_JAR = "artifacts.jar";

  static final String ARTIFACTS_XML_WITHOUT_ABSOLUTE = "artifacts-without-absolute.xml";

  @Mock
  TempBlob artifactsXml;

  @Mock
  Repository repository;

  @Mock
  StorageFacet storageFacet;

  @Mock
  TempBlob processedArtifactsXml;

  ArtifactsXmlAbsoluteUrlRemover underTest;

  @Before
  public void setup() throws Exception {
    setupRepositoryMock();
    when(artifactsXml.getBlob()).thenReturn(mock(Blob.class)); // Allow exceptions to be logged without npe
    underTest = new ArtifactsXmlAbsoluteUrlRemover();
  }

  @Test
  public void testCheckUrl() throws Exception {
    when(artifactsXml.get()).thenReturn(getClass().getResourceAsStream("compositeArtifacts.xml"));
    TempBlob modified = underTest.editUrlPathForCompositeRepository(artifactsXml, repository, "compositeArtifacts", "xml");
    assertXmlMatches(modified.get(), "compositeArtifactsWithoutDots.xml");
  }

  @Test
  public void testCheckUrl2() throws Exception {
    when(artifactsXml.get()).thenReturn(getClass().getResourceAsStream("compositeContent.jar"));
    TempBlob modified = underTest.editUrlPathForCompositeRepository(artifactsXml, repository, "compositeContent", "jar");
    assertXmlMatches(modified.get(), "compositeArtifactsWithoutDots.xml");
  }

  @Test
  public void removeAbsoluteUrl() throws Exception {
    when(artifactsXml.get()).thenReturn(getClass().getResourceAsStream(ARTIFACTS_XML));
    TempBlob modified = underTest.removeMirrorUrlFromArtifactsXml(artifactsXml, repository, "xml");
    assertXmlMatches(modified.get(), ARTIFACTS_XML_WITHOUT_ABSOLUTE);
  }

  @Test
  public void removeAbsoluteUrlFromXz() throws Exception {
    when(artifactsXml.get()).thenReturn(getClass().getResourceAsStream(ARTIFACTS_XML_XZ));
    TempBlob modified = underTest.removeMirrorUrlFromArtifactsXml(artifactsXml, repository, "xml.xz");
    try (XZCompressorInputStream xz = new XZCompressorInputStream(modified.get())) {
      assertXmlMatches(xz, ARTIFACTS_XML_WITHOUT_ABSOLUTE);
    }
    catch (IOException e) {
      fail("Exception not expected. Is this file XZ compressed? " + e.getMessage());
    }
  }

  @Test
  public void removeAbsoluteUrlFromJar() throws Exception {
    when(artifactsXml.get()).thenReturn(getClass().getResourceAsStream(ARTIFACTS_JAR));
    TempBlob modified = underTest.removeMirrorUrlFromArtifactsXml(artifactsXml, repository, "jar");
    try (ZipArchiveInputStream zip = new ZipArchiveInputStream(modified.get())) {
      zip.getNextZipEntry();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      IOUtils.copy(zip, out);
      assertXmlMatches(new ByteArrayInputStream(out.toByteArray()), ARTIFACTS_XML_WITHOUT_ABSOLUTE);
    }
    catch (IOException e) {
      fail("Exception not expected. Is this file a JAR? " + e.getMessage());
    }
  }

  private void setupRepositoryMock() {
    when(repository.facet(StorageFacet.class)).thenReturn(storageFacet);
    when(storageFacet.createTempBlob(any(InputStream.class), any(Iterable.class))).thenAnswer(args -> {
      InputStream inputStream = (InputStream) args.getArguments()[0];
      byte[] bytes = IOUtils.toByteArray(inputStream);
      when(processedArtifactsXml.get()).thenReturn(new ByteArrayInputStream(bytes));
      return processedArtifactsXml;
    });
  }

  private void assertXmlMatches(final InputStream xml, final String expected) throws IOException {
    String expectedXml = IOUtils.toString(getClass().getResourceAsStream(expected));
    String resultXml = IOUtils.toString(xml);
    Diff diff = compare(expectedXml).withTest(resultXml).ignoreWhitespace().build();
    assertFalse("XML similar " + diff.toString(), diff.hasDifferences());
  }
}
