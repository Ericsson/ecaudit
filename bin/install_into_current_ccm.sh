#!/bin/bash
#
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

shopt -s extglob

SCRIPT_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
CCM_CONFIG=${CCM_CONFIG_DIR:=~/.ccm}

if [ ! -f ${CCM_CONFIG}/CURRENT ]; then
 echo "Unable to find an active ccm cluster"
 exit 2
fi

if [ ! -f ${SCRIPT_PATH}/../target/ecaudit*.jar ]; then
 echo "No jar file found. Build project and try again."
 exit 3
fi

CCM_CLUSTER_NAME=`cat ${CCM_CONFIG}/CURRENT`
echo "Installing ecAudit into ${CCM_CLUSTER_NAME}"

CLUSTER_PATH=${CCM_CONFIG}/${CCM_CLUSTER_NAME}

mkdir -p ${CLUSTER_PATH}/lib
rm -f ${CLUSTER_PATH}/lib/ecaudit.jar
ln -s ${SCRIPT_PATH}/../target/ecaudit*.jar ${CLUSTER_PATH}/lib/ecaudit.jar

grep -sq ecaudit.jar ${CLUSTER_PATH}/cassandra.in.sh
if [ $? -ne 0 ]; then
 echo "CLASSPATH=\"\$CLASSPATH:${CLUSTER_PATH}/lib/ecaudit.jar\"" >> ${CLUSTER_PATH}/cassandra.in.sh
 echo "JVM_EXTRA_OPTS=\"\$JVM_EXTRA_OPTS -Dcassandra.custom_query_handler_class=com.ericsson.bss.cassandra.ecaudit.handler.AuditQueryHandler\"" >> ${CLUSTER_PATH}/cassandra.in.sh
fi

for NODE_PATH in ${CLUSTER_PATH}/node*;
do
 sed -i 's/^authenticator:.*/authenticator: com.ericsson.bss.cassandra.ecaudit.auth.AuditPasswordAuthenticator/' ${NODE_PATH}/conf/cassandra.yaml
 sed -i 's/^authorizer:.*/authorizer: com.ericsson.bss.cassandra.ecaudit.auth.AuditAuthorizer/' ${NODE_PATH}/conf/cassandra.yaml
 sed -i 's/^role_manager:.*/role_manager: com.ericsson.bss.cassandra.ecaudit.auth.AuditRoleManager/' ${NODE_PATH}/conf/cassandra.yaml
done
