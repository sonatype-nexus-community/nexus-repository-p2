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

    final Asset asset = findAsset(proxyRepo, "/" + VALID_PACKAGE_URL);
    assertThat(asset.name(), is(equalTo("/" + VALID_PACKAGE_URL)));
    assertThat(asset.contentType(), is(equalTo(MIME_TYPE)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));

    final Component component = findComponent(proxyRepo, COMPONENT_NAME);
    assertThat(component.version(), is(equalTo(VERSION_NUMBER)));
    assertThat(component.group(), is(equalTo(null)));
  }

  @Test
  public void retrieveP2IndexFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(P2_INDEX)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, P2_INDEX);
    assertThat(asset.name(), is(equalTo(P2_INDEX)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
    // TODO: We discovered these files do exist, but right now it's not working, we need to fix this!
    // assertThat(status(proxyClient.get("folder/" + P2_INDEX)), is(HttpStatus.OK));
  }

  @Test
  public void retrieveCompositeArtifactsJarFromProxyWhenRemoteOnline() throws Exception {
    assertThat(status(proxyClient.get(COMPOSITE_ARTIFACTS_JAR)), is(HttpStatus.OK));

    final Asset asset = findAsset(proxyRepo, COMPOSITE_ARTIFACTS_JAR);
    assertThat(asset.name(), is(equalTo(COMPOSITE_ARTIFACTS_JAR)));
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
      String result = IOUtils.toString(entity.getContent()).replaceAll("\\s+","");

      InputStream targetStream = new FileInputStream(testData.resolveFile(ARTIFACT_WITHOUT_MIRROR_XML));
      String expected = IOUtils.toString(targetStream).replaceAll("\\s+","");

      assertThat(result, equalTo(expected));
    }
  }

  @After
  public void tearDown() throws Exception {
    server.stop();
  }
}
