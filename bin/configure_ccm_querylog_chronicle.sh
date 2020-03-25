#!/bin/bash
#
# Copyright 2020 Telefonaktiebolaget LM Ericsson
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

if [ ! -f ${CCM_CONFIG}/CURRENT ]; then
 echo "Unable to find an active ccm cluster"
 exit 2
fi

if [ ! -f ${JAR_FILE} ]; then
 echo "No jar file found. Build project and try again."
 exit 3
fi

CCM_CLUSTER_NAME=`cat ${CCM_CONFIG}/CURRENT`
echo "Installing ecAudit with Chronicle backend into ${CCM_CLUSTER_NAME}"

CLUSTER_PATH=${CCM_CONFIG}/${CCM_CLUSTER_NAME}

mkdir -p ${CLUSTER_PATH}/lib
rm -f ${CLUSTER_PATH}/lib/ecaudit.jar
ln -s ${JAR_FILE} ${CLUSTER_PATH}/lib/ecaudit.jar

grep -sq ecaudit.jar ${CLUSTER_PATH}/cassandra.in.sh
if [ $? -ne 0 ]; then
 echo "CLASSPATH=\"\$CLASSPATH:${CLUSTER_PATH}/lib/ecaudit.jar\"" >> ${CLUSTER_PATH}/cassandra.in.sh
 echo "JVM_EXTRA_OPTS=\"\$JVM_EXTRA_OPTS -Dcassandra.custom_query_handler_class=com.ericsson.bss.cassandra.ecaudit.handler.AuditQueryHandler\"" >> ${CLUSTER_PATH}/cassandra.in.sh
fi

grep -sq openhft ${CLUSTER_PATH}/cassandra.in.sh
if [ $? -ne 0 ]; then
 echo "JVM_EXTRA_OPTS=\"\$JVM_EXTRA_OPTS -da:net.openhft...\"" >> ${CLUSTER_PATH}/cassandra.in.sh
fi

grep -sq "filter_type=NONE" ${CLUSTER_PATH}/cassandra.in.sh
if [ $? -ne 0 ]; then
 sed -i '/filter_type/d' ${CLUSTER_PATH}/cassandra.in.sh
 echo "JVM_EXTRA_OPTS=\"\$JVM_EXTRA_OPTS -Decaudit.filter_type=NONE\"" >> ${CLUSTER_PATH}/cassandra.in.sh
fi

update_audit_yaml() {
 mkdir -p $2
 rm -f $1
 cat <<EOF > $1

logger_backend:
  - class_name: com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger
    parameters:
      - log_dir: $2
        roll_cycle: MINUTELY
        max_log_size: 1073741824 # 1GB
        fields: TIMESTAMP, OPERATION, BATCH_ID, STATUS
EOF
}

for NODE_PATH in ${CLUSTER_PATH}/node*;
do
 sed -i 's/^authenticator:.*/authenticator: AllowAllAuthenticator/' ${NODE_PATH}/conf/cassandra.yaml
 sed -i 's/^authorizer:.*/authorizer: AllowAllAuthorizer/' ${NODE_PATH}/conf/cassandra.yaml
 sed -i 's/^role_manager:.*/role_manager: com.ericsson.bss.cassandra.ecaudit.auth.AuditRoleManager/' ${NODE_PATH}/conf/cassandra.yaml
 update_audit_yaml ${NODE_PATH}/conf/audit.yaml ${NODE_PATH}/logs/audit
done
