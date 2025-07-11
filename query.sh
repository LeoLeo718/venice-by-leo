#!/bin/bash

# Only works on files for now since. It fails for special directories "." and "/" since "basename" and "dirname" returns
# the same value
function real_path() {
  local dir_path=$(cd "$(dirname "$1")" && pwd)
  local file_name=$(basename "$1")
  echo "$dir_path/$file_name"
}

function is_compatible () {
  "$@" >/dev/null 2>&1
}

function is_compatible_date_f() {
    is_compatible date -j -f "%b %d %X %Y %Z" "Jan 1 00:00:00 2022 GMT"
}

function is_compatible_date_d() {
    is_compatible date -d "Jan 1 00:00:00 2022 GMT"
}

is_compatible_date_f
useBsdDate=$?

is_compatible_date_d
useGnuDate=$?

if [ $useBsdDate -eq 1 ] && [ $useGnuDate -eq 1 ] ; then
  echo "ERROR: Cannot identify which flavor of 'date' command to use. Please report the error."
  exit 1
fi

# GNU: date -d "Apr 29 16:43:30 2022 GMT" +"%s"
# BSD: date -j -f "%b %d %X %Y %Z" "Apr 29 16:43:30 2022 GMT" +"%s"
function parse_openssl_date_to_epoch_seconds() {
  if [ $useGnuDate -eq 0 ]; then
    date -d "$1" +"%s"
  elif [ $useBsdDate -eq 0 ]; then
    date -j -f "%b %d %X %Y %Z" "$1" +"%s"
  fi
}

script_real_path=$(real_path "${BASH_SOURCE[0]}") # Absolute path
base_dir=$(dirname "$script_real_path")

function cert_has_enough_time() {
  cert=$1
  password=$2

  not_after=$(2>/dev/null openssl pkcs12 -in "$cert" -clcerts -nodes -passin "pass:$password" | openssl x509 -noout -enddate | cut -d= -f2)
  expiration_time=$(parse_openssl_date_to_epoch_seconds "$not_after")
  current_time=$(date +%s)
  remaining_time=$((expiration_time - current_time))

  # Cert must be valid for a small buffer zone so that during execution of this program the certificate will remain valid.
  if (( remaining_time < 30 )); then    # in seconds
    return 1
  else
    return 0
  fi
}

function download_fat_jar() {
  artifactory_base_path="$1"
  version="$2"
  local_base_path="$3"

  mkdir -p "$local_base_path"
  # Do not use wget, because it is not available on Mac
  curl -s "$artifactory_base_path/$version/venice-thin-client-$version-standalone.jar" > "$local_base_path/venice-thin-client-$version-standalone.tmp"
  # This is to detect interrupted download
  mv "$local_base_path/venice-thin-client-$version-standalone.tmp" "$local_base_path/venice-thin-client-$version-standalone.jar"
}

