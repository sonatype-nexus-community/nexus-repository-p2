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

import org.sonatype.nexus.repository.p2.internal.exception.AttributeParsingException;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.storage.TempBlob;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.normalizeComponentName;

/**
 *
 * @since 0.next
 */
@Named
@Singleton
public class AttributesParserManifest
    extends AbstractAttributesParser
{
  private static final String MANIFEST_FILE_PREFIX = "META-INF/";

  private JarExtractor<Manifest> manifestJarExtractor;

  @Inject
  public AttributesParserManifest(final TempBlobConverter tempBlobConverter) {
    super(tempBlobConverter);
    manifestJarExtractor = new JarExtractor<Manifest>(tempBlobConverter) {
      @Override
      protected Manifest createSpecificEntity(JarInputStream jis, JarEntry jarEntry) throws AttributeParsingException {
        Manifest manifest = jis.getManifest();
        if (manifest != null) {
          return manifest;
        }
        try {
          return new Manifest(jis);
        } catch (IOException e) {
          throw new AttributeParsingException(e);
        }
      }
    };
  }

  @Override
  public Optional<P2Attributes> getAttributesFromBlob(final TempBlob tempBlob, final String extension)
      throws AttributeParsingException
  {
    P2Attributes p2Attributes;
    Optional<Manifest> manifestJarEntity = manifestJarExtractor.getSpecificEntity(tempBlob, extension, MANIFEST_FILE_PREFIX);
    if (!manifestJarEntity.isPresent()) {
      return Optional.empty();
    }

    Attributes mainManifestAttributes = manifestJarEntity.get().getMainAttributes();
    String bundleLocalizationValue = mainManifestAttributes.getValue("Bundle-Localization");
    Optional<PropertyResourceBundle> propertiesOpt = getBundleProperties(tempBlob, extension, bundleLocalizationValue);

    p2Attributes = P2Attributes.builder()
        .componentName(normalizeName(extractValueFromProperty(mainManifestAttributes.getValue("Bundle-SymbolicName"), propertiesOpt)))
        .pluginName(extractValueFromProperty(mainManifestAttributes.getValue("Bundle-Name"), propertiesOpt))
        .componentVersion(extractValueFromProperty(mainManifestAttributes.getValue("Bundle-Version"), propertiesOpt))
        .build();

    return Optional.ofNullable(p2Attributes);
  }

  private String normalizeName(final String name) {
    String resultName = name;
    //handle org.tigris.subversion.clientadapter.svnkit;singleton:=true
    if (name != null) {
      resultName = name.split(";")[0];
    }
    return normalizeComponentName(resultName);
  }
}
