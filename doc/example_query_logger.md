# Query Logger Example

This short guide will demonstrate how to setup ecAudit as a pure Query Logger in combination with the [Chronicle Logger](chronicle_logger.md) backend.
In this example all CQL queries will be logged without any filtering, and users will be able to connect without any credentials.

## cassandra.yaml

Change the following setting in your ```cassandra.yaml```.

```
role_manager: com.ericsson.bss.cassandra.ecaudit.auth.AuditRoleManager
```

Leave the ```authenticator``` and ```authorizer``` settings at their default.

```
authenticator: AllowAllAuthenticator
authorizer: AllowAllAuthorizer
```


## cassandra-env.sh

Add the following JVM option to the ```cassandra-env.sh``` or the ```cassandra.in.sh```.

```
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Dcassandra.custom_query_handler_class=com.ericsson.bss.cassandra.ecaudit.handler.AuditQueryHandler"
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Decaudit.filter_type=NONE"
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -da:net.openhft..."
```

**Note:** If these settings configured in the ```cassandra-env.sh```,
consider that the ```JVM_EXTRA_OPTS``` variable is consumed at the end of the file,
so make sure to add the lines *before* they are consumed.

In other words, the last lines of the ```cassandra-env.sh``` should look something like this:

```
...
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Dcassandra.custom_query_handler_class=com.ericsson.bss.cassandra.ecaudit.handler.AuditQueryHandler"
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -Decaudit.filter_type=NONE"
JVM_EXTRA_OPTS="$JVM_EXTRA_OPTS -da:net.openhft..."
JVM_OPTS="$JVM_OPTS $JVM_EXTRA_OPTS"
```


## audit.yaml

Setup the ```audit.yalm``` as follows to roll a new Chronicle log file every minute,
and store only fields which are relevant in this example.
Also, use ```post_logging``` to make sure there is one audit record per CQL request.

```
log_timing_strategy: post_logging

logger_backend:
    - class_name: com.ericsson.bss.cassandra.ecaudit.logger.ChronicleAuditLogger
      parameters:
      - log_dir: /var/lib/cassandra/audit
        roll_cycle: MINUTELY
        fields: TIMESTAMP, OPERATION, BATCH_ID, STATUS
```

## eclog

The query logs contain binary data since they are written with the Chronicle logger backend.

The ```eclog``` tool will print the content in a human readable format.
With a ```eclog.yaml``` file it is possible to get a custom format of each record.
For example.

```
log_format: "${TIMESTAMP},{?${BATCH_ID}?},${STATUS},${OPERATION}"
time_format: "yyyy-MM-dd HH:mm:ss z"
```

