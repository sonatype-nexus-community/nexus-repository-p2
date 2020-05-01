/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.p2.upgrade.tasks;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.common.app.ApplicationDirectories;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.p2.internal.P2Format;
import org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyCacheInvalidatorFacetImpl;
import org.sonatype.nexus.repository.types.ProxyType;
import org.sonatype.nexus.scheduling.Cancelable;
import org.sonatype.nexus.scheduling.TaskSupport;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Streams.stream;
import static org.sonatype.nexus.repository.p2.upgrade.P2Upgrade_1_2.MARKER_FILE;

/**
 * Delete p2 proxy after internal format changes.
 *
 * @since 1.next
 */
@Named
public class P2DeleteLegacyProxyAssetsTask
    extends TaskSupport
    implements Cancelable
{
  private final Path markerFile;

  private final RepositoryManager repositoryManager;

  @Inject
  public P2DeleteLegacyProxyAssetsTask(
      final ApplicationDirectories directories,
      final RepositoryManager repositoryManager)
  {
    this.markerFile = new File(directories.getWorkDirectory("db"), MARKER_FILE).toPath();
    this.repositoryManager = checkNotNull(repositoryManager);
  }

  @Override
  protected Object execute() throws Exception {
    stream(repositoryManager.browse())
        .peek(r -> log.debug("Looking at repository: {}", r))
        .filter(r -> r.getFormat() instanceof P2Format)
        .peek(r -> log.debug("Looking at p2 repository: {}", r))
        .filter(r -> r.getType() instanceof ProxyType)
        .peek(r -> log.debug("Found p2 proxy repository: {}", r))
        .forEach(this::deleteLegacyAssets);
    if (Files.exists(markerFile)) {
      Files.delete(markerFile);
    }
    return null;
  }

  private void deleteLegacyAssets(final Repository repository) {
    log.info("Deleting legacy assets in p2 proxy repository: {}", repository);
    repository.facet(P2ProxyCacheInvalidatorFacetImpl.class).deleteAssets();
  }

  @Override
  public String getMessage() {
    return "Delete legacy p2 proxy assets";
  }
}
