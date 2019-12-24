package org.sonatype.nexus.repository.p2.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.common.collect.AttributesMap;
import org.sonatype.nexus.common.hash.MultiHashingInputStream;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.cache.CacheControllerHolder;
import org.sonatype.nexus.repository.p2.P2Facet;
import org.sonatype.nexus.repository.p2.P2RestoreFacet;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyFacetImpl;
import org.sonatype.nexus.repository.p2.internal.util.P2DataAccess;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.AssetBlob;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.Query;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.storage.TempBlob;
import org.sonatype.nexus.repository.transaction.TransactionalTouchBlob;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.transaction.UnitOfWork;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.ARTIFACTS_NAME;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.COMPOSITE_ARTIFACTS;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.COMPOSITE_CONTENT;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.CONTENT_NAME;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.INDEX_EXTENSION;
import static org.sonatype.nexus.repository.storage.AssetEntityAdapter.P_ASSET_KIND;
import static org.sonatype.nexus.repository.storage.ComponentEntityAdapter.P_VERSION;
import static org.sonatype.nexus.repository.storage.MetadataNodeEntityAdapter.P_NAME;

/**
 * @since 0.next
 */
@Named
public class P2RestoreFacetImpl
    extends FacetSupport
    implements P2RestoreFacet
{
  private final BlobStoreManager blobStoreManager;

  private final P2DataAccess p2DataAccess;

  @Inject
  public P2RestoreFacetImpl(final P2DataAccess p2DataAccess, final BlobStoreManager blobStoreManager) {
    this.p2DataAccess = p2DataAccess;
    this.blobStoreManager = blobStoreManager;
  }

  @Override
  @TransactionalTouchBlob
  public void restore(final AssetBlob assetBlob, final String path) {
    StorageTx tx = UnitOfWork.currentTx();
    P2Facet facet = facet(P2Facet.class);

    Asset asset;
    AssetKind assetKind = facet(P2Facet.class).getAssetKind(path);
    if (componentRequired(path)) {
      Map<String, String> assetAttributes = new HashMap<>();
      Map<String, String> componentAttributes = new HashMap<>();
      assetAttributes.put(P_ASSET_KIND, assetKind.name());
      componentAttributes.put(P_ASSET_KIND, assetKind.name());
      assetAttributes.put(P_NAME, path);
      if (CacheControllerHolder.CONTENT.equals(assetKind.getCacheType())) {
        componentAttributes.put(P_VERSION, path.replaceFirst(path.split("_\\d+.+")[0], "")
            .replaceFirst("_", "")
            .split("\\D+$")[0]);

        String[] namePaths = path.split("/");
        String componentName = namePaths[namePaths.length - 1].replace(componentAttributes.get(P_VERSION), "");
        componentAttributes.put(P_NAME, componentName);
      }

      Component component = facet.findOrCreateComponent(tx, path, componentAttributes);
      asset = facet.findOrCreateAsset(tx, component, path, assetAttributes);
    }
    else {
      asset = facet.findOrCreateAsset(tx, path);
      asset.attributes().set(P_ASSET_KIND, assetKind.name());
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
    AssetKind assetKind = facet(P2Facet.class).getAssetKind(name);

    return CacheControllerHolder.CONTENT.equals(assetKind.getCacheType());

    //return !(componentName.startsWith(CONTENT_NAME) || componentName.startsWith(ARTIFACTS_NAME) || componentName.startsWith(COMPOSITE_ARTIFACTS) ||
    //    componentName.startsWith(COMPOSITE_CONTENT) || componentName.matches(INDEX_EXTENSION) || componentName.startsWith("site."));
  }

  @Override
  public Query getComponentQuery(final Blob blob, final String blobName, final String blobStoreName)
      throws IOException
  {
    Map<String, String> attributes = getComponentAttributes(blob, blobName, blobStoreName);

    return Query.builder().where(P_NAME).eq(attributes.get(P_NAME))
        .and(P_VERSION).eq(attributes.get(P_VERSION)).build();
  }

  private Map<String, String> getComponentAttributes(final Blob blob, final String blobName, final String blobStoreName)
      throws IOException
  {
    BlobStore blobStore = checkNotNull(blobStoreManager.get(blobStoreName));
    InputStream inputStream = blob.getInputStream();
    MultiHashingInputStream hashingStream = new MultiHashingInputStream(P2DataAccess.HASH_ALGORITHMS, inputStream);
    TempBlob tempBlob = new TempBlob(blob, hashingStream.hashes(), true, blobStore);

    Map<String, String> attributes = new HashMap<>();
    AssetKind assetKind = facet(P2Facet.class).getAssetKind(blobName);
    if (AssetKind.COMPONENT_BINARY.equals(assetKind)) {
      //https/download.eclipse.org/technology/epp/packages/2019-12/binary/epp.package.java.executable.cocoa.macosx.x86_64_4.14.0.20191212-1200
      String[] versionPaths = blobName.split("_");
      String version = versionPaths[versionPaths.length - 1];
      String[] namePaths = blobName.split("/");
      String name = namePaths[namePaths.length - 1].replace("_" + version, "");

      attributes.put(P_NAME, name);
      attributes.put(P_VERSION, version);
    }
    else {
      String[] paths = blobName.split("\\.");

      P2Attributes p2Attributes = P2Attributes.builder().extension(paths[paths.length - 1]).build();

      P2Attributes mergedAttributes = p2DataAccess.mergeAttributesFromTempBlob(tempBlob, p2Attributes);

      attributes.put(P_NAME, mergedAttributes.getComponentName());
      attributes.put(P_VERSION, mergedAttributes.getComponentVersion());
    }

    return attributes;
  }
}
