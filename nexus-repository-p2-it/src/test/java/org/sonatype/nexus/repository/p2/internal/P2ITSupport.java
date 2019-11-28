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

import java.net.URL;

import javax.annotation.Nonnull;

import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.repository.p2.internal.fixtures.RepositoryRuleP2;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.testsuite.testsupport.RepositoryITSupport;
import org.junit.Rule;

import static com.google.common.base.Preconditions.checkNotNull;

public class P2ITSupport
    extends RepositoryITSupport
{
  public static final String FORMAT_NAME = "p2";

  public static final String MIME_TYPE = "application/java-archive";

  public static final String COMPONENT_NAME = "org.eclipse.cvs.source";

  public static final String ARTIFACT_NAME = "artifacts";

  public static final String ARTIFACT_WITHOUT_MIRROR_NAME = "artifacts-mirror-removed";

  public static final String VERSION_NUMBER = "1.4.404.v20180330-0640";

  public static final String EXTENSION_JAR = ".jar";

  public static final String EXTENSION_XML = ".xml";

  public static final String EXTENSION_XML_XZ = ".xml.xz";

  public static final String PACKAGE_NAME = COMPONENT_NAME + "_" + VERSION_NUMBER + EXTENSION_JAR;

  public static final String PACKAGE_BASE_PATH = "R-4.7.3a-201803300640/features/";

  public static final String HELP_COMPONENT_NAME = "org.eclipse.help.source";

  public static final String HELP_VERSION = "2.2.104.v20180330-0640";

  public static final String HELP_PACKAGE_NAME = HELP_COMPONENT_NAME + "_" + HELP_VERSION + EXTENSION_JAR;

  public static final String VALID_HELP_PACKAGE_URL = PACKAGE_BASE_PATH + HELP_PACKAGE_NAME;

  public static final String ARTIFACTS_BASE_PATH = "R-4.7-201706120950/";

  public static final String ARTIFACT_JAR = ARTIFACT_NAME + EXTENSION_JAR;

  public static final String ARTIFACT_XML = ARTIFACT_NAME + EXTENSION_XML;

  public static final String ARTIFACT_XML_TEST_PATH = ARTIFACTS_BASE_PATH + ARTIFACT_XML;

  public static final String ARTIFACT_XML_XZ = ARTIFACT_NAME + EXTENSION_XML_XZ;

  public static final String ARTIFACT_XML_XZ_TEST_PATH = ARTIFACTS_BASE_PATH + ARTIFACT_XML_XZ;

  public static final String ARTIFACT_WITHOUT_MIRROR_XML = ARTIFACT_WITHOUT_MIRROR_NAME + EXTENSION_XML;

  public static final String INVALID_PACKAGE_NAME = COMPONENT_NAME + "-0.24.zip";

  public static final String BAD_PATH = "/this/path/is/not/valid";

  public static final String VALID_PACKAGE_URL = PACKAGE_BASE_PATH + PACKAGE_NAME;

  public static final String P2_INDEX = "p2.index";

  public static final String COMPOSITE_ARTIFACTS_JAR = "compositeArtifacts.jar";

  public static final String INVALID_PACKAGE_URL = PACKAGE_BASE_PATH + INVALID_PACKAGE_NAME;

  @Rule
  public RepositoryRuleP2 repos = new RepositoryRuleP2(() -> repositoryManager);

  @Override
  protected RepositoryRuleP2 createRepositoryRule() {
    return new RepositoryRuleP2(() -> repositoryManager);
  }

  public P2ITSupport() {
    testData.addDirectory(NexusPaxExamSupport.resolveBaseFile("target/test-classes/p2"));
  }

  @Nonnull
  protected P2Client p2Client(final Repository repository) throws Exception {
    checkNotNull(repository);
    return p2Client(repositoryBaseUrl(repository));
  }

  protected P2Client p2Client(final URL repositoryUrl) throws Exception {
    return new P2Client(
        clientBuilder(repositoryUrl).build(),
        clientContext(),
        repositoryUrl.toURI()
    );
  }
}
