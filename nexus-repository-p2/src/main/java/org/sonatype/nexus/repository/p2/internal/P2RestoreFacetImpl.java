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

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.common.collect.AttributesMap;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.cache.CacheControllerHolder;
import org.sonatype.nexus.repository.p2.P2Facet;
import org.sonatype.nexus.repository.p2.P2RestoreFacet;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.p2.internal.util.P2TempBlobUtils;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.AssetBlob;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.Query;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.storage.TempBlob;
import org.sonatype.nexus.repository.transaction.TransactionalTouchBlob;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.transaction.UnitOfWork;

import static org.sonatype.nexus.repository.p2.internal.P2FacetImpl.HASH_ALGORITHMS;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.DIVIDER;
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
  private final P2TempBlobUtils p2TempBlobUtils;

  @Inject
  public P2RestoreFacetImpl(final P2TempBlobUtils p2TempBlobUtils) {
    this.p2TempBlobUtils = p2TempBlobUtils;
  }

  @Override
  @TransactionalTouchBlob
  public void restore(final AssetBlob assetBlob, final String path) {
    StorageTx tx = UnitOfWork.currentTx();
    P2Facet facet = facet(P2Facet.class);

    Asset asset;
    if (componentRequired(path)) {
      P2Attributes attributes = P2Attributes.builder().build();
      try {
        attributes = getComponentAttributes(assetBlob.getBlob(), path, assetBlob.getBlobRef().getStore());
      }
      catch (IOException e) {
        log.error("Exception of extracting components attributes from blob {}", assetBlob);
      }

      Component component = facet.findOrCreateComponent(tx, attributes);
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
    AssetKind assetKind = facet(P2Facet.class).getAssetKind(name);

    return CacheControllerHolder.CONTENT.equals(assetKind.getCacheType());
  }

  @Override
  public Query getComponentQuery(final Blob blob, final String blobName, final String blobStoreName)
      throws IOException
  {
    P2Attributes attributes = getComponentAttributes(blob, blobName, blobStoreName);

    return Query.builder().where(P_NAME).eq(attributes.getComponentName())
        .and(P_VERSION).eq(attributes.getComponentVersion()).build();
  }

  private P2Attributes getComponentAttributes(final Blob blob, final String blobName, final String blobStoreName)
      throws IOException
  {
    P2Attributes.Builder attributes = P2Attributes.builder();
    AssetKind assetKind = facet(P2Facet.class).getAssetKind(blobName);
    if (AssetKind.COMPONENT_BINARY == assetKind) {
      //https/download.eclipse.org/technology/epp/packages/2019-12/binary/epp.package.java.executable.cocoa.macosx.x86_64_4.14.0.20191212-1200
      String[] versionPaths = blobName.split("_");
      String version = versionPaths[versionPaths.length - 1];
      String[] namePaths = blobName.split(DIVIDER);
      String name = namePaths[namePaths.length - 1].replace("_" + version, "");

      attributes.componentName(name);
      attributes.componentVersion(version);
      attributes.pluginName(name);
    }
    else {
      StorageFacet storageFacet = facet(StorageFacet.class);
      try (TempBlob tempBlob = storageFacet.createTempBlob(blob.getInputStream(), HASH_ALGORITHMS)) {
        String[] paths = blobName.split("\\.");
        P2Attributes p2Attributes = P2Attributes.builder().extension(paths[paths.length - 1]).build();
        P2Attributes mergedAttributes = p2TempBlobUtils.mergeAttributesFromTempBlob(tempBlob, p2Attributes);

        attributes.componentName(mergedAttributes.getComponentName());
        attributes.componentVersion(mergedAttributes.getComponentVersion());
        attributes.pluginName(mergedAttributes.getPluginName());
      }
    }

    attributes.assetKind(assetKind);

    return attributes.build();
  }
}
