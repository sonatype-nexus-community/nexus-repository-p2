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

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.repository.p2.internal.exception.InvalidMetadataException;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.storage.TempBlob;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static org.sonatype.goodies.testsupport.hamcrest.DiffMatchers.equalTo;

public class AttributesParserFeatureXmlTest
    extends TestSupport
{
  private AttributesParserFeatureXml underTest;

  @Mock
  private TempBlob tempBlob;

  private static final String JAR_NAME = "org.eclipse.core.runtime.feature_1.2.100.v20170912-1859.jar";

  private static final String JAR_NAME_WITH_MANIFEST = "org.tigris.subversion.clientadapter.svnkit_1.7.5.jar";

  private static final String JAR_SOURCES_NAME = "org.eclipse.e4.tools.emf.editor3x.source_4.7.0.v20170712-1432.jar";

  private static final String NON_P2_JAR = "org.apache.karaf.http.core-3.0.0.rc1.jar.zip";

  private static final String JAR_XML_COMPONENT_VERSION = "1.2.100.v20170912-1859";

  private static final String JAR_MANIFEST_COMPONENT_VERSION = "1.7.5";

  private static final String JAR_SOURCES_COMPONENT_VERSION = "4.7.0.v20170712-1432";

  private static final String XML_PLUGIN_NAME = "Eclipse Core Runtime Infrastructure";

  private static final String MANIFEST_PLUGIN_NAME = "SVNKit Client Adapter";

  private static final String SOURCES_PLUGIN_NAME = "Editor3x Source";

  @Before
  public void setUp() throws Exception {
    underTest = new AttributesParserFeatureXml(new TempBlobConverter());
  }

  @Test
  public void getVersionFromJarInputStream() throws InvalidMetadataException {
    when(tempBlob.get()).thenAnswer((a) -> getClass().getResourceAsStream(JAR_NAME));

    P2Attributes attributesFromJarFile = underTest.getAttributesFromBlob(tempBlob, "jar").get();
    assertThat(attributesFromJarFile.getComponentVersion(), is(equalTo(JAR_XML_COMPONENT_VERSION)));
    assertThat(attributesFromJarFile.getPluginName(), is(equalTo(XML_PLUGIN_NAME)));
  }

  @Test
  public void getEmptyAttributesFromJarInputStream() throws InvalidMetadataException {
    when(tempBlob.get()).thenAnswer((a) -> getClass().getResourceAsStream(JAR_NAME_WITH_MANIFEST));
    assertThat(underTest.getAttributesFromBlob(tempBlob, "jar").isPresent(), is(false));
  }

  @Test (expected = InvalidMetadataException.class)
  public void getNoneP2FileFromJarInputStream() throws InvalidMetadataException {
    when(tempBlob.get()).thenAnswer((a) -> getClass().getResourceAsStream(NON_P2_JAR));
    underTest.getAttributesFromBlob(tempBlob, "zip");
  }

  private P2Attributes getAttributesFromJarFile(final TempBlob tempBlob, final String jar) throws InvalidMetadataException
  {
    return underTest.getAttributesFromBlob(tempBlob, jar).orElseThrow(() -> new AssertionError("No Attributes found to use"));
  }
}
