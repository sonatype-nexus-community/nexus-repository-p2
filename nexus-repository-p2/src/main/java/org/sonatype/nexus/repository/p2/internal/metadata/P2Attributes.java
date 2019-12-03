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
package org.sonatype.nexus.repository.p2.internal.metadata;

import javax.annotation.Nullable;

import static java.util.Optional.ofNullable;

/**
 * Shared attributes of P2 metadata files.
 */
public class P2Attributes
{
  private String groupName;

  private String componentName;

  private String componentVersion;

  private String path;

  private String fileName;

  private String extension;

  private P2Attributes(final Builder builder) {
    this.groupName = builder.groupName;
    this.componentName = builder.componentName;
    this.componentVersion = builder.componentVersion;
    this.path = builder.path;
    this.fileName = builder.fileName;
    this.extension = builder.extension;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder
  {
    private String groupName;

    private String componentName;

    private String componentVersion;

    private String path;

    private String fileName;

    private String extension;

    private Builder() {
    }

    public Builder groupName(final String groupName) {
      this.groupName = groupName;
      return this;
    }

    public Builder componentName(final String componentName) {
      this.componentName = componentName;
      return this;
    }

    public Builder componentVersion(final String componentVersion) {
      this.componentVersion = componentVersion;
      return this;
    }

    public Builder path(final String path) {
      this.path = path;
      return this;
    }

    public Builder fileName(final String fileName) {
      this.fileName = fileName;
      return this;
    }

    public Builder extension(final String extension) {
      this.extension = extension;
      return this;
    }

    public P2Attributes build() {
      return new P2Attributes(this);
    }

    public Builder merge(final P2Attributes one, P2Attributes two) {
      componentVersion(ofNullable(two.getComponentVersion()).orElse(one.getComponentVersion()));
      componentName(ofNullable(two.getComponentName()).orElse(one.getComponentName()));
      groupName(ofNullable(two.getGroupName()).orElse(one.getGroupName()));
      path(ofNullable(two.getPath()).orElse(one.getPath()));
      fileName(ofNullable(two.getFileName()).orElse(one.getFileName()));
      extension(ofNullable(two.getExtension()).orElse(one.getExtension()));
      return this;
    }
  }

  @Nullable
  public String getGroupName() {
    return groupName;
  }

  @Nullable
  public String getComponentName() {
    return componentName;
  }

  @Nullable
  public String getComponentVersion() {
    return componentVersion;
  }

  @Nullable
  public String getPath() {
    return path;
  }

  @Nullable
  public String getFileName() {
    return fileName;
  }

  @Nullable
  public String getExtension() {
    return extension;
  }
}