if [ $# -lt 3 ]; then
  echo "  Usage: $0 <fabric> <store_name> <key_string> [is_vson_store] [facet_counting_mode] [count_by_value_fields] [top_k] [count_by_bucket_fields] [bucket_definitions]"
  echo "  Example: $0 ei-ltx1 LeapContentRecommendationTest '{\"contractId\":2660,\"memberId\":1,\"modelVersionId\":\"testModel\",\"source\":\"NEARLINE\"}'"
  echo "  Example: $0 ei-ltx1 store_name 'key1,key2,key3' false countByValue 'firstName,lastName' 5"
  echo "  Example: $0 ei-ltx1 store_name 'key1,key2,key3' false countByBucket 'age' 10 'young:lt:30,senior:gte:30'"
  exit 1
else
  # Initialize essential variables
  fabric="$1"
  store_name="$2"
  key_string="$3"
  if [ $# -gt 3 ]; then
    is_vson_store="$4"
  else
    is_vson_store="false"
  fi
  
  # Parse optional aggregation parameters
  facet_counting_mode="single"
  count_by_value_fields=""
  top_k="10"
  count_by_bucket_fields=""
  bucket_definitions=""
  
  if [ $# -gt 4 ]; then
    facet_counting_mode="$5"
  fi
  
  if [ $# -gt 5 ] && [ "$facet_counting_mode" = "countByValue" ]; then
    count_by_value_fields="$6"
  fi
  
  if [ $# -gt 6 ] && [ "$facet_counting_mode" = "countByValue" ]; then
    top_k="$7"
  fi
  
  if [ $# -gt 5 ] && [ "$facet_counting_mode" = "countByBucket" ]; then
    count_by_bucket_fields="$6"
  fi
  
  if [ $# -gt 6 ] && [ "$facet_counting_mode" = "countByBucket" ]; then
    bucket_definitions="$7"
  fi
fi

# === 强制使用本地 build 的 fat jar ===
jar_file="clients/venice-thin-client/build/libs/venice-thin-client-all.jar"
# === 注释掉自动下载和查找逻辑 ===
: <<'SKIP_AUTO_JAR'
artifactory_base_path="https://artifactory.corp.linkedin.com:8083/artifactory/DDS/com/linkedin/venice-thin-client/venice-thin-client"
local_base_path="build/venice-thin-client/libs"
latest_version=$(2>/dev/null curl -s "$artifactory_base_path/" | grep "^<a href=" | cut -d'"' -f2 | sort -V | tail -n1 | tr -d '/')

if [[ ! -e "$local_base_path" ]]; then
  echo "First time running the script. This will take a short while, but next time you won't have to wait again."

  # Download the fat jar if possible, otherwise built it
  if [[ -n "$latest_version" ]]; then
    echo "Downloading Venice client v$latest_version ..."
    download_fat_jar "$artifactory_base_path" "$latest_version" "$local_base_path"
  else
    echo "WARN: Cannot download Venice client fat jar from Artifactory. Try to build it instead ..."
    mint build
  fi
fi

jar_file=$(find "$local_base_path" -type f -name 'venice-thin-client-*-standalone.jar' | sort -V | tail -1)
if [[ ! -e "$jar_file" ]]; then
  # It's possible that user killed the build process in previous run, but the "build" directory has been created.
  echo "WARN: Cannot locate Venice client fat jar. Retrying ..."
  echo "Please let this process finish"
  rm -rf build && mint build
else
  # Auto upgrade local jar to the latest available version
  current_version=$(echo "$jar_file" | grep -Eo "([0-9]+\.?){3}")
  # It's possible that local version is actually newer than the latest on Artifactory, in that case we don't want to download
  newer_version=$(echo -e "$current_version\n$latest_version" | sort -V | tail -1)
  if [[ "$current_version" != "$newer_version" ]]; then
    echo "Updating Venice client from v$current_version to v$latest_version ..."
    download_fat_jar "$artifactory_base_path" "$latest_version" "$local_base_path"
    rm "$jar_file"    # remove old version
  fi
fi

# Check again
jar_file=$(find "$local_base_path" -type f -name 'venice-thin-client-*-standalone.jar' | sort -V | tail -1)
if [[ ! -e "$jar_file" ]]; then
  echo "ERROR: Cannot locate Venice client fat jar. Please report the error."
  exit 1
fi
SKIP_AUTO_JAR

# Prepare SSL configuration file
ssl_config_file="${base_dir}/ssl.config"
ssl_configs="ssl.enabled=true
ssl.keystore.type=PKCS12
ssl.keystore.password=work_around_jdk-6879539
ssl.keystore.location=${base_dir}/identity.p12
ssl.truststore.password=changeit
ssl.truststore.location=/etc/riddler/cacerts"
echo -n "$ssl_configs" > "$ssl_config_file"


# Validate certificate
if ! grep -q "ssl.keystore.password=" "$ssl_config_file"; then
  echo "ERROR: $ssl_config_file does not contain ssl.keystore.password"
  exit 1
elif ! grep -q "ssl.keystore.location=" "$ssl_config_file"; then
  echo "ERROR: $ssl_config_file does not contain ssl.keystore.location"
  exit 1
elif ! grep -q "ssl.keystore.type=" "$ssl_config_file"; then
  echo "ERROR: $ssl_config_file does not contain ssl.keystore.type"
  exit 1
elif ! grep -q "ssl.truststore.location=" "$ssl_config_file"; then
  echo "ERROR: $ssl_config_file does not contain ssl.truststore.location"
  exit 1
elif ! grep -q "ssl.truststore.password=" "$ssl_config_file"; then
  echo "ERROR: $ssl_config_file does not contain ssl.truststore.password"
  exit 1
elif ! grep -q "ssl.enabled=" "$ssl_config_file"; then
  echo "ERROR: $ssl_config_file does not contain ssl.enabled"
  exit 1
fi

keystore_password=$(grep "ssl.keystore.password=" "$ssl_config_file" | cut -d= -f2-)
keystore_path=$(grep "ssl.keystore.location=" "$ssl_config_file" | cut -d= -f2-)
keystore_real_path=$(real_path "$keystore_path")
truststore_path=$(grep "ssl.truststore.location=" "$ssl_config_file" | cut -d= -f2-)

if [[ "$keystore_path" != "$keystore_real_path" ]]; then
  echo "ERROR: Please use absolute path for $keystore_path"
  exit 1
elif [[ ! -e "$truststore_path" ]]; then
  echo "ERROR: $truststore_path does not exist"
  exit 1
elif [[ ! -e "$keystore_path" ]]; then
  # $keystore_path does not exist
  echo
  echo "Creating certificate ..."
  if ! id-tool grestin sign -o "$base_dir"; then
    echo "ERROR: Failed to create certificate"
    exit 1
  fi
elif ! cert_has_enough_time "$keystore_path" "$keystore_password"; then
  echo "Your certificate $keystore_path has expired. Creating new certificate ..."
  if ! id-tool grestin sign -o "$base_dir"; then
    echo "ERROR: Failed to renew certificate"
    exit 1
  fi
fi

if [[ ! -e "${base_dir}/identity.p12" ]]; then
  echo "ERROR: Cannot locate identity.p12 keystore file. Please report the error."
  exit 1
elif ! cert_has_enough_time "$keystore_path" "$keystore_password"; then
  echo "ERROR: Cannot renew expired certificate. Please report the error."
  exit 1
fi

echo "Gathering information from remote ..."

# Discover cluster for store
d2_result=$(2>.stderr curli --no-log --force-insecure-d2 --fabric "$fabric" "d2://venice-discovery/discover_cluster/$store_name")
error=$(<.stderr)

if [[ "$error" == *"[ERROR]"* ]]; then
  echo "$error"
  exit 1
fi

if [[ "$d2_result" == *"doesn't exist"* ]]; then
  echo "ERROR: Invalid store name $store_name"
  exit 1
elif [[ -z "$d2_result" ]]; then
  echo "ERROR: D2 returned nothing"
  exit 1
fi

d2_cluster=$(echo "$d2_result" | grep "d2Service" | cut -d: -f2 | tr -d ' ",')

if [[ -z "$d2_cluster" ]]; then
  echo "ERROR: Cannot determine D2 cluster. Please try again. If the issue persists, please report the error to Venice team."
  echo "D2 result: $d2_result"
  exit 1
fi

# Choose a Venice router that supports https
router_url=$(2>/dev/null curli --no-log --force-insecure-d2 --fabric "$fabric" "d2://d2Clusters/$d2_cluster" | grep "https://" | head -1 | cut -d: -f2- | tr -d ' ",')

if [[ -z $router_url ]]; then
  echo "ERROR: Cannot determine router URL. Please try again. If the issue persists, please report the error to Venice team."
  exit 1
fi

echo "Checking local environment ..."

# Prepare for invocation
# Check if java exists
if ! command -v java > /dev/null; then
  # Cannot find java in default $PATH. Give it a second chance.
  # The following works on Linux only.
  jdk_dir=$(find '/export/apps/jdk/' -type d -name 'JDK-*' | grep -e JDK-1_8 -e JDK-11 -e JDK-17 | sort -V | tail -1) # Latest compatible version
  if [[ -z $jdk_dir ]]; then
    # Most likely running on a Mac.
    echo 'ERROR: Cannot find "java" from PATH'
    exit 1
  fi
  PATH="$jdk_dir/bin:$PATH"
fi

echo
echo "Will send a request to $router_url for store $store_name with key string: $key_string"

router_hostname_and_port=$(echo "$router_url" | sed 's/https:\/\///')
router_hostname=$(echo "$router_hostname_and_port" | cut -d: -f1)
router_port=$(echo "$router_hostname_and_port" | cut -d: -f2)

if ! echo > "/dev/tcp/$router_hostname/$router_port"; then
  echo
  echo "ERROR: Failed to establish connection to Venice router $router_hostname"
  echo "You must run this tool in $fabric"
  exit 1
fi

# Build the command arguments
java_args=("$jar_file" "$store_name" "$key_string" "$router_url" "$is_vson_store" "$ssl_config_file")

# Add aggregation parameters if needed
if [ "$facet_counting_mode" != "single" ]; then
  java_args+=("$facet_counting_mode")
  
  if [ "$facet_counting_mode" = "countByValue" ] && [ -n "$count_by_value_fields" ]; then
    java_args+=("$count_by_value_fields")
    java_args+=("$top_k")
  elif [ "$facet_counting_mode" = "countByBucket" ] && [ -n "$count_by_bucket_fields" ]; then
    java_args+=("$count_by_bucket_fields")
    if [ -n "$bucket_definitions" ]; then
      java_args+=("$bucket_definitions")
    fi
  fi
fi

java -jar "${java_args[@]}" 2>.stderr
query_command_status=$?
error_message=$(<.stderr)
echo "$error_message"
if [ $query_command_status -ne 0 ] && [[ $error_message = *"Error: Invalid or corrupt jarfile"* ]]; then
  installed_java_version="$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')"
  echo "Currently configured JRE version ($installed_java_version) may be outdated. Please update JAVA_HOME and try again."
fi
