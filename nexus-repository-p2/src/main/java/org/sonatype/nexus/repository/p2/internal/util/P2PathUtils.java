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
package org.sonatype.nexus.repository.p2.internal.util;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.repository.p2.internal.AssetKind;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.TempBlob;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher.State;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.join;
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
import static org.sonatype.nexus.repository.p2.internal.P2FacetImpl.HASH_ALGORITHMS;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.ARTIFACTS_NAME;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.COMPOSITE_ARTIFACTS;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.COMPOSITE_CONTENT;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.CONTENT_NAME;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.JAR_EXTENSION;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.XML_EXTENSION;
import static org.sonatype.nexus.repository.p2.internal.proxy.P2ProxyRecipe.XML_XZ_EXTENSION;

/**
 * Utility methods for working with P2 routes and paths.
 */
public class P2PathUtils
{
  public final static String DIVIDER = "/";

  public static final String PLUGIN_NAME = "pluginName";

  private final static String NAME_VERSION_SPLITTER = "_";

  public static final String HTTP_NXRM_PREFIX = "http/";

  public static final String HTTPS_NXRM_PREFIX = "https/";

  private static final String HTTP_URL_PREFIX = "http://";

  private static final String HTTPS_URL_PREFIX = "https://";

  private static final String FEATURE = ".feature";

  private static final String PLUGIN = ".plugin";

  private P2PathUtils() {
    throw new UnsupportedOperationException();
  }

  /**
   * * Returns the path from a {@link TokenMatcher.State}.
   */
  public static String path(final TokenMatcher.State state) {
    return match(state, "path");
  }

  public static String maybePath(final TokenMatcher.State state) {
    checkNotNull(state);
    String path = state.getTokens().get("path");
    if (isNullOrEmpty(path)) {
      return String.format("%s.%s", match(state, "name"), match(state, "extension"));
    }
    return String.format("%s/%s.%s", path, match(state, "name"), match(state, "extension"));
  }

  /**
   * Utility method encapsulating getting a particular token by name from a matcher, including preconditions.
   */
  private static String match(final TokenMatcher.State state, final String name) {
    checkNotNull(state);
    String result = state.getTokens().get(name);
    checkNotNull(result);
    return result;
  }

  /**
   * Builds a path to an archive for a particular path and name.
   */
  public static String path(final String path, final String filename) {
    if (isNullOrEmpty(path)) {
      return filename;
    }
    else {
      return path + "/" + filename;
    }
  }

  /**
   * Builds a path to an archive for a particular path, name and extension.
   */
  public static String path(final String path, final String filename, final String extension) {
    String file = join(".", filename, extension);
    return isNullOrEmpty(path) ? file : join("/", path, file);
  }

  /**
   * Builds a path to a binary for a particular path, name and version.
   */
  public static String binaryPath(final String path, final String name, final String version) {
    String file = join("_", name, version);
    return isNullOrEmpty(path) ? file : join("/", path, file);
  }

  /**
   * Returns the name from a {@link TokenMatcher.State}.
   */
  public static String name(final TokenMatcher.State state) {
    return match(state, "name");
  }

  /**
   * Returns the name and extension from a {@link TokenMatcher.State}.
   */
  public static String filename(final TokenMatcher.State state) {
    return name(state) + '.' + extension(state);
  }

  public static String version(final TokenMatcher.State state) { return match(state, "version"); }

  /**
   * Returns the Component Name from the name as a default from a {@link TokenMatcher.State}.
   *
   * @see #name(State)
   */
  public static String componentName(final TokenMatcher.State state) {
    return normalizeComponentName(name(state).split(NAME_VERSION_SPLITTER)[0]);
  }

  /**
   * Returns the Component Name from the name without suffixes like ".feature" or ".plugin"
   */
  public static String normalizeComponentName(final String componentName) {
    String normalizedComponentName = componentName;
    normalizedComponentName = StringUtils.removeEnd(normalizedComponentName, FEATURE);
    normalizedComponentName = StringUtils.removeEnd(normalizedComponentName, PLUGIN);
    return normalizedComponentName;
  }

