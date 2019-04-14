#!/bin/bash

# Strict mode, fail on any error
set -euo pipefail

# Clone the repository to be built
git clone --single-branch --branch $GIT_BRANCH https://github.com/AbsaOSS/spline.git

# The name of the Cosmos DB instance to be deployed. Generate a unique name.
COSMOSDB_INSTANCE="$RESOURCE_NAME_PREFIX$BUILD_BUILDID"

# Create a Cosmos DB database. This command has no effect if the database already exists.
az cosmosdb create -g $RESOURCE_GROUP -n $COSMOSDB_INSTANCE --kind MongoDB --capabilities EnableAggregationPipeline -o table

# Get the connection string (in mongodb:// format) to the Cosmos DB account.
# The connection string contains the account key.
# Example connection string:
#    mongodb://mycosmosdb:kmRux...XBQ==@mycosmosdb.documents.azure.com:10255/?ssl=true&replicaSet=globaldb
cosmosdb_conn_string=$(az cosmosdb list-connection-strings -g $RESOURCE_GROUP -n $COSMOSDB_INSTANCE --query connectionStrings[0].connectionString -o tsv)

# Add the database name within the connection string (before the '?' delimiter).
COSMOSDB_CONN_STRING=${cosmosdb_conn_string/\?/spline?}

# Set job variables from script
echo "##vso[task.setvariable variable=COSMOSDB_CONN_STRING]$COSMOSDB_CONN_STRING"
