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
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Named;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.common.collect.AttributesMap;
import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.p2.P2Facet;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.AssetBlob;
import org.sonatype.nexus.repository.storage.Bucket;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.MetadataNodeEntityAdapter;
import org.sonatype.nexus.repository.storage.Query;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.BlobPayload;

import static java.util.Collections.singletonList;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.ARTIFACT_JAR;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.ARTIFACT_XML;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.ARTIFACT_XML_XZ;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPONENT_BINARY;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPONENT_FEATURES;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPONENT_PLUGINS;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPOSITE_ARTIFACTS_JAR;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPOSITE_ARTIFACTS_XML;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPOSITE_CONTENT_JAR;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPOSITE_CONTENT_XML;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.CONTENT_JAR;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.CONTENT_XML;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.CONTENT_XML_XZ;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.P2_INDEX;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.ARTIFACTS_NAME;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.COMPOSITE_ARTIFACTS;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.COMPOSITE_CONTENT;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.CONTENT_NAME;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.JAR_EXTENSION;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.XML_EXTENSION;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.XML_XZ_EXTENSION;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.PLUGIN_NAME;
import static org.sonatype.nexus.repository.storage.AssetEntityAdapter.P_ASSET_KIND;
import static org.sonatype.nexus.repository.storage.ComponentEntityAdapter.P_VERSION;
import static org.sonatype.nexus.repository.storage.MetadataNodeEntityAdapter.P_NAME;

/**
 * {@link P2Facet} implementation
 *
 * @since 0.next
 */