Here's an example on how that could look:
```
# java -jar eclog.jar -c eclog.yaml -l 10 /var/lib/cassandra/audit/
2020-03-25 13:09:00 CET,,SUCCEEDED,UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?[0xb8e05320cd1037d6e317cce868ca7f9e679cf764169e9d0b3f5b2c0ffc3cb50dd5c4, 0x440b1bdefbc9932ec91daa7fbe985e951fd425c9ab069c4d9a398b1444984097f7c1, 0x3c679832c20720548015925f1681ce1991e508bba683a31711485c5ff09c2ff91679, 0x96284cbd07ac5aa9eeec4b298c34c20f4e7d542e009519031175e5dc26ad1e8ce24e, 0x051946dc6fe26d78722b5ca7580288a94bd77f86b4b4b14394281619e0d3deb24c9f, 0x4f384c4b37394c4f3631]
2020-03-25 13:09:00 CET,,SUCCEEDED,UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?[0x008a83c79b8c9994c3b52cefe5bf1bfaceb1daad442110334d86df65c03f71b99acb, 0x8b48e2330bc5f75b5d2de0bedb4a208ea37a98bde25c6ccb94668b8690ff6f38dada, 0x4886d54aa9493f1930f868b75b0edfa84858c5c255dacb4e50ed62d6a96bcf44f104, 0x164dc0ea51ba165748a0dc11c4a6606a1cfa603e3da5e07d22b34c7cb8dad41dd97f, 0xcb15c5d21a848b5e15828d9efc2ec52ad48eba3928c7ff4ad3f610185017c8f8f20b, 0x50364d4e4d3850394f30]
2020-03-25 13:09:00 CET,,SUCCEEDED,UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?[0x3ab9b990a5dbbe3dbf0354d35bc32122ab09f963a9a29b04434fac1ab16af8992ed0, 0xdd9f9d6c207f3720d01cc2f9ab4e83a5c59226c779de8cf79cfe8f1a6c5a092fbee4, 0x9116a0bb80af7f73d803ceeb78f8aada9555e78936b26678202f6fb1f4993df92089, 0xbde6fe3df138b0428bcec8ad4ab0dcf256b5bd57c2b6ac42523913522d78f710ee98, 0x63f1cac68e33a7375a8370fbf300dcc70f76052c3ce84588f3b7d69ef6bc470db986, 0x36384b30374f33314c30]
2020-03-25 13:09:00 CET,,SUCCEEDED,UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?[0x10f1dd6febff8cbcf3ad91b9f9c1aab0231188a482f678c5f329ee8c9ecbf8e89d73, 0x8aa88581e34267312170391cc1edbffc6e09cbf499fb30ca3a862bf0058578e19541, 0xcb5ba7b74ab80826b452106821cbe5117bb80ffbe35f1b97b82c6c43ad6aee55299e, 0x37fdf1c2ac34981234ea6ef18b116aac66158a31932d710c4c079860f8468f8cfe04, 0xcf830798d1b749d799423c14f208ab22e162195702f6c89c47fc0c16ea340558fd7b, 0x394e35344e4b34383631]
2020-03-25 13:09:00 CET,,SUCCEEDED,UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?[0x5ff08459ccd892a25f9189377eeee2a4f03827ef081b0c5909420a7ed9ad649929ad, 0x208ee47f58287238307277e66e81862e33bdd2e524afcf51eeb27516bb4a84cc5969, 0x3c4cb4956a93859adf04c0039d5c2ee0572d2504a8ba50d9f5dbae549910a9de5959, 0xdbf122dca6ed0e644b3691ea3ece67b48de7867e5b866efbada244e58ccba22c52ca, 0x609a5636d027d4e27cad7c3da7587e9cb197028a4408e3ac4de1e3ad11e0f29e9e9a, 0x4f503030314c35393330]
2020-03-25 13:09:00 CET,,SUCCEEDED,UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?[0xdc9e1bf05eab897f470a0777e3e3832852175016c58597038029ad7e2bc54d24086a, 0xcc4010fda2b4d80fbdb900134d27bf287d97df31875b848934d875a7ba248860a36f, 0xd82dda78d34b0d1e4d1b54c8140213b390476eee11e59899d74576bb468b544647a3, 0xbf1723eb11b1acdf9e97a86b0c2fb7fb75632f353c77b0ee521f37b37605d1d55d6d, 0x7cd7e4548d69eba1b5947858a8139da29447e41f02fc09741bd21a5089f04ec4b042, 0x37373539364d4f323330]
2020-03-25 13:09:00 CET,,SUCCEEDED,UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?[0x85182fdfe7f86306cd58ce81aa53cca24c193841d6ae8a91646a4c304dd9f47892f9, 0x1f5c9f8c30a62ce4815e936de814514ea33ed4d85fa33f90222d58a5a0a49b38d2fe, 0x225510932bd8812b4610817653b79c86552c99aefb177f4cefa4850a9139db696e6e, 0xd7e099a59da559e039600f6e2c0ede94bada8cfe5d8d4ff26d68ff597d3b3f68f2b4, 0xfbdd9cec024fcbde438399670475aa8900d80e0ed3d0c3312d9d4ae79241214d74c3, 0x30503337373039503231]
2020-03-25 13:09:00 CET,,SUCCEEDED,UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?[0x5c7310ecff5c4121973da9cacfdd5022dd27b9047bed9c1880fa881a91d4275ad020, 0xfa28e70b2dacef5f8bed360e7317018e789944fb8bc288224867d11d87463a3751ba, 0x78c72240e3bef9f89d0cd59e5b9c87cb2b323a0e19090b3495cbd0ba6703717560af, 0x6508112e2894adc8f4525fff28988061094b3f06570693ea36b611c4f78cfed0720e, 0xf5b3bd9baa993daac0b25ea274d6669eae349fc89ec0c6b761caa0b0a01e2bc45742, 0x374f324e363531323730]
2020-03-25 13:09:00 CET,,SUCCEEDED,UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?[0x3f0debec2d8107c992436dd8a3cb67ed21f4334b3b7b2c14b1dac6a59fa5870f75ab, 0x2835fb5492819b1a97cfbd478983ca4ca188a6e4c4aec74d7d6734e2a43f907a70cd, 0xc902e1eb11298a5b5e5c0d3bc5b5b2661091bc2b7f489625b7e5bcb847a1473aa5f2, 0xed2eae78d83e17a2a824e14b99b4644150529790cbbbad3ce65a2d91d93d8246175e, 0x554a0b119dedf8cd043c23e0a4c0d90ebc4aa01dc173d75758902c50706f46f86edd, 0x313637364d364b4c3630]
2020-03-25 13:09:00 CET,,SUCCEEDED,UPDATE "standard1" SET "C0" = ?,"C1" = ?,"C2" = ?,"C3" = ?,"C4" = ? WHERE KEY=?[0xde7618ecf0e1cf11a43b9dbbb5fd7d8f4f4613a599ddf3495e8a2895de814dec3f28, 0x85580d850d89384bb20d251695c02dd926c0388731c0c96fabcf775d65c71d13427b, 0x5fea2011446e9ef66a25d89bea6123a4e9103c1f93ed059b230b482c4a4c087ca62f, 0x8447a04fdb26ff71088c8fcc25bc633c5fa86b9da879afdf141c517be32675d89c9b, 0xc61be8e74cb57a35c0626e1f48043ef53d06f5ed71288a213142c15372ee0e2c078a, 0x314e344b4d504d4b4b30]
```
