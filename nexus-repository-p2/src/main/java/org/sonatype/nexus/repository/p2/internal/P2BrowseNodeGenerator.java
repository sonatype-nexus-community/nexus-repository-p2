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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.browse.BrowseNodeGenerator;
import org.sonatype.nexus.repository.browse.BrowsePaths;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Component;

import org.apache.commons.lang3.StringUtils;

import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.DIVIDER;

/**
 * @since 0.next
 */
@Singleton
@Named(P2Format.NAME)
public class P2BrowseNodeGenerator
    implements BrowseNodeGenerator
{
  @Override
  public List<BrowsePaths> computeAssetPaths(final Asset asset, @Nullable final Component component) {
    if (component != null) {
      List<BrowsePaths> paths = computeComponentPaths(asset, component);
      String assetName =
          asset.name().contains(DIVIDER) ? asset.name().substring(asset.name().lastIndexOf(DIVIDER) + 1) : asset.name();
      BrowsePaths.appendPath(paths, assetName);
      return paths;
    }

    return BrowsePaths.fromPaths(Collections.singletonList(asset.name()), false);
  }

  @Override
  public List<BrowsePaths> computeComponentPaths(final Asset asset, final Component component) {
    String pathPrefix =
        asset.name().contains(DIVIDER) ? asset.name()
            .substring(0, asset.name().lastIndexOf(DIVIDER)) : StringUtils.EMPTY;
    List<String> pathParts = new ArrayList<>();
    pathParts.add(component.name());
    pathParts.add(component.version());
    if (!pathPrefix.isEmpty()) {
      pathParts.add(pathPrefix);
    }
    return BrowsePaths.fromPaths(pathParts, true);
  }
}
