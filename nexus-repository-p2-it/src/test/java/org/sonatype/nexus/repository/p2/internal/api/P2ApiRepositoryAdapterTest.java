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
package org.sonatype.nexus.repository.p2.internal.api;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.common.app.BaseUrlHolder;
import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.common.event.EventManager;
import org.sonatype.nexus.repository.Format;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.Type;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.repository.manager.internal.RepositoryImpl;
import org.sonatype.nexus.repository.p2.internal.P2Format;
import org.sonatype.nexus.repository.rest.api.ApiRepositoryAdapter;
import org.sonatype.nexus.repository.rest.api.SimpleApiRepositoryAdapter;
import org.sonatype.nexus.repository.rest.api.model.AbstractApiRepository;
import org.sonatype.nexus.repository.routing.RoutingRuleStore;
import org.sonatype.nexus.repository.types.ProxyType;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class P2ApiRepositoryAdapterTest
    extends TestSupport
{
  private ApiRepositoryAdapter underTest;

  @Mock
  private RoutingRuleStore routingRuleStore;

  @Before
  public void setup() {
    underTest = new SimpleApiRepositoryAdapter(routingRuleStore);
    BaseUrlHolder.set("http://nexus-url");
  }

  @Test
  public void proxyRepository() throws Exception {
    String repositoryName = "p2-proxy";
    Format format = new P2Format();
    Type type = new ProxyType();
    boolean online = true;

    Repository repository = createRepository(repositoryName, format, type, online);
    AbstractApiRepository hostedRepository = underTest.adapt(repository);
    assertRepository(hostedRepository, repositoryName, type, online);
  }

  private static void assertRepository(
      final AbstractApiRepository repository,
      String repositoryName,
      final Type type,
      final boolean online)
  {
    assertThat(repository.getFormat(), is(P2Format.NAME));
    assertThat(repository.getName(), is(repositoryName));
    assertThat(repository.getOnline(), is(online));
    assertThat(repository.getType(), is(type.getValue()));
    assertThat(repository.getUrl(), is(BaseUrlHolder.get() + "/repository/" + repositoryName));
  }

  private static Repository createRepository(String repositoryName, Format format, Type type, boolean online)
      throws Exception
  {
    Repository repository = new RepositoryImpl(mock(EventManager.class), type, format);
    Configuration configuration = config(repositoryName, online);
    repository.init(configuration);
    return repository;
  }

  private static Configuration config(final String repositoryName, boolean online) {
    Configuration configuration = mock(Configuration.class);
    when(configuration.isOnline()).thenReturn(online);
    when(configuration.getRepositoryName()).thenReturn(repositoryName);
    when(configuration.attributes(any(String.class))).thenReturn(new NestedAttributesMap("dummy", newHashMap()));
    return configuration;
  }
}
