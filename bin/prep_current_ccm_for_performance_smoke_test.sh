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

if [[ ! -f ~/.ccm/CURRENT ]]; then
 echo "Unable to find an active ccm cluster"
 exit 2
fi

CCM_CLUSTER_NAME=`cat ~/.ccm/CURRENT`
echo "Preparing ${CCM_CLUSTER_NAME} for performance smoke test"

CLUSTER_PATH=~/.ccm/${CCM_CLUSTER_NAME}

for NODE_PATH in ${CLUSTER_PATH}/node*;
do
 sed -i 's/^authenticator:.*/authenticator: PasswordAuthenticator/' ${NODE_PATH}/conf/cassandra.yaml
 sed -i 's/^authorizer:.*/authorizer: CassandraAuthorizer/' ${NODE_PATH}/conf/cassandra.yaml
 sed -i 's/^role_manager:.*/role_manager: CassandraRoleManager/' ${NODE_PATH}/conf/cassandra.yaml
 sed -i 's/^roles_update_interval_in_ms:.*/roles_update_interval_in_ms: 2000/' ${NODE_PATH}/conf/cassandra.yaml
 sed -i 's/^roles_validity_in_ms:.*/roles_validity_in_ms: 10000/' ${NODE_PATH}/conf/cassandra.yaml

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
' ${NODE_PATH}/conf/logback.xml

done
