package org.sonatype.nexus.repository.p2.internal.util;

import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.p2.internal.exception.InvalidMetadataException;
import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.storage.TempBlob;

import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.normalizeComponentName;

@Named
@Singleton
public class AttributesParserManifest
    extends AbstractAttributesParser
{
  private static final String MANIFEST_FILE_PREFIX = "META-INF/";

  private static final JarExtractor<Manifest> MANIFEST_JAR_EXTRACTOR = (jis, jarEntry) -> {
    Manifest manifest = jis.getManifest();
    if (manifest != null) {
      return manifest;
    }
    return new Manifest(jis);
  };

  @Inject
  public AttributesParserManifest(TempBlobConverter tempBlobConverter){
    super(tempBlobConverter);
  }

  @Override
  public Optional<P2Attributes> getAttributesFromBlob(final TempBlob tempBlob, final String extension)
      throws InvalidMetadataException
  {
    P2Attributes p2Attributes;
    Optional<Manifest> manifestJarEntity = MANIFEST_JAR_EXTRACTOR.getSpecificEntity(tempBlob, extension, MANIFEST_FILE_PREFIX);
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
