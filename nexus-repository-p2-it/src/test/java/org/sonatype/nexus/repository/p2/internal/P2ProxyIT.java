/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2018-present Sonatype, Inc.
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

import org.sonatype.goodies.httpfixture.server.fluent.Behaviours;
import org.sonatype.goodies.httpfixture.server.fluent.Server;
import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.http.HttpStatus;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.testsuite.testsupport.NexusITSupport;

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

  private static final String COMPONENT_NAME = "org.eclipse.cvs.source";

  private static final String VERSION_NUMBER = "_1.4.404.v20180330-0640";

  private static final String EXTENSION = ".jar";

  private static final String PACKAGE_NAME = COMPONENT_NAME + VERSION_NUMBER + EXTENSION;

  private static final String INVALID_PACKAGE_NAME = COMPONENT_NAME + "-0.24.zip";

  private static final String BASE_PATH = "R-4.7.3a-201803300640/features/";

  private static final String BAD_PATH = "/this/path/is/not/valid";

  private static final String VALID_PACKAGE_URL = BASE_PATH + PACKAGE_NAME;

  private static final String INVALID_PACKAGE_URL = BASE_PATH + INVALID_PACKAGE_NAME;

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
        .serve(VALID_PACKAGE_URL)
        .withBehaviours(Behaviours.file(testData.resolveFile(PACKAGE_NAME)))
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
  public void retrieveZipFromProxyShouldNotWork() throws Exception {
    assertThat(status(proxyClient.get(INVALID_PACKAGE_URL)), is(HttpStatus.NOT_FOUND));
  }

  @After
  public void tearDown() throws Exception {
    server.stop();
  }
}
