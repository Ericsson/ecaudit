#!/bin/bash
shopt -s extglob

SCRIPT_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

if [ ! -f ~/.ccm/CURRENT ]; then
 echo "Unable to find an active ccm cluster"
 exit 2
fi

if [ ! -f ${SCRIPT_PATH}/../target/ecaudit*.jar ]; then
 echo "No jar file found. Build project and try again."
 exit 3
fi

CCM_CLUSTER_NAME=`cat ~/.ccm/CURRENT`
echo "Installing ecAudit into ${CCM_CLUSTER_NAME}"

CLUSTER_PATH=~/.ccm/${CCM_CLUSTER_NAME}

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
