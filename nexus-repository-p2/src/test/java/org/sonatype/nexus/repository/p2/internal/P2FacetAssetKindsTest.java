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

import java.util.Arrays;
import java.util.Collection;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Test for {@link P2FacetImpl}
 */
@RunWith(Parameterized.class)
public class P2FacetAssetKindsTest
    extends TestSupport
{
  @Parameters(name = "{index} - {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{{"p2.index", AssetKind.P2_INDEX},
        {"features/org.eclipse.epp.package.java.feature_4.14.0.20191212-1200.jar", AssetKind.BUNDLE},
        {"plugins/org.eclipse.epp.package.java_4.14.0.20191212-1200.jar.pack.gz", AssetKind.BUNDLE},
        {"binary/epp.package.java.executable.cocoa.macosx.x86_64_4.14.0.20191212-1200", AssetKind.BUNDLE},
        {"compositeArtifacts.jar", AssetKind.COMPOSITE_ARTIFACTS},
        {"compositeArtifacts.xml", AssetKind.COMPOSITE_ARTIFACTS},
        {"compositeContent.jar", AssetKind.COMPOSITE_CONTENT},
        {"compositeContent.xml", AssetKind.COMPOSITE_CONTENT},
        {"content.jar", AssetKind.CONTENT_METADATA},
        {"content.xml", AssetKind.CONTENT_METADATA},
        {"content.xml.xz", AssetKind.CONTENT_METADATA},
        {"artifacts.jar", AssetKind.ARTIFACTS_METADATA},
        {"artifacts.xml", AssetKind.ARTIFACTS_METADATA},
        {"artifacts.xml.xz", AssetKind.ARTIFACTS_METADATA}});
  }

  private P2FacetImpl underTest;

  @Parameter
  public String path;

  @Parameter(1)
  public AssetKind expectedKind;

  @Before
  public void setUp() throws Exception {
    underTest = new P2FacetImpl();
  }

  @Test
  public void testGetAssetKind() {
    assertThat(underTest.getAssetKind(path), is(expectedKind));
    assertThat(underTest.getAssetKind("496e75a238e44f9c24eb4c266b7c1c92dedd98719f5bfb0b6be386b8790b8e67/" + path),
        is(expectedKind));
  }
}
