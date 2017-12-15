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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.join;

/**
 * Utility methods for working with P2 routes and paths.
 */
@Named
@Singleton
public class P2PathUtils
{
  private final static String NAME_VERSION_SPLITTER = "_";

  /**
   * * Returns the path from a {@link TokenMatcher.State}.
   */
  public String path(final TokenMatcher.State state) {
    return match(state, "path");
  }

  /**
   * Utility method encapsulating getting a particular token by name from a matcher, including preconditions.
   */
  private String match(final TokenMatcher.State state, final String name) {
    checkNotNull(state);
    String result = state.getTokens().get(name);
    checkNotNull(result);
    return result;
  }

  /**
   * Builds a path to an archive for a particular path and name.
   */
  public String path(final String path, final String filename) {
    if(isNullOrEmpty(path)) {
      return filename;
    }
    else {
      return path + "/" + filename;
    }
  }

  /**
   * Builds a path to an archive for a particular path and name.
   */
  public String path(final String path, final String filename, final String extension) {
    String file = join(".", filename, extension);
    return isNullOrEmpty(path) ? file : join("/", path, file);
  }

  /**
   * Returns the name from a {@link TokenMatcher.State}.
   */
  public String name(final TokenMatcher.State state) {
    return match(state, "name");
  }

  /**
   * Returns the name and extension from a {@link TokenMatcher.State}.
   */
  public String filename(final TokenMatcher.State state) {
    return name(state) + '.' + extension(state);
  }

  /**
   * Returns the Component Name from the name as a default from a {@link TokenMatcher.State}.
   *
   * @see #name(State)
   */
  public String componentName(final TokenMatcher.State state) {
    return name(state).split(NAME_VERSION_SPLITTER)[0];
  }

  /**
   * Returns the Version from the name as a default from a {@link TokenMatcher.State}.
   *
   * @see #name(State)
   */
  public String componentVersion(final TokenMatcher.State state) {
    return name(state).split(NAME_VERSION_SPLITTER)[1];
  }

  /**
   * Returns the extension from a {@link TokenMatcher.State}.
   */
  public String extension(final TokenMatcher.State state) {
    return match(state, "extension");
  }

  /**
   * Returns the {@link TokenMatcher.State} for the content.
   */
  public TokenMatcher.State matcherState(final Context context) {
    return context.getAttributes().require(TokenMatcher.State.class);
  }

  public P2Attributes toP2Attributes(final TokenMatcher.State state) {
    return P2Attributes.builder()
        .componentName(componentName(state))
        .componentVersion(componentVersion(state))
        .extension(extension(state))
        .fileName(filename(state))
        .path(path(path(state), filename(state)))
        .build();
  }
}
