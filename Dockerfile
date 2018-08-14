# nexus version the plugin is build against
ARG NEXUS_VERSION=3.8.0

FROM sonatype/nexus3:${NEXUS_VERSION}

# dockerfile-maven-plugin ARG
ARG P2_VERSION
ARG TARGET
ARG JAR_FILE

# Addin the bundle.jar nexus plugins folder
ARG TARGET_DIR=/opt/sonatype/nexus/system/org/sonatype/nexus/plugins/nexus-repository-p2/${P2_VERSION}/
ADD ${TARGET}/${JAR_FILE} ${TARGET_DIR}/${JAR_FILE}

RUN echo ${P2_VERSION}

USER root
RUN export NEXUS_PATH=/opt/sonatype/nexus/system/com/sonatype/nexus/assemblies/nexus-oss-feature/ \ 
    && export NEXUS_FEATURES_FILE=`find $NEXUS_PATH | grep features.xml` \
    && sed -i 's@nexus-repository-npm</feature>@nexus-repository-npm</feature>\n        <feature prerequisite="false" dependency="false">nexus-repository-p2</feature>@g' $NEXUS_FEATURES_FILE \
    && sed -i 's@<feature name="nexus-repository-npm"@<feature name="nexus-repository-p2" description="org.sonatype.nexus.plugins:nexus-repository-p2" version="'"$P2_VERSION"'">\n        <details>org.sonatype.nexus.plugins:nexus-repository-p2</details>\n        <bundle>mvn:org.sonatype.nexus.plugins/nexus-repository-p2/'"$P2_VERSION"'</bundle>\n    </feature>\n    <feature name="nexus-repository-npm"@g' $NEXUS_FEATURES_FILE \
    && unset NEXUS_PATH NEXUS_FEATURES_FILE P2_VERSION
USER nexus
