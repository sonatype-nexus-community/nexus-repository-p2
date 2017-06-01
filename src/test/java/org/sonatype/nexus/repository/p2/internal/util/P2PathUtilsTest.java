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

import java.util.Collections;
import java.util.Map;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.common.collect.AttributesMap;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

public class P2PathUtilsTest
    extends TestSupport
{
  P2PathUtils p2PathUtils;

  @Mock
  Context context;

  @Mock
  TokenMatcher.State state;

  @Mock
  AttributesMap attributesMap;

  private final String fakePath = "fakepath";
  private final String fakeFileName = "eclipsepackage1-2-3.jar";

  @Before
  public void setUp() throws Exception {
    p2PathUtils = new P2PathUtils();
  }

  @Test
  public void pathWithState() throws Exception {
    final Map<String, String> someMap = Collections.singletonMap("path", fakePath);
    when(state.getTokens())
        .thenReturn(someMap);
    String path = p2PathUtils.path(state);
    assertThat(path, is(equalTo(fakePath)));
  }

  @Test
  public void pathWithPathAndFileName() throws Exception {
    String path = p2PathUtils.path(fakePath, fakeFileName);
    String expectedResult = fakePath + "/" + fakeFileName;
    assertThat(path, is(equalTo(expectedResult)));
  }

  @Test
  public void filename() throws Exception {
    final Map<String, String> someMap = Collections.singletonMap("filename", fakeFileName);
    when(state.getTokens())
        .thenReturn(someMap);
    String filename = p2PathUtils.filename(state);
    assertThat(filename, is(equalTo(fakeFileName)));
  }

  @Test
  public void matcherState() throws Exception {
    when(context.getAttributes())
        .thenReturn(attributesMap);
    when(attributesMap.require(TokenMatcher.State.class))
        .thenReturn(state);
    TokenMatcher.State testState = p2PathUtils.matcherState(context);
    assertThat(testState, instanceOf(TokenMatcher.State.class));
  }
}