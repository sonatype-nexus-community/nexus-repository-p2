package org.sonatype.nexus.repository.p2.internal.util;

import java.util.Optional;
import java.util.PropertyResourceBundle;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.sonatype.nexus.repository.p2.internal.metadata.P2Attributes;
import org.sonatype.nexus.repository.storage.TempBlob;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static javax.xml.xpath.XPathConstants.NODE;
import static org.sonatype.nexus.repository.p2.internal.util.P2PathUtils.normalizeComponentName;

@Named
@Singleton
public class AttributesParserFeatureXml
    extends AbstractAttributesParser
{
  private static final String XML_VERSION_PATH = "feature/@version";

  private static final String XML_PLUGIN_NAME_PATH = "feature/@plugin";

  private static final String XML_PLUGIN_ID_PATH = "feature/@id";

  private static final String XML_NAME_PATH = "feature/@label";

  private static final String XML_FILE_NAME = "feature.xml";

  private static final String FEATURE_PROPERTIES = "feature.properties";

  private static final JarExtractor<Document> DOCUMENT_JAR_EXTRACTOR = (jis, jarEntry) -> {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setValidating(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    return factory.newDocumentBuilder().parse(jis);
  };

  @Inject
  public AttributesParserFeatureXml(final TempBlobConverter tempBlobConverter) {
    super(tempBlobConverter);
  }

  @Override
  public Optional<P2Attributes> getAttributesFromBlob(final TempBlob tempBlob, final String extension)
  {
    P2Attributes p2Attributes;

    Optional<Document> featureXmlOpt =
        DOCUMENT_JAR_EXTRACTOR.getSpecificEntity(tempBlob, extension, XML_FILE_NAME);

    if (!featureXmlOpt.isPresent()) {
      return Optional.empty();
    }

    Optional<PropertyResourceBundle> propertiesOpt =
        getBundleProperties(tempBlob, extension, FEATURE_PROPERTIES);

    Document document = featureXmlOpt.get();

    String pluginId = extractValueFromDocument(XML_PLUGIN_NAME_PATH, document);
    if (pluginId == null) {
      pluginId = extractValueFromDocument(XML_PLUGIN_ID_PATH, document);
    }

    String componentName = normalizeComponentName(extractValueFromProperty(pluginId, propertiesOpt));
    p2Attributes = P2Attributes.builder()
        .componentName(componentName)
        .pluginName(extractValueFromProperty(extractValueFromDocument(XML_NAME_PATH, document), propertiesOpt))
        .componentVersion(extractValueFromDocument(XML_VERSION_PATH, document))
        .build();

    return Optional.ofNullable(p2Attributes);
  }

  @Nullable
  private String extractValueFromDocument(
      final String path,
      final Document from) throws XPathExpressionException
  {
    XPath xPath = XPathFactory.newInstance().newXPath();
    Node node = (Node) xPath.evaluate(path, from, NODE);
    if (node != null) {
      return node.getNodeValue();
    }

    return null;
  }
}
