package org.sonatype.nexus.repository.p2.internal.util;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.PropertyResourceBundle;

import javax.annotation.Nullable;
import javax.xml.xpath.XPathExpressionException;

import org.sonatype.nexus.repository.p2.internal.exception.InvalidMetadataException;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.storage.TempBlob;

import org.apache.commons.io.IOUtils;

public abstract class AbstractAttributesParser
{
  protected Optional<PropertyResourceBundle> getBundleProperties(
      final TempBlob tempBlob,
      final String extension,
      @Nullable final String startNameForSearch)
  {
    JarExtractor<PropertyResourceBundle> jarExtractor =
        (jis, jarEntry) -> new PropertyResourceBundle(new ByteArrayInputStream(IOUtils.toByteArray(jis)));

    return jarExtractor.getSpecificEntity(tempBlob, extension, startNameForSearch);
  }

  protected String extractValueFromProperty(String value, Optional<PropertyResourceBundle> propertyResourceBundleOpt) {
    if (!propertyResourceBundleOpt.isPresent() || value == null ||
        !propertyResourceBundleOpt.get().containsKey(value.substring(1))) {
      return value;
    }

    // get property key for bundle name without '%' character in start
    return propertyResourceBundleOpt.get().getString(value.substring(1));
  }

  public abstract Optional<P2Attributes> getAttributesFromBlob(final TempBlob tempBlob, final String extension)
      throws InvalidMetadataException, XPathExpressionException;
}
