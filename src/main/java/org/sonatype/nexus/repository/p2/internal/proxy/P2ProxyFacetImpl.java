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
import java.util.jar.JarInputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.repository.cache.CacheInfo;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.repository.p2.internal.metadata.ArtifactsXmlAbsoluteUrlRemover;
import org.sonatype.nexus.repository.p2.internal.util.JarParser;
import org.sonatype.nexus.repository.p2.internal.util.P2DataAccess;
import org.sonatype.nexus.repository.p2.internal.util.P2PathUtils;
import org.sonatype.nexus.repository.proxy.ProxyFacet;
import org.sonatype.nexus.repository.proxy.ProxyFacetSupport;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Bucket;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.storage.TempBlob;
import org.sonatype.nexus.repository.transaction.TransactionalStoreBlob;
import org.sonatype.nexus.repository.transaction.TransactionalTouchBlob;
import org.sonatype.nexus.repository.transaction.TransactionalTouchMetadata;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.p2.internal.AssetKind;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.transaction.UnitOfWork;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.repository.storage.AssetEntityAdapter.P_ASSET_KIND;

/**
 * P2 {@link ProxyFacet} implementation.
 */
@Named
public class P2ProxyFacetImpl
    extends ProxyFacetSupport
{
  private final P2PathUtils p2PathUtils;
  private final P2DataAccess p2DataAccess;
  private final ArtifactsXmlAbsoluteUrlRemover xmlRewriter;
  private final JarParser jarParser;

  @Inject
  public P2ProxyFacetImpl(final P2PathUtils p2PathUtils,
                          final P2DataAccess p2DataAccess,
                          final ArtifactsXmlAbsoluteUrlRemover xmlRewriter,
                          final JarParser jarParser)
  {
    this.p2PathUtils = checkNotNull(p2PathUtils);
    this.p2DataAccess = checkNotNull(p2DataAccess);
    this.xmlRewriter = checkNotNull(xmlRewriter);
    this.jarParser = checkNotNull(jarParser);
  }

  // HACK: Workaround for known CGLIB issue, forces an Import-Package for org.sonatype.nexus.repository.config
  @Override
  protected void doValidate(final Configuration configuration) throws Exception {
    super.doValidate(configuration);
  }

  @Nullable
  @Override
  protected Content getCachedContent(final Context context) throws IOException {
    AssetKind assetKind = context.getAttributes().require(AssetKind.class);
    TokenMatcher.State matcherState = p2PathUtils.matcherState(context);
    switch(assetKind) {
      case ARTIFACT_JAR:
      case ARTIFACT_XML:
      case ARTIFACT_XML_XZ:
      case CONTENT_JAR:
      case CONTENT_XML:
      case CONTENT_XML_XZ:
      case P2_INDEX:
        return getAsset(p2PathUtils.path(p2PathUtils.path(matcherState), p2PathUtils.filename(matcherState)));
      case COMPONENT_PLUGINS:
      case COMPONENT_FEATURES:
        return getAsset(p2PathUtils.path(p2PathUtils.path(matcherState), p2PathUtils.name(matcherState)));
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  protected Content store(final Context context, final Content content) throws IOException {
    AssetKind assetKind = context.getAttributes().require(AssetKind.class);
    TokenMatcher.State matcherState = p2PathUtils.matcherState(context);
    switch(assetKind) {
      case ARTIFACT_JAR:
      case ARTIFACT_XML:
      case ARTIFACT_XML_XZ:
      case CONTENT_JAR:
      case CONTENT_XML:
      case CONTENT_XML_XZ:
      case P2_INDEX:
        return putMetadata(p2PathUtils.path(p2PathUtils.path(matcherState),
            p2PathUtils.filename(matcherState)),
            content,
            assetKind);
      case COMPONENT_PLUGINS:
      case COMPONENT_FEATURES:
        return putComponent(p2PathUtils.path(matcherState),
            p2PathUtils.name(matcherState),
            p2PathUtils.extension(matcherState),
            content,
            assetKind);
      default:
        throw new IllegalStateException();
    }
  }

  private Content putMetadata(final String path, final Content content, final AssetKind assetKind) throws IOException {
    StorageFacet storageFacet = facet(StorageFacet.class);
    try (TempBlob tempBlob = storageFacet.createTempBlob(content.openInputStream(), P2DataAccess.HASH_ALGORITHMS)) {
      return removeMirrorUrlFromArtifactsAndSaveMetadataAsAsset(path, tempBlob, content, assetKind);
    }
  }

  private Content removeMirrorUrlFromArtifactsAndSaveMetadataAsAsset(final String path,
                                  final TempBlob metadataContent,
                                  final Payload payload,
                                  final AssetKind assetKind) throws IOException {

    String assetPath = path;

    if (assetKind.equals(AssetKind.ARTIFACT_XML)) {
      try (TempBlob newMetadataContent = xmlRewriter.removeMirrorUrlFromArtifactsXml(metadataContent, getRepository(), "xml")) {
        return saveMetadataAsAsset(assetPath, newMetadataContent, payload, assetKind);
      }
    }
    else if (assetKind.equals(AssetKind.ARTIFACT_JAR)) {
      try (TempBlob newMetadataContent = xmlRewriter.removeMirrorUrlFromArtifactsXml(metadataContent, getRepository(), "jar")) {
        return saveMetadataAsAsset(assetPath, newMetadataContent, payload, assetKind);
      }
    }
    else if (assetKind.equals(AssetKind.ARTIFACT_XML_XZ)) {
      try (TempBlob newMetadataContent = xmlRewriter.removeMirrorUrlFromArtifactsXml(metadataContent, getRepository(), "xml.xz")) {
        return saveMetadataAsAsset(assetPath, newMetadataContent, payload, assetKind);
      }
    }
    else {
      return saveMetadataAsAsset(assetPath, metadataContent, payload, assetKind);
    }
  }

  @TransactionalStoreBlob
  protected Content saveMetadataAsAsset(final String assetPath,
                                        final TempBlob metadataContent,
                                        final Payload payload,
                                        final AssetKind assetKind) throws IOException
  {
    StorageTx tx = UnitOfWork.currentTx();
    Bucket bucket = tx.findBucket(getRepository());

    Asset asset = p2DataAccess.findAsset(tx, bucket, assetPath);
    if (asset == null) {
      asset = tx.createAsset(bucket, getRepository().getFormat());
      asset.name(assetPath);
      asset.formatAttributes().set(P_ASSET_KIND, assetKind.name());
    }

    return p2DataAccess.saveAsset(tx, asset, metadataContent, payload);
  }

  private Content putComponent(final String path,
                               final String filename,
                               final String extension,
                               final Content content,
                               final AssetKind assetKind) throws IOException {
    StorageFacet storageFacet = facet(StorageFacet.class);
    try (TempBlob tempBlob = storageFacet.createTempBlob(content.openInputStream(), P2DataAccess.HASH_ALGORITHMS)) {
      return doPutComponent(path, filename, extension, tempBlob, content, assetKind);
    }
  }

  @TransactionalStoreBlob
  protected Content doPutComponent(final String path,
                                   final String filename,
                                   final String extension,
                                   final TempBlob componentContent,
                                   final Payload payload,
                                   final AssetKind assetKind) throws IOException {
    StorageTx tx = UnitOfWork.currentTx();
    Bucket bucket = tx.findBucket(getRepository());
    String assetPath = p2PathUtils.path(path, filename, extension);
    String version = getVersion(componentContent);

    Component component = p2DataAccess.findComponent(tx, getRepository(), filename, version);
    if (component == null) {
      component = tx.createComponent(bucket, getRepository().getFormat())
          .name(filename)
          .version(version);
    }
    tx.saveComponent(component);

    Asset asset = p2DataAccess.findAsset(tx, bucket, assetPath);
    if (asset == null) {
      asset = tx.createAsset(bucket, component);
      asset.name(assetPath);
      asset.formatAttributes().set(P_ASSET_KIND, assetKind.name());
    }
    return p2DataAccess.saveAsset(tx, asset, componentContent, payload);
  }

  @Override
  protected void indicateVerified(final Context context, final Content content, final CacheInfo cacheInfo)
      throws IOException
  {
    setCacheInfo(content, cacheInfo);
  }

  @TransactionalTouchMetadata
  public void setCacheInfo(final Content content, final CacheInfo cacheInfo) throws IOException {
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

    Asset asset = p2DataAccess.findAsset(tx, tx.findBucket(getRepository()), name);
    if (asset == null) {
      return null;
    }
    if (asset.markAsDownloaded()) {
      tx.saveAsset(asset);
    }
    return p2DataAccess.toContent(asset, tx.requireBlob(asset.requireBlobRef()));
  }

  @Override
  protected String getUrl(@Nonnull final Context context) {
    return context.getRequest().getPath().substring(1);
  }

  private String getVersion(final TempBlob tempBlob) {
    try (JarInputStream jis = new JarInputStream(tempBlob.get())) {
      return jarParser.getVersionFromJarFile(jis);
    }
    catch (Exception ex) {
      log.warn(String.format("Unable to obtain version due to the following exception: %s", ex.getMessage()));
      return "unknown";
    }
  }
}
