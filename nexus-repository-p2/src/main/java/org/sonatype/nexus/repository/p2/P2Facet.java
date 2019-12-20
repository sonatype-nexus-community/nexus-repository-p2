package org.sonatype.nexus.repository.p2;

import java.util.Map;

import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.p2.internal.AssetKind;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.StorageTx;

/**
 * General P2 facet
 *
 * @since 0.next
 */
@Facet.Exposed
public interface P2Facet
    extends Facet
{
  /**
   * Find or Create Component
   *
   * @return Component
   */
  Component findOrCreateComponent(final StorageTx tx,
                                  final String path,
                                  final Map<String, String> attributes);

  /**
   * Find or Create Asset
   *
   * @return Asset
   */
  Asset findOrCreateAsset(final StorageTx tx,
                          final Component component,
                          final String path,
                          final Map<String, String> attributes);

  /**
   * Find or Create Asset without Component
   *
   * @return Asset
   */
  Asset findOrCreateAsset(final StorageTx tx, final String path);

  /**
   * Return AssetKind for current asset path/name
   *
   * @param path
   * @return AssetKind
   */
  AssetKind getAssetKind(String path);
}
