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
echo "Installing ecAudit with SLF4J backend into ${CCM_CLUSTER_NAME}"

CLUSTER_PATH=${CCM_CONFIG}/${CCM_CLUSTER_NAME}

mkdir -p ${CLUSTER_PATH}/lib
rm -f ${CLUSTER_PATH}/lib/ecaudit.jar
ln -s ${JAR_FILE} ${CLUSTER_PATH}/lib/ecaudit.jar

grep -sq ecaudit.jar ${CLUSTER_PATH}/cassandra.in.sh
if [ $? -ne 0 ]; then
 echo "CLASSPATH=\"\$CLASSPATH:${CLUSTER_PATH}/lib/ecaudit.jar\"" >> ${CLUSTER_PATH}/cassandra.in.sh
 echo "JVM_EXTRA_OPTS=\"\$JVM_EXTRA_OPTS -Dcassandra.custom_query_handler_class=com.ericsson.bss.cassandra.ecaudit.handler.AuditQueryHandler\"" >> ${CLUSTER_PATH}/cassandra.in.sh
fi

update_cache_times() {
 sed -i "s/^$1_validity_in_ms:.*/$1_validity_in_ms: 10000/" $2
 sed -i "/^$1_update_interval_in_ms/d" $2
 sed -i "/^$1_validity_in_ms:.*/a\
$1_update_interval_in_ms: 2000" $2
}

update_audit_yaml() {
 rm -f $1
 cat <<EOF > $1
logger_backend:
  - class_name: com.ericsson.bss.cassandra.ecaudit.logger.Slf4jAuditLogger

whitelist_cache_validity_in_ms: 10000
whitelist_cache_update_interval_in_ms: 2000
EOF
}

update_logback_config() {
 grep -sq ECAUDIT $1
 if [ $? -eq 0 ]; then
   return
 fi

 sed -i '/<\/configuration>/i\
<!--audit log-->\
<appender name="AUDIT-FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">\
  <file>${cassandra.logdir}/audit/audit.log</file>\
  <encoder>\
    <pattern>%d{HH:mm:ss.SSS} - %msg%n</pattern>\
    <immediateFlush>true</immediateFlush>\
  </encoder>\
  <rollingPolicy class="ch.qos.logback.core.rolling.FixedWindowRollingPolicy">\
    <fileNamePattern>${cassandra.logdir}/audit/audit.log.%i</fileNamePattern>\
    <minIndex>1</minIndex>\
    <maxIndex>5</maxIndex>\
  </rollingPolicy>\
  <triggeringPolicy class="ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy">\
    <maxFileSize>200MB</maxFileSize>\
  </triggeringPolicy>\
</appender>\
\
<logger name="ECAUDIT" level="INFO" additivity="false">\
  <appender-ref ref="AUDIT-FILE" />\
</logger>\
' $1
}

for NODE_PATH in ${CLUSTER_PATH}/node*;
do
 sed -i 's/^authenticator:.*/authenticator: com.ericsson.bss.cassandra.ecaudit.auth.AuditAuthenticator/' ${NODE_PATH}/conf/cassandra.yaml
 sed -i 's/^authorizer:.*/authorizer: com.ericsson.bss.cassandra.ecaudit.auth.AuditAuthorizer/' ${NODE_PATH}/conf/cassandra.yaml
 sed -i 's/^role_manager:.*/role_manager: com.ericsson.bss.cassandra.ecaudit.auth.AuditRoleManager/' ${NODE_PATH}/conf/cassandra.yaml
 update_cache_times roles ${NODE_PATH}/conf/cassandra.yaml
 update_cache_times permissions ${NODE_PATH}/conf/cassandra.yaml
 #update_cache_times credentials ${NODE_PATH}/conf/cassandra.yaml
 update_audit_yaml ${NODE_PATH}/conf/audit.yaml
 update_logback_config ${NODE_PATH}/conf/logback.xml
done