@Named
public class P2FacetImpl
    extends FacetSupport
    implements P2Facet
{
  public static final List<HashAlgorithm> HASH_ALGORITHMS = ImmutableList.of(SHA1);

  @Override
  public Component findOrCreateComponent(final StorageTx tx, final String path, final Map<String, String> attributes) {
    String name = attributes.get(P_NAME);
    String version = attributes.get(P_VERSION);

    Component component = findComponent(tx, getRepository(), name, version);
    if (component == null) {
      Bucket bucket = tx.findBucket(getRepository());
      component = tx.createComponent(bucket, getRepository().getFormat())
          .name(name)
          .version(version);
      if (attributes.containsKey(PLUGIN_NAME)) {
        component.formatAttributes().set(PLUGIN_NAME, attributes.get(PLUGIN_NAME));
      }

      if (attributes.containsKey(P_ASSET_KIND)) {
        component.formatAttributes().set(P_ASSET_KIND, attributes.get(P_ASSET_KIND));
      }

      tx.saveComponent(component);
    }

    return component;
  }

  @Override
  public Asset findOrCreateAsset(final StorageTx tx,
                                 final Component component,
                                 final String path,
                                 final Map<String, String> attributes)
  {
    Bucket bucket = tx.findBucket(getRepository());
    Asset asset = findAsset(tx, bucket, path);
    if (asset == null) {
      asset = tx.createAsset(bucket, component);
      asset.name(path);

      // TODO: Make this a bit more robust (could be problematic if keys are removed in later versions, or if keys clash)
      for (Map.Entry<String, String> attribute : attributes.entrySet()) {
        asset.formatAttributes().set(attribute.getKey(), attribute.getValue());
      }
      asset.formatAttributes().set(P_ASSET_KIND, getAssetKind(path).name());
      tx.saveAsset(asset);
    }

    return asset;
  }

  @Override
  public Asset findOrCreateAsset(final StorageTx tx, final String path) {
    Bucket bucket = tx.findBucket(getRepository());
    Asset asset = findAsset(tx, bucket, path);
    if (asset == null) {
      asset = tx.createAsset(bucket, getRepository().getFormat());
      asset.name(path);
      asset.formatAttributes().set(P_ASSET_KIND, getAssetKind(path).name());
      tx.saveAsset(asset);
    }

    return asset;
  }

  /**
   * Find a component by its name and tag (version)
   *
   * @return found component of null if not found
   */
  @Nullable
  public Component findComponent(final StorageTx tx,
                                        final Repository repository,
                                        final String name,
                                        final String version)
  {
    Iterable<Component> components = tx.findComponents(
        Query.builder()
            .where(P_NAME).eq(name)
            .and(P_VERSION).eq(version)
            .build(),
        singletonList(repository)
    );
    if (components.iterator().hasNext()) {
      return components.iterator().next();
    }
    return null;
  }

  /**
   * Find an asset by its name.
   *
   * @return found asset or null if not found
   */
  @Nullable
  public Asset findAsset(final StorageTx tx, final Bucket bucket, final String assetName) {
    return tx.findAssetWithProperty(MetadataNodeEntityAdapter.P_NAME, assetName, bucket);
  }

  /**
   * Save an asset && create blob.
   *
   * @return blob content
   */
  public Content saveAsset(final StorageTx tx,
                                  final Asset asset,
                                  final Supplier<InputStream> contentSupplier,
                                  final Payload payload) throws IOException
  {
    AttributesMap contentAttributes = null;
    String contentType = null;
    if (payload instanceof Content) {
      contentAttributes = ((Content) payload).getAttributes();
      contentType = payload.getContentType();
    }
    return saveAsset(tx, asset, contentSupplier, contentType, contentAttributes);
  }

  /**
   * Save an asset && create blob.
   *
   * @return blob content
   */
  public Content saveAsset(final StorageTx tx,
                                  final Asset asset,
                                  final Supplier<InputStream> contentSupplier,
                                  final String contentType,
                                  @Nullable final AttributesMap contentAttributes) throws IOException
  {
    Content.applyToAsset(asset, Content.maintainLastModified(asset, contentAttributes));
    AssetBlob assetBlob = tx.setBlob(
        asset, asset.name(), contentSupplier, HASH_ALGORITHMS, null, contentType, false
    );
    asset.markAsDownloaded();
    tx.saveAsset(asset);
    return toContent(asset, assetBlob.getBlob());
  }

  /**
   * Convert an asset blob to {@link Content}.
   *
   * @return content of asset blob
   */
  public Content toContent(final Asset asset, final Blob blob) {
    Content content = new Content(new BlobPayload(blob, asset.requireContentType()));
    Content.extractFromAsset(asset, HASH_ALGORITHMS, content.getAttributes());
    return content;
  }

  @Override
  public AssetKind getAssetKind(final String path) {
    AssetKind assetKind;
    if (path.matches(".*p2.index$")) {
      assetKind = P2_INDEX;
    }
    else if (path.matches(".*features\\/.*")) {
      assetKind = COMPONENT_FEATURES;
    }
    else if (path.matches(".*binary\\/.*")) {
      assetKind = COMPONENT_BINARY;
    }
    else if (path.matches(".*plugins\\/.*")) {
      assetKind = COMPONENT_PLUGINS;
    }
    else if (isPathMatch(path, COMPOSITE_ARTIFACTS, JAR_EXTENSION)) {
      assetKind = COMPOSITE_ARTIFACTS_JAR;
    }
    else if (isPathMatch(path, COMPOSITE_ARTIFACTS, XML_EXTENSION)) {
      assetKind = COMPOSITE_ARTIFACTS_XML;
    }
    else if (isPathMatch(path, COMPOSITE_CONTENT, JAR_EXTENSION)) {
      assetKind = COMPOSITE_CONTENT_JAR;
    }
    else if (isPathMatch(path, COMPOSITE_CONTENT, XML_EXTENSION)) {
      assetKind = COMPOSITE_CONTENT_XML;
    }
    else if (isPathMatch(path, CONTENT_NAME, JAR_EXTENSION)) {
      assetKind = CONTENT_JAR;
    }
    else if (isPathMatch(path, CONTENT_NAME, XML_EXTENSION)) {
      assetKind = CONTENT_XML;
    }
    else if (isPathMatch(path, CONTENT_NAME, XML_XZ_EXTENSION)) {
      assetKind = CONTENT_XML_XZ;
    }
    else if (isPathMatch(path, ARTIFACTS_NAME, JAR_EXTENSION)) {
      assetKind = ARTIFACT_JAR;
    }
    else if (isPathMatch(path, ARTIFACTS_NAME, XML_EXTENSION)) {
      assetKind = ARTIFACT_XML;
    }
    else if (isPathMatch(path, ARTIFACTS_NAME, XML_XZ_EXTENSION)) {
      assetKind = ARTIFACT_XML_XZ;
    }
    else {
      throw new RuntimeException("Asset path has not supported asset kind");
    }

    return assetKind;
  }

  private boolean isPathMatch(final String path, final String patternName, final String patternExtension) {
    return path.matches(".*" + patternName + "\\." + patternExtension + "$");
  }
}
