#!/bin/bash
#
# Copyright 2019 Telefonaktiebolaget LM Ericsson
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

shopt -s extglob

SCRIPT_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
CCM_CONFIG=${CCM_CONFIG_DIR:=~/.ccm}
JAR_FILE="${SCRIPT_PATH}/../ecaudit/target/ecaudit*.jar"

if [[ $# -ne 1 ]]; then
 echo "Missing argument - specify version of ccm cluster to create"
 exit 2
fi

CLUSTER_VERSION=$1

which ccm > /dev/null
if [[ $? -ne 0 ]]; then
 echo "ccm must be installed"
 exit 3
fi

ccm status | grep -qs UP
if [[ $? -eq 0 ]]; then
 echo "ccm cluster already running"
 exit 3
fi

if [[ ! -e ${CCM_CONFIG}/repository/3.11.4/tools/bin/cassandra-stress ]]; then
 echo "ccm must have cassandra-stress version 3.11.4 in repository"
 exit 3
fi

ccm create -n 1 -v ${CLUSTER_VERSION} ecaudit
if [[ $? -ne 0 ]]; then
 echo "Failed to create ccm cluster 'ecaudit'"
 exit 3
fi

if [ ! -f ${JAR_FILE} ]; then
 echo "No jar file found. Build project and try again."
 exit 3
fi

echo "Generating performance report into ecaudit-performance.html"

ccm start
sleep 30
${CCM_CONFIG}/repository/3.11.4/tools/bin/cassandra-stress write n=3000000 -node 127.0.0.1 -port jmx=7100 -mode native cql3 user=cassandra password=cassandra -rate threads=10 -graph file=ecaudit-performance.html title=ecAudit-Performance revision=vanilla
ccm clear
sleep 5

${SCRIPT_PATH}/configure_ccm_cassandra_auth.sh
ccm start
sleep 30
${CCM_CONFIG}/repository/3.11.4/tools/bin/cassandra-stress write n=3000000 -node 127.0.0.1 -port jmx=7100 -mode native cql3 user=cassandra password=cassandra -rate threads=10 -graph file=ecaudit-performance.html title=ecAudit-Performance revision=authentication-authorization
ccm clear
sleep 5

${SCRIPT_PATH}/configure_ccm_audit_chronicle.sh
ccm start
sleep 30
ccm node1 cqlsh -u cassandra -p cassandra -x "ALTER ROLE cassandra WITH OPTIONS = { 'GRANT AUDIT WHITELIST FOR ALL': 'data' };"
${CCM_CONFIG}/repository/3.11.4/tools/bin/cassandra-stress write n=3000000 -node 127.0.0.1 -port jmx=7100 -mode native cql3 user=cassandra password=cassandra -rate threads=10 -graph file=ecaudit-performance.html title=ecAudit-Performance revision=authentication-authorization-audit-whitelist
ccm clear
sleep 5

${SCRIPT_PATH}/configure_ccm_audit_chronicle.sh
ccm start
sleep 30
ccm node1 cqlsh -u cassandra -p cassandra -x "ALTER ROLE cassandra WITH OPTIONS = { 'REVOKE AUDIT WHITELIST FOR ALL': 'data' };"
${CCM_CONFIG}/repository/3.11.4/tools/bin/cassandra-stress write n=3000000 -node 127.0.0.1 -port jmx=7100 -mode native cql3 user=cassandra password=cassandra -rate threads=10 -graph file=ecaudit-performance.html title=ecAudit-Performance revision=authentication-authorization-audit-chronicle
ccm clear
sleep 5

${SCRIPT_PATH}/configure_ccm_audit_slf4j.sh
ccm start
sleep 30
ccm node1 cqlsh -u cassandra -p cassandra -x "ALTER ROLE cassandra WITH OPTIONS = { 'REVOKE AUDIT WHITELIST FOR ALL': 'data' };"
${CCM_CONFIG}/repository/3.11.4/tools/bin/cassandra-stress write n=3000000 -node 127.0.0.1 -port jmx=7100 -mode native cql3 user=cassandra password=cassandra -rate threads=10 -graph file=ecaudit-performance.html title=ecAudit-Performance revision=authentication-authorization-audit-slf4j
ccm clear
sleep 5
