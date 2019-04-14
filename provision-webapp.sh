#!/bin/bash

# Strict mode, fail on any error
set -euo pipefail

mv spline/web/target/spline-ui*.war ROOT.war

WEBAPP_NAME="$RESOURCE_NAME_PREFIX$BUILD_BUILDID"

az appservice plan create -g $RESOURCE_GROUP -n $WEBAPP_NAME -o table

az webapp create -g $RESOURCE_GROUP -n $WEBAPP_NAME --plan $WEBAPP_NAME -o table

az webapp config set -g $RESOURCE_GROUP -n $WEBAPP_NAME --java-container TOMCAT --java-container-version 7.0.62 --java-version 1.8 -o table

az webapp config appsettings set -g $RESOURCE_GROUP -n $WEBAPP_NAME --settings "spline.mongodb.url=$COSMOSDB_CONN_STRING" -o table

az webapp config set -g $RESOURCE_GROUP -n $WEBAPP_NAME --always-on true -o table

WEBAPP_URL="https://$(az webapp show -g $RESOURCE_GROUP -n $WEBAPP_NAME | jq -r .defaultHostName)"

# Set job variables from script
echo "##vso[task.setvariable variable=WEBAPP_NAME]$WEBAPP_NAME"
echo "##vso[task.setvariable variable=WEBAPP_URL]$WEBAPP_URL"
