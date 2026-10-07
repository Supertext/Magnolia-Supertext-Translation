# Supertext Magnolia demo: Magnolia 6.4 Community Edition (EN, DE-CH, FR-CH, IT-CH) + this module.
# Build context is the repository root:
#   docker build -f demo/Dockerfile -t supertext-magnolia-demo .
# State (JCR repository, keys, logs) lives in /data (mount a volume). See docs/DEVELOPER.md -> Demo.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Module first (its dependencies are cached in their own layer).
COPY pom.xml pom.xml
RUN mvn -B -q dependency:go-offline -DexcludeArtifactIds=magnolia-supertext-translation || true
COPY src/ src/
RUN mvn -B -q install -DskipTests
# Then the demo webapp, against the module version just installed.
COPY demo/pom.xml demo/pom.xml
COPY demo/src/ demo/src/
RUN version=$(mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout) \
    && mvn -B -q -f demo/pom.xml package -Dsupertext.version="$version" \
    && mkdir -p /webapp && cd /webapp && jar xf /src/demo/target/ROOT.war

FROM tomcat:11.0-jdk21-temurin
RUN rm -rf /usr/local/tomcat/webapps/* \
    # Railway terminates TLS at its proxy: trust X-Forwarded-Proto/For.
    && sed -i 's#</Host>#  <Valve className="org.apache.catalina.valves.RemoteIpValve" remoteIpHeader="x-forwarded-for" protocolHeader="x-forwarded-proto" />\n      </Host>#' /usr/local/tomcat/conf/server.xml
COPY --from=build /webapp /usr/local/tomcat/webapps/ROOT
COPY demo/entrypoint.sh /usr/local/bin/magnolia-entrypoint
RUN chmod +x /usr/local/bin/magnolia-entrypoint && mkdir -p /data

ENV PORT=8080 \
    MAGNOLIA_HEAP=1536m

# No VOLUME instruction: Railway rejects it and mounts its own volume at /data.
EXPOSE 8080
ENTRYPOINT ["magnolia-entrypoint"]
