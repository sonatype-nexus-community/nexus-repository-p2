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

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.sonatype.nexus.repository.cache.CacheControllerHolder.CONTENT;
import static org.sonatype.nexus.repository.cache.CacheControllerHolder.METADATA;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPONENT_FEATURES_JAR;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPONENT_FEATURES_PACK_GZ;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPONENT_PLUGINS_JAR;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.COMPONENT_PLUGINS_PACK_GZ;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.ARTIFACT_JAR;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.ARTIFACT_XML;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.ARTIFACT_XML_XZ;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.CONTENT_JAR;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.CONTENT_XML;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.CONTENT_XML_XZ;
import static org.sonatype.nexus.repository.p2.internal.AssetKind.P2_INDEX;


public class AssetKindTest
    extends TestSupport
{
  @Test
  public void cacheTypes() throws Exception {
    assertThat(P2_INDEX.getCacheType(), is(equalTo(METADATA)));
    assertThat(CONTENT_JAR.getCacheType(), is(equalTo(METADATA)));
    assertThat(ARTIFACT_JAR.getCacheType(), is(equalTo(METADATA)));
    assertThat(CONTENT_XML.getCacheType(), is(equalTo(METADATA)));
    assertThat(ARTIFACT_XML.getCacheType(), is(equalTo(METADATA)));
    assertThat(ARTIFACT_XML_XZ.getCacheType(), is(equalTo(METADATA)));
    assertThat(CONTENT_XML_XZ.getCacheType(), is(equalTo(METADATA)));
    assertThat(COMPONENT_PLUGINS_JAR.getCacheType(), is(equalTo(CONTENT)));
    assertThat(COMPONENT_FEATURES_JAR.getCacheType(), is(equalTo(CONTENT)));
    assertThat(COMPONENT_PLUGINS_PACK_GZ.getCacheType(), is(equalTo(CONTENT)));
    assertThat(COMPONENT_FEATURES_PACK_GZ.getCacheType(), is(equalTo(CONTENT)));
  }
}
