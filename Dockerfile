ARG NEXUS_VERSION=3.8.0

FROM maven:3-jdk-8-alpine AS build
ARG NEXUS_VERSION=3.8.0
ARG NEXUS_BUILD=02

COPY . /nexus-repository-p2/
RUN cd /nexus-repository-p2/; sed -i "s/3.8.0-02/${NEXUS_VERSION}-${NEXUS_BUILD}/g" pom.xml; \
    mvn clean package;

FROM sonatype/nexus3:$NEXUS_VERSION
ARG NEXUS_VERSION=3.8.0
ARG NEXUS_BUILD=02
ARG P2_VERSION=0.0.2
ARG TARGET_DIR=/opt/sonatype/nexus/system/org/sonatype/nexus/plugins/nexus-repository-p2/${P2_VERSION}/
USER root
RUN mkdir -p ${TARGET_DIR}; \
    sed -i 's@nexus-repository-npm</feature>@nexus-repository-npm</feature>\n        <feature prerequisite="false" dependency="false">nexus-repository-p2</feature>@g' /opt/sonatype/nexus/system/com/sonatype/nexus/assemblies/nexus-oss-feature/${NEXUS_VERSION}-${NEXUS_BUILD}/nexus-oss-feature-${NEXUS_VERSION}-${NEXUS_BUILD}-features.xml; \
    sed -i 's@<feature name="nexus-repository-npm"@<feature name="nexus-repository-p2" description="org.sonatype.nexus.plugins:nexus-repository-p2" version="0.0.2">\n        <details>org.sonatype.nexus.plugins:nexus-repository-p2</details>\n        <bundle>mvn:org.sonatype.nexus.plugins/nexus-repository-p2/0.0.2</bundle>\n    </feature>\n    <feature name="nexus-repository-npm"@g' /opt/sonatype/nexus/system/com/sonatype/nexus/assemblies/nexus-oss-feature/${NEXUS_VERSION}-${NEXUS_BUILD}/nexus-oss-feature-${NEXUS_VERSION}-${NEXUS_BUILD}-features.xml;
COPY --from=build /nexus-repository-p2/target/nexus-repository-p2-${P2_VERSION}.jar ${TARGET_DIR}
USER nexus
