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
package org.sonatype.nexus.repository.p2.internal.proxy;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.repository.cache.CacheInfo;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.repository.p2.P2Facet;
import org.sonatype.nexus.repository.p2.internal.AssetKind;
import org.sonatype.nexus.repository.p2.internal.metadata.ArtifactsXmlAbsoluteUrlRemover;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.p2.internal.util.P2TempBlobUtils;
import org.sonatype.nexus.repository.proxy.ProxyFacet;
import org.sonatype.nexus.repository.proxy.ProxyFacetSupport;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Bucket;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.storage.TempBlob;
import org.sonatype.nexus.repository.transaction.TransactionalStoreBlob;
import org.sonatype.nexus.repository.transaction.TransactionalTouchBlob;
import org.sonatype.nexus.repository.transaction.TransactionalTouchMetadata;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.transaction.UnitOfWork;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPONENT_BINARY;
import static org.sonatype.nexus.repository.p2.internal.P2FacetImpl.HASH_ALGORITHMS;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.binaryPath;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.extension;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.matcherState;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.maybePath;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.name;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.path;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.toP2Attributes;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.toP2AttributesBinary;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.unescapePathToUri;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.version;
import static org.sonatype.nexus.repository.storage.AssetEntityAdapter.P_ASSET_KIND;

/**
 * P2 {@link ProxyFacet} implementation.
 *
 * @since 0.next
 */