  /**
   * Returns the Version from the name as a default from a {@link TokenMatcher.State}.
   *
   * @see #name(State)
   */
  public static String componentVersion(final TokenMatcher.State state) {
    return name(state).split(NAME_VERSION_SPLITTER)[1];
  }

  /**
   * Returns the extension from a {@link TokenMatcher.State}.
   */
  public static String extension(final TokenMatcher.State state) {
    return match(state, "extension");
  }

  /**
   * Returns the {@link TokenMatcher.State} for the content.
   */
  public static TokenMatcher.State matcherState(final Context context) {
    return context.getAttributes().require(TokenMatcher.State.class);
  }

  public static P2Attributes toP2Attributes(final TokenMatcher.State state) {
    return P2Attributes.builder()
        .componentName(componentName(state))
        .componentVersion(componentVersion(state))
        .extension(extension(state))
        .fileName(filename(state))
        .path(path(path(state), filename(state)))
        .build();
  }

  public static P2Attributes toP2AttributesBinary(final TokenMatcher.State state) {
    return P2Attributes.builder()
        .pluginName(name(state))
        .componentName(name(state))
        .componentVersion(version(state))
        .path(binaryPath(path(state), name(state), version(state)))
        .build();
  }

  public static String escapeUriToPath(final String uri) {
    return uri.replace("://", "/");
  }

  public static String unescapePathToUri(final String path) {
    String resultPath = path;
    if (path.startsWith(HTTP_NXRM_PREFIX)) {
      resultPath = path.replaceFirst(HTTP_NXRM_PREFIX, HTTP_URL_PREFIX);
    }
    else if (path.startsWith(HTTPS_NXRM_PREFIX)) {
      resultPath = path.replaceFirst(HTTPS_NXRM_PREFIX, HTTPS_URL_PREFIX);
    }
    return resultPath;
  }

  public static P2Attributes getBinaryAttributesFromBlobName(final String blobName) {

    P2Attributes.Builder attributes = P2Attributes.builder();
    //https/download.eclipse.org/technology/epp/packages/2019-12/binary/epp.package.java.executable.cocoa.macosx.x86_64_4.14.0.20191212-1200
    String version = getBinaryVersionFromBlobName(blobName);
    String name = getBinaryNameFromBlobName(blobName, version);
    AssetKind assetKind = getAssetKind(blobName);

    attributes.componentName(name);
    attributes.componentVersion(version);
    attributes.pluginName(name);
    attributes.assetKind(assetKind);

    return attributes.build();
  }

  public static P2Attributes getPackageAttributesFromBlob(final StorageFacet storageFacet,
                                                          final P2TempBlobUtils p2TempBlobUtils,
                                                          final Blob blob,
                                                          final String blobName)
      throws IOException
  {
    P2Attributes.Builder attributes = P2Attributes.builder();

    try (TempBlob tempBlob = storageFacet.createTempBlob(blob.getInputStream(), HASH_ALGORITHMS)) {
      String extension = getPackageExtensionFromBlobName(blobName);
      P2Attributes p2Attributes = P2Attributes.builder().extension(extension).build();
      P2Attributes mergedAttributes = p2TempBlobUtils.mergeAttributesFromTempBlob(tempBlob, p2Attributes);
      AssetKind assetKind = getAssetKind(blobName);

      attributes.componentName(mergedAttributes.getComponentName());
      attributes.componentVersion(mergedAttributes.getComponentVersion());
      attributes.pluginName(mergedAttributes.getPluginName());
      attributes.assetKind(assetKind);
    }

    return attributes.build();
  }

  private static String getBinaryNameFromBlobName(final String blobName, final String version) {
    String[] namePaths = blobName.split(DIVIDER);
    return namePaths[namePaths.length - 1].replace("_" + version, "");
  }

  private static String getBinaryVersionFromBlobName(final String blobName) {
    String[] versionPaths = blobName.split("_");
    return versionPaths[versionPaths.length - 1];
  }

  private static String getPackageExtensionFromBlobName(final String blobName) {
    String[] paths = blobName.split("\\.");
    return paths[paths.length - 1];
  }

  public static AssetKind getAssetKind(final String path) {
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

  private static boolean isPathMatch(final String path, final String patternName, final String patternExtension) {
    return path.matches(".*" + patternName + "\\." + patternExtension + "$");
  }
}
