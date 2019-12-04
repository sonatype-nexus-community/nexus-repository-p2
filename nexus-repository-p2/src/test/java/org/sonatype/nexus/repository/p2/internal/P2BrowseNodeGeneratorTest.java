package org.sonatype.nexus.repository.p2.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sonatype.nexus.repository.browse.BrowsePaths;
import org.sonatype.nexus.repository.browse.BrowseTestSupport;
import org.sonatype.nexus.repository.p2.internal.util.P2PathUtils;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Component;

import org.junit.Test;

public class P2BrowseNodeGeneratorTest
    extends BrowseTestSupport
{
  private static final List<String> COMPONENT_NAME = Arrays.asList("org", "tigris", "subversion", "clientadapter", "svnkit");

  private static final String COMPONENT_VERSION = "1.7.5";

  private static final String ASSET_NAME_PREFIX = "features";

  private static final String ASSET_NAME = "assetName";

  private P2BrowseNodeGenerator generator = new P2BrowseNodeGenerator();

  @Test
  public void computeComponentPath() {
    Component component = createComponent(String.join(".", COMPONENT_NAME), null, COMPONENT_VERSION);
    Asset asset = createAsset(ASSET_NAME_PREFIX + P2PathUtils.DIVIDER + ASSET_NAME);

    List<BrowsePaths> paths = generator.computeAssetPaths(asset, component);
    List<String> expectedResult = new ArrayList<>(COMPONENT_NAME);
    expectedResult.addAll(Arrays.asList(COMPONENT_VERSION, ASSET_NAME_PREFIX, ASSET_NAME));
    assertPaths(expectedResult, paths, false);
  }
}
