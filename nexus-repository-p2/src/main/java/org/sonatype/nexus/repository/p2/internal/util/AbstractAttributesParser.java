package org.sonatype.nexus.repository.p2.internal.util;

import java.io.IOException;
import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import javax.annotation.Nullable;
import javax.xml.xpath.XPathExpressionException;

import org.sonatype.nexus.repository.p2.internal.exception.AttributeParsingException;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.storage.TempBlob;

public abstract class AbstractAttributesParser
{
  private static final String PROPERTY_RESOURCE_BUNDLE_EXTENSION = ".properties";

  protected final TempBlobConverter tempBlobConverter;

  public AbstractAttributesParser(final TempBlobConverter tempBlobConverter) {
    this.tempBlobConverter = tempBlobConverter;
  }

  protected Optional<PropertyResourceBundle> getBundleProperties(
          final TempBlob tempBlob,
          final String extension,
          @Nullable final String startNameForSearch) throws AttributeParsingException {
    JarExtractor<PropertyResourceBundle> jarExtractor = new JarExtractor<PropertyResourceBundle>(tempBlobConverter) {
      @Override
      protected PropertyResourceBundle createSpecificEntity(JarInputStream jis, JarEntry jarEntry) throws AttributeParsingException {
        try {
          return new PropertyResourceBundle(jis);
        } catch (IOException e) {
          throw new AttributeParsingException(e);
        }
      }
    };

    return jarExtractor.getSpecificEntity(tempBlob, extension, startNameForSearch + PROPERTY_RESOURCE_BUNDLE_EXTENSION);
  }

  protected String extractValueFromProperty(final String value, final Optional<PropertyResourceBundle> propertyResourceBundleOpt) {
    if (!propertyResourceBundleOpt.isPresent() || value == null ||
        !propertyResourceBundleOpt.get().containsKey(value.substring(1))) {
      return value;
    }

    // get property key for bundle name without '%' character in start
    return propertyResourceBundleOpt.get().getString(value.substring(1));
  }

  public abstract Optional<P2Attributes> getAttributesFromBlob(final TempBlob tempBlob, final String extension)
      throws AttributeParsingException, XPathExpressionException;
}
