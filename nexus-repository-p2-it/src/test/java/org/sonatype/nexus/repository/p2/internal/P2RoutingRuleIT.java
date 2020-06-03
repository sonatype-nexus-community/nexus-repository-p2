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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.sonatype.goodies.httpfixture.server.fluent.Behaviours;
import org.sonatype.goodies.httpfixture.server.fluent.Server;
import org.sonatype.nexus.common.entity.EntityId;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.testsuite.testsupport.raw.RawClient;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.sonatype.nexus.repository.p2.P2ITConfig.configureP2Base;

/**
 * @since 0.next
 */
public class P2RoutingRuleIT extends P2RoutingRuleITSupport
{
  private static Server proxyServer;

  private Map<String, BehaviourSpy> serverPaths = new HashMap<>();

  @Configuration
  public static Option[] configureNexus() {
    return configureP2Base();
  }

  @Before
  public void startup() throws Exception {
    proxyServer = Server.withPort(0).start();
  }

  @After
  public void shutdown() throws Exception {
    if (proxyServer != null) {
      proxyServer.stop();
    }
  }

  @Test
  public void testBlockedRoutingRule() throws Exception {
    String allowedPackagePath = VALID_PACKAGE_URL;
    String blockedPackagePath = VALID_HELP_PACKAGE_URL;

    configureProxyBehaviour(allowedPackagePath, PACKAGE_NAME);
    configureProxyBehaviour(blockedPackagePath, HELP_PACKAGE_NAME);

    EntityId routingRuleId = createBlockedRoutingRule("p2-blocking-rule", ".*_2.*");
    Repository proxyRepo = repos.createP2Proxy("test-p2-blocking-proxy", proxyServer.getUrl().toString());
    RawClient client = rawClient(proxyRepo);

    attachRuleToRepository(proxyRepo, routingRuleId);

    assertGetResponseStatus(client, proxyRepo, blockedPackagePath, 403);
    assertGetResponseStatus(client, proxyRepo, allowedPackagePath, 200);
    assertNoRequests(blockedPackagePath);
  }

  private void assertGetResponseStatus(
      final RawClient client,
      final Repository repository,
      final String path,
      final int responseCode) throws IOException
  {
    try (CloseableHttpResponse response = client.get(path)) {
      StatusLine statusLine = response.getStatusLine();
      assertThat("Repository:" + repository.getName() + " Path:" + path, statusLine.getStatusCode(), is(responseCode));
    }
  }

  private void assertNoRequests(final String reqPath) {
    BehaviourSpy spy = serverPaths.get(reqPath);
    assertNotNull("Missing spy for " + reqPath, spy);
    assertFalse("Unexpected request: " + reqPath,
        spy.requestUris.stream().anyMatch(reqPath::endsWith));
  }

  private void configureProxyBehaviour(final String proxyPath, final String fileName) {
    File file = resolveTestFile(fileName);
    BehaviourSpy spy = new BehaviourSpy(Behaviours.file(file));
    proxyServer.serve("/" + proxyPath).withBehaviours(spy);
    serverPaths.put(proxyPath, spy);
  }
}
