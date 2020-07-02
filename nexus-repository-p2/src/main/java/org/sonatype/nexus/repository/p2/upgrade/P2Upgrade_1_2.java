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
package org.sonatype.nexus.repository.p2.upgrade;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.common.app.ApplicationDirectories;
import org.sonatype.nexus.common.upgrade.DependsOn;
import org.sonatype.nexus.common.upgrade.Upgrades;
import org.sonatype.nexus.orient.DatabaseInstanceNames;
import org.sonatype.nexus.orient.DatabaseUpgradeSupport;

/**
 * Upgrade step to update {@code name} for p2 assets (remove redundant slash in start) and delete browse_node entries
 * for p2 repositories forcing them to be rebuilt by
 * {@link org.sonatype.nexus.repository.browse.internal.RebuildBrowseNodesManager}.
 *
 * @since 1.next
 */
@Named
@Singleton
@Upgrades(model = P2Model.NAME, from = "1.1", to = "1.2")
@DependsOn(model = DatabaseInstanceNames.COMPONENT, version = "1.14", checkpoint = true)
@DependsOn(model = DatabaseInstanceNames.CONFIG, version = "1.8", checkpoint = true)
public class P2Upgrade_1_2
    extends DatabaseUpgradeSupport
{
  public static final String MARKER_FILE = P2Upgrade_1_2.class.getSimpleName() + ".marker";

  private Path markerFile;

  @Inject
  public P2Upgrade_1_2(final ApplicationDirectories directories) {
    this.markerFile = new File(directories.getWorkDirectory("db"), MARKER_FILE).toPath();
  }

  @Override
  public void apply() throws Exception {
    if (!Files.exists(markerFile)) {
      Files.createFile(markerFile);
    }
  }
}
