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
package org.sonatype.nexus.repository.p2.internal;

import java.io.FileInputStream;
import java.io.InputStream;

import org.sonatype.goodies.httpfixture.server.fluent.Behaviours;
import org.sonatype.goodies.httpfixture.server.fluent.Server;
import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.http.HttpStatus;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.ComponentMaintenance;
import org.sonatype.nexus.testsuite.testsupport.NexusITSupport;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.sonatype.nexus.testsuite.testsupport.FormatClientSupport.status;

public class P2ProxyIT
    extends P2ITSupport
{
  private static final String FORMAT_NAME = "p2";

  private static final String MIME_TYPE = "application/java-archive";

  private static final String X_GZIP_TYPE = "application/x-gzip";

  private static final String COMPONENT_NAME = "org.eclipse.cvs.source";

  private static final String ARTIFACT_NAME = "artifacts";

  private static final String ARTIFACT_WITHOUT_MIRROR_NAME = "artifacts-mirror-removed";

  private static final String VERSION_NUMBER = "1.4.404.v20180330-0640";

  private static final String EXTENSION_JAR = ".jar";

  private static final String EXTENSION_XML = ".xml";

  private static final String EXTENSION_XML_XZ = ".xml.xz";

  private static final String PACKAGE_NAME = COMPONENT_NAME + "_" + VERSION_NUMBER + EXTENSION_JAR;

  private static final String ARTIFACTS_BASE_PATH = "R-4.7-201706120950/";

  private static final String ARTIFACT_JAR = ARTIFACT_NAME + EXTENSION_JAR;

  private static final String ARTIFACT_XML = ARTIFACT_NAME + EXTENSION_XML;

  private static final String ARTIFACT_XML_TEST_PATH = ARTIFACTS_BASE_PATH + ARTIFACT_XML;

  private static final String ARTIFACT_XML_XZ = ARTIFACT_NAME + EXTENSION_XML_XZ;

  private static final String ARTIFACT_XML_XZ_TEST_PATH = ARTIFACTS_BASE_PATH + ARTIFACT_XML_XZ;

  private static final String ARTIFACT_WITHOUT_MIRROR_XML = ARTIFACT_WITHOUT_MIRROR_NAME + EXTENSION_XML;

  private static final String INVALID_PACKAGE_NAME = COMPONENT_NAME + "-0.24.zip";

  private static final String PACKAGE_BASE_PATH = "R-4.7.3a-201803300640/features/";

  private static final String CONTENT_BASE_PATH = "R-4.7-201706120950/";

  private static final String CONTENT_JAR = "content.jar";

  private static final String CONTENT_XML = "content.xml";

  private static final String CONTENT_XML_XZ = "content.xml.xz";

  private static final String CONTENT_JAR_PATH = CONTENT_BASE_PATH + CONTENT_JAR;

  private static final String CONTENT_XML_XZ_PATH = CONTENT_BASE_PATH + CONTENT_XML_XZ;

  private static final String CONTENT_XML_PATH = CONTENT_BASE_PATH + CONTENT_XML;

  private static final String BAD_PATH = "/this/path/is/not/valid";

  private static final String VALID_PACKAGE_URL = PACKAGE_BASE_PATH + PACKAGE_NAME;

  private static final String P2_INDEX = "p2.index";

  private static final String COMPOSITE_ARTIFACTS_JAR = "compositeArtifacts.jar";

  private static final String COMPOSITE_ARTIFACTS_XML = "compositeArtifacts.xml";

  private static final String COMPOSITE_CONTENT_JAR = "compositeContent.jar";

  private static final String COMPOSITE_CONTENT_XML = "compositeContent.xml";

  private static final String BINARY = "org.eclipse.equinox.executable_root.cocoa.macosx.x86_64_3.7.0.v20170531-1133";

  private static final String BINARY_BASE_PATH = "R-4.7-201706120950/binary/";

  private static final String BINARY_TEST_PATH = BINARY_BASE_PATH + BINARY;

  private static final String PLUGIN = "com.google.code.atinject.tck_1.1.0.v20160901-1938.jar";

  private static final String PLUGIN_BASE_PATH = "R-4.7-201706120950/plugins/";

  private static final String PLUGIN_TEST_PATH = PLUGIN_BASE_PATH + PLUGIN;

  private static final String FEATURE = "org.eclipse.core.runtime.feature_1.2.0.v20170518-1049.jar";

  private static final String FEATURE_BASE_PATH = "R-4.7-201706120950/features/";

  private static final String FEATURE_TEST_PATH = FEATURE_BASE_PATH + FEATURE;

  private static final String INVALID_PACKAGE_URL = PACKAGE_BASE_PATH + INVALID_PACKAGE_NAME;

  private static final String ACCELEO_FEATURE_JAR = "org.eclipse.acceleo.equinox.launcher_3.7.8.201902261618.jar";

  private static final String ACCELEO_PLUGIN_GZ = "org.eclipse.acceleo.equinox.launcher_3.7.8.201902261618.jar.pack.gz";

  private static final String ACCELEO_COMPONENT_NAME = "org.eclipse.acceleo.equinox.launcher";

  private static final String ACCELEO_COMPONENT_VERSION = "3.7.8.201902261618";

  private static final String
      ACCELEO_FEATURE = ACCELEO_COMPONENT_NAME + ACCELEO_COMPONENT_VERSION + "/features/" + ACCELEO_FEATURE_JAR;

  private static final String
      ACCELEO_PLUGIN = ACCELEO_COMPONENT_NAME + ACCELEO_COMPONENT_VERSION + "/plugins/" + ACCELEO_PLUGIN_GZ;

  private P2Client proxyClient;

  private Repository proxyRepo;

  private Server server;

  @Configuration
  public static Option[] configureNexus() {
    return NexusPaxExamSupport.options(
        NexusITSupport.configureNexusBase(),
        nexusFeature("org.sonatype.nexus.plugins", "nexus-repository-p2")
    );
  }

  @Before
  public void setup() throws Exception {
    server = Server.withPort(0)
        .serve("/" + VALID_PACKAGE_URL)
        .withBehaviours(Behaviours.file(testData.resolveFile(PACKAGE_NAME)))
        .serve("/" + ARTIFACT_JAR)
        .withBehaviours(Behaviours.file(testData.resolveFile(ARTIFACT_JAR)))
        .serve("/" + ARTIFACT_XML_TEST_PATH)
        .withBehaviours(Behaviours.file(testData.resolveFile(ARTIFACT_XML)))
        .serve("/" + ARTIFACT_XML_XZ_TEST_PATH)
        .withBehaviours(Behaviours.file(testData.resolveFile(ARTIFACT_XML_XZ)))
        .serve("/" + P2_INDEX)
        .withBehaviours(Behaviours.file(testData.resolveFile(P2_INDEX)))
        .serve("/folder/" + P2_INDEX)
        .withBehaviours(Behaviours.file(testData.resolveFile(P2_INDEX)))
        .serve("/" + COMPOSITE_ARTIFACTS_JAR)
        .withBehaviours(Behaviours.file(testData.resolveFile(COMPOSITE_ARTIFACTS_JAR)))
        .serve("/" + COMPOSITE_ARTIFACTS_XML)
        .withBehaviours(Behaviours.file(testData.resolveFile(COMPOSITE_ARTIFACTS_XML)))
        .serve("/" + COMPOSITE_CONTENT_JAR)
        .withBehaviours(Behaviours.file(testData.resolveFile(COMPOSITE_CONTENT_JAR)))
        .serve("/" + COMPOSITE_CONTENT_XML)
        .withBehaviours(Behaviours.file(testData.resolveFile(COMPOSITE_CONTENT_XML)))
        .serve("/" + CONTENT_XML_PATH)
        .withBehaviours(Behaviours.file(testData.resolveFile(CONTENT_XML)))
        .serve("/" + CONTENT_JAR_PATH)
        .withBehaviours(Behaviours.file(testData.resolveFile(CONTENT_JAR)))
        .serve("/" + CONTENT_XML_XZ_PATH)
        .withBehaviours(Behaviours.file(testData.resolveFile(CONTENT_XML_XZ)))
        .serve("/" + BINARY_TEST_PATH)
        .withBehaviours(Behaviours.file(testData.resolveFile(BINARY)))
        .serve("/" + PLUGIN_TEST_PATH)
        .withBehaviours(Behaviours.file(testData.resolveFile(PLUGIN)))
        .serve("/" + FEATURE_TEST_PATH)
        .withBehaviours(Behaviours.file(testData.resolveFile(FEATURE)))
        .serve("/" + ACCELEO_COMPONENT_NAME + ACCELEO_COMPONENT_VERSION + "/features/" + ACCELEO_FEATURE_JAR)
        .withBehaviours(Behaviours.file(testData.resolveFile(ACCELEO_FEATURE_JAR)))
        .serve("/" + ACCELEO_COMPONENT_NAME + ACCELEO_COMPONENT_VERSION + "/plugins/" + ACCELEO_PLUGIN_GZ)
        .withBehaviours(Behaviours.file(testData.resolveFile(ACCELEO_PLUGIN_GZ)))
        .start();

    proxyRepo = repos.createP2Proxy("p2-test-proxy", server.getUrl().toExternalForm());
    proxyClient = p2Client(proxyRepo);
  }

  @Test
  public void unresponsiveRemoteProduces404() throws Exception {
    assertThat(status(proxyClient.get(BAD_PATH)), is(HttpStatus.NOT_FOUND));
  }

  @Test
  public void retrieveJarFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(VALID_PACKAGE_URL)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, VALID_PACKAGE_URL);
    assertThat(asset.name(), is(equalTo(VALID_PACKAGE_URL)));
    assertThat(asset.contentType(), is(equalTo(MIME_TYPE)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));

    final Component component = findComponent(proxyRepo, COMPONENT_NAME);
    assertThat(component.version(), is(equalTo(VERSION_NUMBER)));
    assertThat(component.name(), is(equalTo(COMPONENT_NAME)));
  }

  @Test
  public void retrieveP2IndexFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(P2_INDEX)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, P2_INDEX);
    assertThat(asset.name(), is(equalTo(P2_INDEX)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));

    assertThat(status(proxyClient.get("folder/" + P2_INDEX)), is(HttpStatus.OK));
  }

  @Test
  public void retrieveCompositeArtifactsJarFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(COMPOSITE_ARTIFACTS_JAR)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, COMPOSITE_ARTIFACTS_JAR);
    assertThat(asset.name(), is(equalTo(COMPOSITE_ARTIFACTS_JAR)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
  }

  @Test
  public void retrieveCompositeArtifactsXmlFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(COMPOSITE_ARTIFACTS_XML)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, COMPOSITE_ARTIFACTS_XML);
    assertThat(asset.name(), is(equalTo(COMPOSITE_ARTIFACTS_XML)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
  }

  @Test
  public void retrieveCompositeContentJarFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(COMPOSITE_CONTENT_JAR)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, COMPOSITE_CONTENT_JAR);
    assertThat(asset.name(), is(equalTo(COMPOSITE_CONTENT_JAR)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
  }

  @Test
  public void retrieveCompositeContentXmlFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(COMPOSITE_CONTENT_XML)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, COMPOSITE_CONTENT_XML);
    assertThat(asset.name(), is(equalTo(COMPOSITE_CONTENT_XML)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
  }

  @Test
  public void retrieveContentXmlFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(CONTENT_XML_PATH)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, CONTENT_XML_PATH);
    assertThat(asset.name(), is(equalTo(CONTENT_XML_PATH)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
  }

  @Test
  public void retrieveContentXmlXzFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(CONTENT_XML_XZ_PATH)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, CONTENT_XML_XZ_PATH);
    assertThat(asset.name(), is(equalTo(CONTENT_XML_XZ_PATH)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
  }

  @Test
  public void retrieveContentJarFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(CONTENT_JAR_PATH)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, CONTENT_JAR_PATH);
    assertThat(asset.name(), is(equalTo(CONTENT_JAR_PATH)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
  }

  @Test
  public void retrieveBinaryFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(BINARY_TEST_PATH)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, BINARY_TEST_PATH);
    assertThat(asset.name(), is(equalTo(BINARY_TEST_PATH)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
  }

  @Test
  public void retrievePluginFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(PLUGIN_TEST_PATH)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, PLUGIN_TEST_PATH);
    assertThat(asset.name(), is(equalTo(PLUGIN_TEST_PATH)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
  }

  @Test
  public void retrieveFeatureFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(FEATURE_TEST_PATH)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, FEATURE_TEST_PATH);
    assertThat(asset.name(), is(equalTo(FEATURE_TEST_PATH)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
  }

  @Test
  public void retrieveZipFromProxyShouldNotWork() throws Exception {
    assertThat(status(proxyClient.get(INVALID_PACKAGE_URL)), is(HttpStatus.NOT_FOUND));
  }

  @Test
  public void removeMirrorURLFromXML() throws Exception {
    assertThat(status(proxyClient.get(ARTIFACT_XML_TEST_PATH)), is(HttpStatus.OK));

    try (CloseableHttpResponse response = proxyClient.get(ARTIFACT_XML_TEST_PATH)) {
      HttpEntity entity = response.getEntity();
      String result = IOUtils.toString(entity.getContent()).replaceAll("\\s+", "");

      InputStream targetStream = new FileInputStream(testData.resolveFile(ARTIFACT_WITHOUT_MIRROR_XML));
      String expected = IOUtils.toString(targetStream).replaceAll("\\s+", "");

      assertThat(result, equalTo(expected));
    }
  }

  @Test
  public void checkComponentRemovedWhenAssetRemoved() throws Exception {
    assertThat(
        status(proxyClient.get(ACCELEO_FEATURE)),
        is(HttpStatus.OK));
    assertThat(
        status(proxyClient.get(ACCELEO_PLUGIN)),
        is(HttpStatus.OK));

    Asset assetFeature = findAsset(proxyRepo, ACCELEO_FEATURE);
    assertThat(assetFeature.name(), is(equalTo(ACCELEO_FEATURE)));
    assertThat(assetFeature.contentType(), is(equalTo(MIME_TYPE)));
    assertThat(assetFeature.format(), is(equalTo(FORMAT_NAME)));

    Asset assetPlugin = findAsset(proxyRepo, ACCELEO_PLUGIN);
    assertThat(assetPlugin.name(), is(equalTo(ACCELEO_PLUGIN)));
    assertThat(assetPlugin.contentType(), is(equalTo(X_GZIP_TYPE)));
    assertThat(assetPlugin.format(), is(equalTo(FORMAT_NAME)));

    assertThat(assetFeature.componentId(), is(equalTo(assetPlugin.componentId())));

    Component component = findComponent(proxyRepo, ACCELEO_COMPONENT_NAME);
    assertThat(component.version(), is(equalTo(ACCELEO_COMPONENT_VERSION)));
    assertThat(component.name(), is(equalTo(ACCELEO_COMPONENT_NAME)));

    ComponentMaintenance maintenanceFacet = proxyRepo.facet(ComponentMaintenance.class);

    maintenanceFacet.deleteAsset(assetFeature.getEntityMetadata().getId());

    component = findComponent(proxyRepo, ACCELEO_COMPONENT_NAME);
    assertThat(component, is(equalTo(null)));

    assetPlugin = findAsset(proxyRepo, ACCELEO_PLUGIN);
    assertThat(assetPlugin, is(equalTo(null)));
  }

  @After
  public void tearDown() throws Exception {
    server.stop();
  }
}
