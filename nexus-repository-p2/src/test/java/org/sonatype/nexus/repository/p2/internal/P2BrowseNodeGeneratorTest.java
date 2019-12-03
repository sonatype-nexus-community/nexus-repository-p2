package org.sonatype.nexus.repository.p2.internal;

import java.util.Arrays;
import java.util.List;

import org.sonatype.nexus.repository.browse.BrowsePaths;
import org.sonatype.nexus.repository.browse.BrowseTestSupport;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Component;

import org.junit.Test;

public class P2BrowseNodeGeneratorTest
    extends BrowseTestSupport
{
  private P2BrowseNodeGenerator generator = new P2BrowseNodeGenerator();

  @Test
  public void computeComponentPath() {

    Component component = createComponent("SVNKit Client Adapter (Not required)", "org.tigris.subversion.clientadapter.svnkit", "1.7.5");
    Asset asset = createAsset("features/assetName");

    List<BrowsePaths> paths = generator.computeAssetPaths(asset, component);
    assertPaths(Arrays.asList("SVNKit Client Adapter (Not required)", "1.7.5", "features", "assetName"), paths, false);
  }
}
