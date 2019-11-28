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
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.p2.internal.util.JarParser;
import org.sonatype.nexus.repository.p2.internal.util.P2DataAccess;
import org.sonatype.nexus.repository.p2.internal.util.P2PathUtils;
import org.sonatype.nexus.repository.p2.internal.util.TempBlobConverter;
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

import com.google.common.annotations.VisibleForTesting;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPONENT_BINARY;
import static org.sonatype.nexus.repository.p2.internal.util.P2DataAccess.HASH_ALGORITHMS;
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
  private final TempBlobConverter tempBlobConverter;

  @Inject
  public P2ProxyFacetImpl(final P2PathUtils p2PathUtils,
                          final P2DataAccess p2DataAccess,
                          final ArtifactsXmlAbsoluteUrlRemover xmlRewriter,
                          final JarParser jarParser,
                          final TempBlobConverter tempBlobConverter)
  {
    this.p2PathUtils = checkNotNull(p2PathUtils);
    this.p2DataAccess = checkNotNull(p2DataAccess);
    this.xmlRewriter = checkNotNull(xmlRewriter);
    this.jarParser = checkNotNull(jarParser);
    this.tempBlobConverter = checkNotNull(tempBlobConverter);
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
      case COMPOSITE_ARTIFACTS_JAR:
      case COMPOSITE_CONTENT_JAR:
      case COMPOSITE_ARTIFACTS_XML:
      case COMPOSITE_CONTENT_XML:
        return getAsset(p2PathUtils.maybePath(matcherState));
      case COMPONENT_PLUGINS:
      case COMPONENT_FEATURES:
        return getAsset(p2PathUtils.path(p2PathUtils.path(matcherState), p2PathUtils.name(matcherState)));
      case COMPONENT_BINARY:
        return getAsset(p2PathUtils.binaryPath(p2PathUtils.path(matcherState), p2PathUtils.name(matcherState), p2PathUtils.version(matcherState)));
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
      case COMPOSITE_ARTIFACTS_JAR:
      case COMPOSITE_CONTENT_JAR:
      case COMPOSITE_ARTIFACTS_XML:
      case COMPOSITE_CONTENT_XML:
        return putMetadata(p2PathUtils.maybePath(matcherState),
            content,
            assetKind);
      case COMPONENT_PLUGINS:
      case COMPONENT_FEATURES:
        return putComponent(p2PathUtils.toP2Attributes(matcherState), content, assetKind);
      case COMPONENT_BINARY:
        return putBinary(p2PathUtils.toP2AttributesBinary(matcherState), content);
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

  private Content putBinary(final P2Attributes p2attributes,
                               final Content content) throws IOException {
    StorageFacet storageFacet = facet(StorageFacet.class);
    try (TempBlob tempBlob = storageFacet.createTempBlob(content.openInputStream(), HASH_ALGORITHMS)) {
      return doPutBinary(p2attributes, tempBlob, content);
    }
  }

  private Content doPutBinary(final P2Attributes p2Attributes,
                               final TempBlob componentContent,
                               final Payload payload) throws IOException {
    return doCreateOrSaveComponent(p2Attributes, componentContent, payload, COMPONENT_BINARY);
  }

  private Content putComponent(final P2Attributes p2Attributes,
                               final Content content,
                               final AssetKind assetKind) throws IOException {
    StorageFacet storageFacet = facet(StorageFacet.class);
    try (TempBlob tempBlob = storageFacet.createTempBlob(content.openInputStream(), HASH_ALGORITHMS)) {
      return doPutComponent(p2Attributes, tempBlob, content, assetKind);
    }
  }


  private Content doPutComponent(P2Attributes p2Attributes,
                                   final TempBlob componentContent,
                                   final Payload payload,
                                   final AssetKind assetKind) throws IOException {
    p2Attributes = mergeAttributesFromTempBlob(componentContent, p2Attributes);

    return doCreateOrSaveComponent(p2Attributes, componentContent, payload, assetKind);
  }

  @TransactionalStoreBlob
  protected Content doCreateOrSaveComponent(final P2Attributes p2Attributes,
                                          final TempBlob componentContent,
                                          final Payload payload,
                                          final AssetKind assetKind) throws IOException
  {
    StorageTx tx = UnitOfWork.currentTx();
    Bucket bucket = tx.findBucket(getRepository());

    Component component = p2DataAccess.findComponent(tx,
        getRepository(),
        p2Attributes.getComponentName(),
        p2Attributes.getComponentVersion());

    if (component == null) {
      component = tx.createComponent(bucket, getRepository().getFormat())
          .name(p2Attributes.getComponentName())
          .version(p2Attributes.getComponentVersion());
    }
    tx.saveComponent(component);

    Asset asset = p2DataAccess.findAsset(tx, bucket, p2Attributes.getPath());
    if (asset == null) {
      asset = tx.createAsset(bucket, component);
      asset.name(p2Attributes.getPath());
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

  @VisibleForTesting
  protected P2Attributes mergeAttributesFromTempBlob(final TempBlob tempBlob, final P2Attributes p2Attributes) {
    try (JarInputStream jis = getJar(tempBlob, p2Attributes.getExtension())) {
      return jarParser
          .getAttributesFromJarFile(jis)
          .map(jarP2Attributes -> P2Attributes.builder().merge(p2Attributes, jarP2Attributes).build())
          .orElse(p2Attributes);
    }
    catch (Exception ex) {
      log.warn("Unable to get version from JarInputStream due to following exception: {}", ex.getMessage());
    }

    return p2Attributes;
  }

  @VisibleForTesting
  protected JarInputStream getJar(final TempBlob tempBlob, final String extension) throws IOException {
    if (extension.equals("jar")) {
      return new JarInputStream(tempBlob.get());
    }
    else {
      return new JarInputStream(tempBlobConverter.getJarFromPackGz(tempBlob));
    }
  }
}
