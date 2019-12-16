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

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher.State;

import org.apache.commons.lang.StringUtils;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.join;

/**
 * Utility methods for working with P2 routes and paths.
 *
 * @since 0.next
 *
 */
public class P2PathUtils
{
  public final static String DIVIDER = "/";

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
    if(isNullOrEmpty(path)) {
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
}