@Named
public class P2ProxyFacetImpl
    extends ProxyFacetSupport
{
  private static final String COMPOSITE_ARTIFACTS = "compositeArtifacts";

  private static final String COMPOSITE_CONTENT = "compositeContent";

  private final P2TempBlobUtils p2TempBlobUtils;

  private final ArtifactsXmlAbsoluteUrlRemover xmlRewriter;

  @Inject
  public P2ProxyFacetImpl(final P2TempBlobUtils p2TempBlobUtils,
                          final ArtifactsXmlAbsoluteUrlRemover xmlRewriter)
  {
    this.p2TempBlobUtils = checkNotNull(p2TempBlobUtils);
    this.xmlRewriter = checkNotNull(xmlRewriter);
  }

  // HACK: Workaround for known CGLIB issue, forces an Import-Package for org.sonatype.nexus.repository.config
  @Override
  protected void doValidate(final Configuration configuration) throws Exception {
    super.doValidate(configuration);
  }

  @Nullable
  @Override
  protected Content getCachedContent(final Context context) {
    AssetKind assetKind = context.getAttributes().require(AssetKind.class);
    TokenMatcher.State matcherState = matcherState(context);
    switch (assetKind) {
      case ARTIFACT_JAR:
      case ARTIFACT_XML:
      case ARTIFACT_XML_XZ:
      case CONTENT_JAR:
      case CONTENT_XML:
      case CONTENT_XML_XZ:
      case P2_INDEX:
      case COMPOSITE_ARTIFACTS_JAR:
      case COMPOSITE_CONTENT_JAR:
      case COMPOSITE_ARTIFACTS_XML:
      case COMPOSITE_CONTENT_XML:
        return getAsset(maybePath(matcherState));
      case COMPONENT_PLUGINS:
      case COMPONENT_FEATURES:
        return getAsset(path(path(matcherState), name(matcherState), extension(matcherState)));
      case COMPONENT_BINARY:
        return getAsset(binaryPath(path(matcherState), name(matcherState), version(matcherState)));
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    AssetKind assetKind = context.getAttributes().require(AssetKind.class);
    TokenMatcher.State matcherState = matcherState(context);
    switch (assetKind) {
      case ARTIFACT_JAR:
      case ARTIFACT_XML:
      case ARTIFACT_XML_XZ:
      case CONTENT_JAR:
      case CONTENT_XML:
      case CONTENT_XML_XZ:
      case P2_INDEX:
      case COMPOSITE_ARTIFACTS_JAR:
      case COMPOSITE_CONTENT_JAR:
      case COMPOSITE_ARTIFACTS_XML:
      case COMPOSITE_CONTENT_XML:
        return putMetadata(maybePath(matcherState),
            content,
            assetKind);
      case COMPONENT_PLUGINS:
      case COMPONENT_FEATURES:
        return putComponent(toP2Attributes(matcherState), content, assetKind);
      case COMPONENT_BINARY:
        return putBinary(toP2AttributesBinary(matcherState), content);
      default:
        throw new IllegalStateException();
    }
  }

  private Content putMetadata(final String path, final Content content, final AssetKind assetKind) throws IOException {
    StorageFacet storageFacet = facet(StorageFacet.class);
    try (TempBlob tempBlob = storageFacet.createTempBlob(content.openInputStream(), HASH_ALGORITHMS)) {
      return removeMirrorUrlFromArtifactsAndSaveMetadataAsAsset(path, tempBlob, content, assetKind);
    }
  }

  private Content removeMirrorUrlFromArtifactsAndSaveMetadataAsAsset(final String path,
                                                                     final TempBlob metadataContent,
                                                                     final Payload payload,
                                                                     final AssetKind assetKind) throws IOException
  {

    Content resultContent;
    switch (assetKind) {
      case ARTIFACT_XML:
        try (TempBlob newMetadataContent = xmlRewriter
            .removeMirrorUrlFromArtifactsXml(metadataContent, getRepository(), "xml")) {
          resultContent = saveMetadataAsAsset(path, newMetadataContent, payload, assetKind);
        }
        break;
      case ARTIFACT_JAR:
        try (TempBlob newMetadataContent = xmlRewriter
            .removeMirrorUrlFromArtifactsXml(metadataContent, getRepository(), "jar")) {
          resultContent = saveMetadataAsAsset(path, newMetadataContent, payload, assetKind);
        }
        break;
      case ARTIFACT_XML_XZ:
        try (TempBlob newMetadataContent = xmlRewriter
            .removeMirrorUrlFromArtifactsXml(metadataContent, getRepository(), "xml.xz")) {
          resultContent = saveMetadataAsAsset(path, newMetadataContent, payload, assetKind);
        }
        break;
      case COMPOSITE_ARTIFACTS_JAR:
        try (TempBlob newMetadataContent = xmlRewriter
            .editUrlPathForCompositeRepository(metadataContent, getRemoteUrl(), getRepository(), COMPOSITE_ARTIFACTS,
                "jar")) {
          resultContent = saveMetadataAsAsset(path, newMetadataContent, payload, assetKind);
        }
        break;
      case COMPOSITE_CONTENT_JAR:
        try (TempBlob newMetadataContent = xmlRewriter
            .editUrlPathForCompositeRepository(metadataContent, getRemoteUrl(), getRepository(), COMPOSITE_CONTENT,
                "jar")) {
          resultContent = saveMetadataAsAsset(path, newMetadataContent, payload, assetKind);
        }
        break;
      case COMPOSITE_CONTENT_XML:
        try (TempBlob newMetadataContent = xmlRewriter
            .editUrlPathForCompositeRepository(metadataContent, getRemoteUrl(), getRepository(), COMPOSITE_CONTENT,
                "xml")) {
          resultContent = saveMetadataAsAsset(path, newMetadataContent, payload, assetKind);
        }
        break;
      case COMPOSITE_ARTIFACTS_XML:
        try (TempBlob newMetadataContent = xmlRewriter
            .editUrlPathForCompositeRepository(metadataContent, getRemoteUrl(), getRepository(), COMPOSITE_ARTIFACTS,
                "xml")) {
          resultContent = saveMetadataAsAsset(path, newMetadataContent, payload, assetKind);
        }
        break;
      default:
        resultContent = saveMetadataAsAsset(path, metadataContent, payload, assetKind);
        break;
    }

    return resultContent;
  }

  @TransactionalStoreBlob
  protected Content saveMetadataAsAsset(final String assetPath,
                                        final TempBlob metadataContent,
                                        final Payload payload,
                                        final AssetKind assetKind) throws IOException
  {
    StorageTx tx = UnitOfWork.currentTx();
    Bucket bucket = tx.findBucket(getRepository());

    Asset asset = facet(P2Facet.class).findAsset(tx, bucket, assetPath);
    if (asset == null) {
      asset = tx.createAsset(bucket, getRepository().getFormat());
      asset.name(assetPath);
      asset.formatAttributes().set(P_ASSET_KIND, assetKind.name());
    }

    return facet(P2Facet.class).saveAsset(tx, asset, metadataContent, payload);
  }

  private Content putBinary(final P2Attributes p2attributes,
                            final Content content) throws IOException
  {
    StorageFacet storageFacet = facet(StorageFacet.class);
    try (TempBlob tempBlob = storageFacet.createTempBlob(content.openInputStream(), HASH_ALGORITHMS)) {
      return doPutBinary(p2attributes, tempBlob, content);
    }
  }

  @TransactionalStoreBlob
  private Content doPutBinary(final P2Attributes p2Attributes,
                              final TempBlob componentContent,
                              final Payload payload) throws IOException
  {
    return facet(P2Facet.class).doCreateOrSaveComponent(p2Attributes, componentContent, payload, COMPONENT_BINARY);
  }

  @TransactionalStoreBlob
  private Content putComponent(final P2Attributes p2Attributes,
                               final Content content,
                               final AssetKind assetKind) throws IOException
  {
    StorageFacet storageFacet = facet(StorageFacet.class);
    try (TempBlob tempBlob = storageFacet.createTempBlob(content.openInputStream(), HASH_ALGORITHMS)) {
      return doPutComponent(p2Attributes, tempBlob, content, assetKind);
    }
  }

  private Content doPutComponent(P2Attributes p2Attributes,
                                 final TempBlob componentContent,
                                 final Payload payload,
                                 final AssetKind assetKind) throws IOException
  {
    p2Attributes = p2TempBlobUtils.mergeAttributesFromTempBlob(componentContent, p2Attributes);

    return facet(P2Facet.class).doCreateOrSaveComponent(p2Attributes, componentContent, payload, assetKind);
  }

  @Override
  protected void indicateVerified(final Context context, final Content content, final CacheInfo cacheInfo)
  {
    setCacheInfo(content, cacheInfo);
  }

  @TransactionalTouchMetadata
  public void setCacheInfo(final Content content, final CacheInfo cacheInfo) {
    StorageTx tx = UnitOfWork.currentTx();
    Asset asset = Content.findAsset(tx, tx.findBucket(getRepository()), content);
    if (asset == null) {
      log.debug(
          "Attempting to set cache info for non-existent P2 asset {}", content.getAttributes().require(Asset.class)
      );
      return;
    }
    log.debug("Updating cacheInfo of {} to {}", asset, cacheInfo);
    CacheInfo.applyToAsset(asset, cacheInfo);
    tx.saveAsset(asset);
  }

  @TransactionalTouchBlob
  protected Content getAsset(final String name) {
    StorageTx tx = UnitOfWork.currentTx();

    Asset asset = facet(P2Facet.class).findAsset(tx, tx.findBucket(getRepository()), name);
    if (asset == null) {
      return null;
    }
    if (asset.markAsDownloaded()) {
      tx.saveAsset(asset);
    }
    return facet(P2Facet.class).toContent(asset, tx.requireBlob(asset.requireBlobRef()));
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    String path = context.getRequest().getPath().substring(1);
    return unescapePathToUri(path);
  }
}
