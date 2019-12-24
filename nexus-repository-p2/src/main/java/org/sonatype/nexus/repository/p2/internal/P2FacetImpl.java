package org.sonatype.nexus.repository.p2.internal;

import java.util.Map;

import javax.inject.Named;

import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.p2.P2Facet;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Bucket;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.view.ConfigurableViewFacet;
import org.sonatype.nexus.repository.view.Matcher;
import org.sonatype.nexus.repository.view.matchers.token.PatternParser;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

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
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.buildSimpleMatcher;
import static org.sonatype.nexus.repository.p2.internal.util.P2DataAccess.findAsset;
import static org.sonatype.nexus.repository.p2.internal.util.P2DataAccess.findComponent;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.DIVIDER;
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
    else if (isPathMatch( path, COMPOSITE_ARTIFACTS, JAR_EXTENSION)) {
      assetKind = COMPOSITE_ARTIFACTS_JAR;
    }
    else if (isPathMatch( path,COMPOSITE_ARTIFACTS, XML_EXTENSION)) {
      assetKind = COMPOSITE_ARTIFACTS_XML;
    }
    else if (isPathMatch( path, COMPOSITE_CONTENT, JAR_EXTENSION)) {
      assetKind = COMPOSITE_CONTENT_JAR;
    }
    else if (isPathMatch( path, COMPOSITE_CONTENT, XML_EXTENSION)) {
      assetKind = COMPOSITE_CONTENT_XML;
    }
    else if (isPathMatch( path, CONTENT_NAME, JAR_EXTENSION)) {
      assetKind = CONTENT_JAR;
    }
    else if (isPathMatch( path, CONTENT_NAME, XML_EXTENSION)) {
      assetKind = CONTENT_XML;
    }
    else if (isPathMatch( path, CONTENT_NAME, XML_XZ_EXTENSION)) {
      assetKind = CONTENT_XML_XZ;
    }
    else if (isPathMatch( path, ARTIFACTS_NAME, JAR_EXTENSION)) {
      assetKind = ARTIFACT_JAR;
    }
    else if (isPathMatch( path, ARTIFACTS_NAME, XML_EXTENSION)) {
      assetKind = ARTIFACT_XML;
    }
    else if (isPathMatch( path, ARTIFACTS_NAME, XML_XZ_EXTENSION)) {
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
