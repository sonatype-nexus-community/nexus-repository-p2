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

import java.net.URL;

import javax.annotation.Nonnull;

import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.repository.p2.internal.fixtures.RepositoryRuleP2;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.testsuite.testsupport.RepositoryITSupport;

import org.junit.Rule;

import static com.google.common.base.Preconditions.checkNotNull;

public class P2ITSupport
    extends RepositoryITSupport
{
  @Rule
  public RepositoryRuleP2 repos = new RepositoryRuleP2(() -> repositoryManager);

  @Override
  protected RepositoryRuleP2 createRepositoryRule() {
    return new RepositoryRuleP2(() -> repositoryManager);
  }

  public P2ITSupport() {
    testData.addDirectory(NexusPaxExamSupport.resolveBaseFile("target/it-resources/p2"));
  }

  @Nonnull
  protected P2Client p2Client(final Repository repository) throws Exception {
    checkNotNull(repository);
    return p2Client(repositoryBaseUrl(repository));
  }

  protected P2Client p2Client(final URL repositoryUrl) throws Exception {
    return new P2Client(
        clientBuilder(repositoryUrl).build(),
        clientContext(),
        repositoryUrl.toURI()
    );
  }
}
