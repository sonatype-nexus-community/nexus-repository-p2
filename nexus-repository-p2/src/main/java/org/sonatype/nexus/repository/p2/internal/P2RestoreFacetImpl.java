package org.sonatype.nexus.repository.p2.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Named;

import org.sonatype.nexus.common.collect.AttributesMap;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.p2.P2Facet;
import org.sonatype.nexus.repository.p2.P2RestoreFacet;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.AssetBlob;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.Query;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.transaction.TransactionalTouchBlob;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.transaction.UnitOfWork;

import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.ARTIFACTS_NAME;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.COMPOSITE_ARTIFACTS;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.COMPOSITE_CONTENT;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.CONTENT_NAME;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.INDEX_EXTENSION;
import static org.sonatype.nexus.repository.storage.MetadataNodeEntityAdapter.P_NAME;

/**
 * @since 0.next
 */
@Named
public class P2RestoreFacetImpl
    extends FacetSupport
    implements P2RestoreFacet
{
  @Override
  @TransactionalTouchBlob
  public void restore(final AssetBlob assetBlob, final String path) throws IOException {
    StorageTx tx = UnitOfWork.currentTx();
    P2Facet facet = facet(P2Facet.class);

    Asset asset;
    if (componentRequired(path)) {
      Map<String, String> attributes = new HashMap<>();

      try (InputStream is = assetBlob.getBlob().getInputStream()) {
        // TODO Implement
        //  attributes = extractDescriptionFromArchive(path, is);
      }
      Component component = facet.findOrCreateComponent(tx, path, attributes);
      asset = facet.findOrCreateAsset(tx, component, path, attributes);
    }
    else {
      asset = facet.findOrCreateAsset(tx, path);
    }
    tx.attachBlob(asset, assetBlob);

    Content.applyToAsset(asset, Content.maintainLastModified(asset, new AttributesMap()));
    tx.saveAsset(asset);
  }

  @Override
  @TransactionalTouchBlob
  public boolean assetExists(final String path) {
    final StorageTx tx = UnitOfWork.currentTx();
    return tx.findAssetWithProperty(P_NAME, path, tx.findBucket(getRepository())) != null;
  }

  @Override
  public boolean componentRequired(final String name) {
    return !(name.startsWith(CONTENT_NAME) || name.startsWith(ARTIFACTS_NAME) || name.startsWith(COMPOSITE_ARTIFACTS) ||
        name.startsWith(COMPOSITE_CONTENT) || name.matches(INDEX_EXTENSION) || name.startsWith("site."));
  }

  @Override
  public Query getComponentQuery(final Map<String, String> attributes) {
    //TODO CHANEGE
    //return Query.builder().where(P_NAME).eq(attributes.get(P2Attributes.P_PACKAGE))
    //    .and(P_VERSION).eq(attributes.get(P2Attributes.P_VERSION)).build();
    return Query.builder().build();
  }
}
