/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.p2.upgrade.tasks;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.scheduling.TaskDescriptorSupport;

/**
 * Task descriptor for {@link P2DeleteLegacyProxyAssetsTask}.
 *
 * @since 1.next
 */
@Named
@Singleton
public class P2DeleteLegacyProxyAssetsTaskDescriptor
    extends TaskDescriptorSupport
{
  public static final String TASK_NAME = "P2 - Delete legacy proxy assets";

  public static final String TYPE_ID = "repository.p2.delete-legacy-proxy-assets";

  public P2DeleteLegacyProxyAssetsTaskDescriptor() {
    super(TYPE_ID, P2DeleteLegacyProxyAssetsTask.class, TASK_NAME, VISIBLE, EXPOSED);
  }
}
