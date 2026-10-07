#!/bin/sh
# Starts Magnolia in Tomcat. On first boot Magnolia installs all modules by itself
# (magnolia.update.auto=true); on every start the supertext-demo module creates the
# DEMO_ADMIN_* / DEMO_EDITOR_* accounts if missing and the sample page /supertext-demo.
# Never print variable values here: they include passwords.
set -e

mkdir -p /data/keys /data/logs /data/tmp

if [ -z "$DEMO_ADMIN_EMAIL" ] || [ -z "$DEMO_ADMIN_PASSWORD" ]; then
  echo "WARNING: DEMO_ADMIN_EMAIL / DEMO_ADMIN_PASSWORD are not set: no demo administrator is created," \
       "and Magnolia's built-in superuser account stays enabled. Set them before exposing the demo." >&2
fi

# Railway passes the port to listen on.
if [ -n "$PORT" ] && [ "$PORT" != "8080" ]; then
  sed -i "s/port=\"8080\"/port=\"$PORT\"/" /usr/local/tomcat/conf/server.xml
fi

export CATALINA_OPTS="${CATALINA_OPTS} -Xms512m -Xmx${MAGNOLIA_HEAP:-1536m} -Djava.awt.headless=true -Dfile.encoding=UTF-8"
exec /usr/local/tomcat/bin/catalina.sh run
