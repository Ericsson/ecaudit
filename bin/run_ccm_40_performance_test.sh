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

if [[ $# -ne 1 ]]; then
 echo "Missing argument - specify path to cassandra 4.0 source dir"
 echo " hint: ant realclean jar"
 exit 2
fi

CASSANDRA_SOURCE=$1

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

ccm create -n 1 --install-dir=${CASSANDRA_SOURCE} 40audit
if [[ $? -ne 0 ]]; then
 echo "Failed to create ccm cluster '40audit'"
 exit 3
fi

echo "Generating performance report into 40audit-performance.html"

ccm start
sleep 30
${CASSANDRA_SOURCE}/tools/bin/cassandra-stress write n=3000000 -node 127.0.0.1 -port jmx=7100 -mode native cql3 user=cassandra password=cassandra -rate threads=10 -graph file=40audit-performance.html title=40Audit-Performance revision=vanilla
ccm clear
sleep 5

${SCRIPT_PATH}/configure_ccm_cassandra_auth.sh
ccm start
sleep 30
${CASSANDRA_SOURCE}/tools/bin/cassandra-stress write n=3000000 -node 127.0.0.1 -port jmx=7100 -mode native cql3 user=cassandra password=cassandra -rate threads=10 -graph file=40audit-performance.html title=40Audit-Performance revision=authentication-authorization
ccm clear
sleep 5

${SCRIPT_PATH}/configure_ccm_40audit_chronicle.sh
ccm start
sleep 30
ccm node1 nodetool "enableauditlog --excluded-users cassandra"
${CASSANDRA_SOURCE}/tools/bin/cassandra-stress write n=3000000 -node 127.0.0.1 -port jmx=7100 -mode native cql3 user=cassandra password=cassandra -rate threads=10 -graph file=40audit-performance.html title=40Audit-Performance revision=authentication-authorization-audit-whitelist
ccm clear
sleep 5

${SCRIPT_PATH}/configure_ccm_40audit_chronicle.sh
ccm start
sleep 30
ccm node1 nodetool "enableauditlog --included-users cassandra"
${CASSANDRA_SOURCE}/tools/bin/cassandra-stress write n=3000000 -node 127.0.0.1 -port jmx=7100 -mode native cql3 user=cassandra password=cassandra -rate threads=10 -graph file=40audit-performance.html title=40Audit-Performance revision=authentication-authorization-audit-chronicle
ccm clear
sleep 5

#${SCRIPT_PATH}/configure_ccm_audit_slf4j.sh
#ccm start
#sleep 30
#ccm node1 cqlsh -u cassandra -p cassandra -x "ALTER ROLE cassandra WITH OPTIONS = { 'REVOKE AUDIT WHITELIST FOR ALL': 'data' };"
#${CASSANDRA_SOURCE}/tools/bin/cassandra-stress write n=3000000 -node 127.0.0.1 -port jmx=7100 -mode native cql3 user=cassandra password=cassandra -rate threads=10 -graph file=40audit-performance.html title=40Audit-Performance revision=authentication-authorization-audit-slf4j
#ccm clear
#sleep 5
