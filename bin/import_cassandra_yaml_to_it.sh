#!/bin/bash
##
# Copyright 2018 Telefonaktiebolaget LM Ericsson
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Use this script to import and apply tags in the cassandra.yaml file for tests.
# These tags are used by the embedded cassandra daemon in the integration tests.

shopt -s extglob

SCRIPT_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

if [[ $# -ne 1 ]]; then
 echo "Missing argument - specify cassandra.yaml to import into integration tests"
 exit 2
fi

IMPORT_YAML=$1

format_for_it () {
 echo "Importing $1 into $2"
 cp $1 $2
 sed -i 's/# \(data_file_directories:\)/\1/' $2
 sed -i 's@^# \(.*\)/var/lib/cassandra/\(.*\)@\1###tmp###/cassandra/\2@' $2
 sed -i 's/^\(authenticator: \).*/\1com.ericsson.bss.cassandra.ecaudit.auth.AuditPasswordAuthenticator/' $2
 sed -i 's/^\(authorizer: \).*/\1com.ericsson.bss.cassandra.ecaudit.auth.AuditAuthorizer/' $2
 sed -i 's/^\(role_manager: \).*/\1com.ericsson.bss.cassandra.ecaudit.auth.AuditRoleManager/' $2
 sed -i 's/roles_validity_in_ms:.*/roles_validity_in_ms: 0/' $2
 sed -i 's/permissions_validity_in_ms:.*/permissions_validity_in_ms: 0/' $2
 sed -i 's/credentials_validity_in_ms:.*/credentials_validity_in_ms: 0/' $2
 sed -i 's/key_cache_size_in_mb:.*/key_cache_size_in_mb: 0/' $2
 sed -i 's/counter_cache_size_in_mb:.*/counter_cache_size_in_mb: 0/' $2
 sed -i 's/^storage_port:.*/storage_port: ###storage_port###/' $2
 sed -i 's/^ssl_storage_port:.*/ssl_storage_port: ###ssl_storage_port###/' $2
 sed -i 's/^native_transport_port:.*/native_transport_port: ###native_transport_port###/' $2
 sed -i 's/^rpc_port:.*/rpc_port: ###rpc_port###/' $2
 sed -i 's/^num_tokens:.*/num_tokens: 1/' $2
 sed -i 's/^enable_user_defined_functions:.*/enable_user_defined_functions: true/' $2
}

for IT_TARGET in ${SCRIPT_PATH}/../integration-test-*/src/test/resources/cassandra.yaml;
do
 format_for_it ${IMPORT_YAML} ${IT_TARGET}
done
