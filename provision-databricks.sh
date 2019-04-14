#!/bin/bash

# Strict mode, fail on any error
set -euo pipefail

echo "Installing Databricks CLI"
sudo apt-get install -y python3-setuptools
pip3 install wheel
pip3 install databricks-cli
sudo ln -s /home/vsts/.local/bin/* /usr/local/bin/

# Databricks cluster to be created
cluster_name="$RESOURCE_NAME_PREFIX$BUILD_BUILDID"

echo "Creating Databricks cluster"
cluster=$(databricks clusters create --json "$(cat << JSON
{
  "cluster_name": "$cluster_name",
  "spark_version": "5.3.x-scala2.11",
  "node_type_id": "Standard_DS3_v2",
  "autoscale": {
    "min_workers": 1,
    "max_workers": 3
  },
  "autotermination_minutes": 120
}
JSON
)"
)

cluster_id=$(echo $cluster | jq -r .cluster_id)
sleep 10 #avoid race conditions

echo "Installing Spline libraries"
databricks_spline=$(ls databricks-spline/target/databricks-spline-*.jar | head -1)
echo "Installing library $databricks_spline"
databricks_spline_base=$(basename "$databricks_spline")
databricks_spline_dbfs="dbfs:/lib/spline/$databricks_spline_base"
databricks fs cp "$databricks_spline" "$databricks_spline_dbfs" --overwrite
databricks libraries install --cluster-id $cluster_id --jar "$databricks_spline_dbfs"

echo "Provisioning Spline connection string as Databricks secret"
if ! databricks secrets list-scopes --output JSON | jq -e '.scopes[] | select (.name == "spline")'; then
    databricks secrets create-scope --scope spline --initial-manage-principal "users"
fi
databricks secrets put --scope spline --key spline.mongodb.url --string-value "$COSMOSDB_CONN_STRING"


# Copy and run sample notebooks

echo "Copying sample notebooks"
databricks workspace import_dir notebooks /Shared/lineage-tutorial --overwrite

for notebook in notebooks/*.scala; do

  notebook_name=$(basename $notebook .scala)
  notebook_path="/Shared/lineage-tutorial/$notebook_name"
  echo "Running notebook $notebook_path"
  run=$(databricks runs submit --json "$(cat << JSON
  {
    "name": "SampleRun",
    "existing_cluster_id": "$cluster_id",
    "timeout_seconds": 1200,
    "notebook_task": {
      "notebook_path": "$notebook_path"
    }
  }
JSON
  )")

  # Echo job web page URL to task output to facilitate debugging
  run_id=$(echo $run | jq .run_id)
  databricks runs get --run-id "$run_id" | jq -r .run_page_url


done

