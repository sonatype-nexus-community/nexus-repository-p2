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
package org.sonatype.nexus.repository.p2.internal.cleanup;

import java.net.URL;

import javax.annotation.Nonnull;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.sonatype.goodies.httpfixture.server.fluent.Behaviours;
import org.sonatype.goodies.httpfixture.server.fluent.Server;
import org.sonatype.goodies.testsupport.group.Unstable;
import org.sonatype.nexus.common.app.BaseUrlHolder;
import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.p2.internal.P2Client;
import org.sonatype.nexus.repository.p2.internal.fixtures.RepositoryRuleP2;
import org.sonatype.nexus.testsuite.testsupport.NexusITSupport;
import org.sonatype.nexus.testsuite.testsupport.cleanup.CleanupITSupport;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.sonatype.nexus.repository.http.HttpStatus.OK;
import static org.sonatype.nexus.repository.p2.internal.P2ITSupport.PACKAGE_NAME;
import static org.sonatype.nexus.repository.p2.internal.P2ITSupport.HELP_PACKAGE_NAME;
import static org.sonatype.nexus.repository.p2.internal.P2ITSupport.VALID_HELP_PACKAGE_URL;
import static org.sonatype.nexus.repository.p2.internal.P2ITSupport.VALID_PACKAGE_URL;
import static org.sonatype.nexus.testsuite.testsupport.FormatClientSupport.status;

public class CleanupTaskP2ProxyIT
    extends CleanupITSupport
{
  private P2Client proxyClient;

  private Repository proxyRepo;

  private Server server;

  @Rule
  public RepositoryRuleP2 repos = new RepositoryRuleP2(() -> repositoryManager);

  @Configuration
  public static Option[] configureNexus() {
    return NexusPaxExamSupport.options(
        NexusITSupport.configureNexusBase(),
        nexusFeature("org.sonatype.nexus.plugins", "nexus-repository-p2")
    );
  }

  @Before
  public void setup() throws Exception {
    BaseUrlHolder.set(this.nexusUrl.toString());
    testData.addDirectory(NexusPaxExamSupport.resolveBaseFile("target/test-classes/p2"));
    server = Server.withPort(0)
        .serve("/" + VALID_PACKAGE_URL)
        .withBehaviours(Behaviours.file(testData.resolveFile(PACKAGE_NAME)))
        .serve("/" + VALID_HELP_PACKAGE_URL)
        .withBehaviours(Behaviours.file(testData.resolveFile(HELP_PACKAGE_NAME)))
        .start();

    proxyRepo = repos.createP2Proxy(testName.getMethodName(), server.getUrl().toExternalForm());
    proxyClient = p2Client(proxyRepo);
    deployArtifacts(VALID_HELP_PACKAGE_URL);
  }

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void cleanupByLastBlobUpdated() throws Exception {
    assertLastBlobUpdatedComponentsCleanedUp(proxyRepo, 1,
        () -> deployArtifacts(VALID_PACKAGE_URL), 1L);
  }

  @Test
  public void cleanupByLastDownloaded() throws Exception {
    assertLastDownloadedComponentsCleanedUp(proxyRepo, 1,
        () -> deployArtifacts(VALID_PACKAGE_URL), 1L);
  }

  @Category(Unstable.class)
  @Test
  public void cleanupByRegex() throws Exception {
    assertCleanupByRegex(proxyRepo, 1, ".*help.*\\.jar",
        () -> deployArtifacts(VALID_PACKAGE_URL), 1L);
  }

  private int deployArtifacts(final String... names) {
    try {
      for (String name : names) {
        assertThat(status(proxyClient.get(name)),
            is(OK));
      }

      return names.length;
    }
    catch (Exception e) {
      log.error("", e);
    }
    return 0;
  }

  @Nonnull
  private P2Client p2Client(final Repository repository) throws Exception {
    checkNotNull(repository);
    return p2Client(repositoryBaseUrl(repository));
  }

  private P2Client p2Client(final URL repositoryUrl) throws Exception {
    return new P2Client(
        clientBuilder(repositoryUrl).build(),
        clientContext(),
        repositoryUrl.toURI()
    );
  }
}
