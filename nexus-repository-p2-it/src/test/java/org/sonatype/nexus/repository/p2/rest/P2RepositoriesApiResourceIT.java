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
package org.sonatype.nexus.repository.p2.rest;

import javax.ws.rs.core.Response;

import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.sonatype.nexus.common.app.BaseUrlHolder;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.p2.internal.P2Format;
import org.sonatype.nexus.repository.rest.api.model.AbstractRepositoryApiRequest;
import org.sonatype.nexus.repository.storage.StorageFacetConstants;
import org.sonatype.nexus.repository.types.ProxyType;

import static javax.ws.rs.core.Response.Status;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class P2RepositoriesApiResourceIT
    extends P2ResourceITSupport
{
  private static final String REMOTE_URL = "http://example.com";

  @Configuration
  public static Option[] configureNexus() {
    return options(
        configureNexusBase(),
        nexusFeature("org.sonatype.nexus.plugins", "nexus-repository-p2")
    );
  }

  @Before
  public void before() {
    BaseUrlHolder.set(this.nexusUrl.toString());
  }

  @Test
  public void createProxy() throws Exception {
    AbstractRepositoryApiRequest request = createProxyRequest(true);
    Response response = post(getCreateRepositoryPathUrl(ProxyType.NAME), request);
    assertEquals(response.getStatus(), Status.CREATED.getStatusCode());

    Repository repository = repositoryManager.get(request.getName());
    assertNotNull(repository);
    assertEquals(P2Format.NAME, repository.getFormat().getValue());
    assertEquals(ProxyType.NAME, repository.getType().getValue());

    repositoryManager.delete(request.getName());
  }

  @Test
  public void createProxyBadCredentials() throws Exception {
    setBadCredentials();
    AbstractRepositoryApiRequest request = createProxyRequest(true);
    Response response = post(getCreateRepositoryPathUrl(ProxyType.NAME), request);
    assertEquals(response.getStatus(), Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createProxyUnauthorized() throws Exception {
    setUnauthorizedUser();
    AbstractRepositoryApiRequest request = createProxyRequest(true);
    Response response = post(getCreateRepositoryPathUrl(ProxyType.NAME), request);
    assertEquals(response.getStatus(), Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void updateProxy() throws Exception {
    repos.createP2Proxy(PROXY_NAME, REMOTE_URL);

    AbstractRepositoryApiRequest request = createProxyRequest(false);

    Response response = put(getUpdateRepositoryPathUrl(ProxyType.NAME, PROXY_NAME), request);
    assertEquals(response.getStatus(), Status.NO_CONTENT.getStatusCode());

    Repository repository = repositoryManager.get(request.getName());
    assertNotNull(repository);

    assertThat(repository.getConfiguration().attributes(StorageFacetConstants.STORAGE)
            .get(StorageFacetConstants.STRICT_CONTENT_TYPE_VALIDATION),
        is(false));
    repositoryManager.delete(PROXY_NAME);
  }

  @Test
  public void updateProxyBadCredentials() throws Exception {
    setBadCredentials();
    AbstractRepositoryApiRequest request = createProxyRequest(false);

    Response response = put(getUpdateRepositoryPathUrl(ProxyType.NAME, PROXY_NAME), request);
    assertEquals(response.getStatus(), Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void updateProxyUnauthorized() throws Exception {
    repos.createP2Proxy(PROXY_NAME, REMOTE_URL);

    setUnauthorizedUser();
    AbstractRepositoryApiRequest request = createProxyRequest(false);

    Response response = put(getUpdateRepositoryPathUrl(ProxyType.NAME, PROXY_NAME), request);
    assertEquals(response.getStatus(), Status.FORBIDDEN.getStatusCode());
  }
}
