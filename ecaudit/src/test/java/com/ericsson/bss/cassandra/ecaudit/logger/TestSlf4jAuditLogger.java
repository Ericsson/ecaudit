/*
 * Copyright 2018 Telefonaktiebolaget LM Ericsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ericsson.bss.cassandra.ecaudit.logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.ericsson.bss.cassandra.ecaudit.common.record.AuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestSlf4jAuditLogger
{
    private static final String DEFAULT_LOG_FORMAT = "client:'${CLIENT_IP}'|user:'${USER}'{?|batchId:'${BATCH_ID}'?}|status:'${STATUS}'|operation:'${OPERATION}'";
    private static final String EXPECTED_STATEMENT = "insert into ks.tbl (key, val) values (?, ?)['kalle', 'anka']";
    private static final String EXPECTED_STATEMENT_NAKED = "insert into ks.tbl (key, val) values (?, ?)";
    private static final String EXPECTED_CLIENT_ADDRESS = "127.0.0.1";
    private static final Integer EXPECTED_CLIENT_PORT = 789;
    private static final String EXPECTED_COORDINATOR_ADDRESS = "127.0.0.2";
    private static final String EXPECTED_USER = "user";
    private static final Status EXPECTED_STATUS = Status.ATTEMPT;
    private static final UUID EXPECTED_BATCH_ID = UUID.fromString("12345678-aaaa-bbbb-cccc-123456789abc");
    private static final Long EXPECTED_TIMESTAMP = 42L;
    private static final String EXPECTED_SUBJECT = "the_subject";
    private static final String CUSTOM_LOGGER_NAME = "TEST_LOGGER";
    private static final Logger LOG = LoggerFactory.getLogger(CUSTOM_LOGGER_NAME);

    private static AuditEntry logEntryWithAll;
    private static AuditEntry logEntryWithoutBatch;
    private static AuditEntry logEntryWithoutClientPort;
    private static AuditEntry logEntryWithoutSubject;

    @Mock
    private Appender<ILoggingEvent> mockAuditAppender;
    @Captor
    private ArgumentCaptor<ILoggingEvent> loggingEventCaptor;

    @BeforeClass
    public static void setup()
    {
        InetAddress expectedCoordinatorAddress = mock(InetAddress.class);
        when(expectedCoordinatorAddress.getHostAddress()).thenReturn(EXPECTED_COORDINATOR_ADDRESS);
        AuditOperation auditOperation = mock(AuditOperation.class);
        when(auditOperation.getOperationString()).thenReturn(EXPECTED_STATEMENT);
        when(auditOperation.getNakedOperationString()).thenReturn(EXPECTED_STATEMENT_NAKED);
        logEntryWithAll = AuditEntry.newBuilder()
                                    .user(EXPECTED_USER)
                                    .client(new InetSocketAddress(EXPECTED_CLIENT_ADDRESS, EXPECTED_CLIENT_PORT))
                                    .coordinator(expectedCoordinatorAddress)
                                    .operation(auditOperation)
                                    .status(EXPECTED_STATUS)
                                    .timestamp(EXPECTED_TIMESTAMP)
                                    .batch(EXPECTED_BATCH_ID)
                                    .subject(EXPECTED_SUBJECT)
                                    .build();

        logEntryWithoutBatch = AuditEntry.newBuilder()
                                         .basedOn(logEntryWithAll)
                                         .batch(null)
                                         .build();

        logEntryWithoutClientPort = AuditEntry.newBuilder()
                                              .basedOn(logEntryWithAll)
                                              .client(new InetSocketAddress(EXPECTED_CLIENT_ADDRESS, 0))
                                              .build();

        logEntryWithoutSubject = AuditEntry.newBuilder()
                                         .basedOn(logEntryWithAll)
                                         .subject(null)
                                         .build();
    }

    @Before
    public void before()
    {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(CUSTOM_LOGGER_NAME).addAppender(mockAuditAppender);
    }

    @After
    public void after()
    {
        verifyNoMoreInteractions(mockAuditAppender);
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(CUSTOM_LOGGER_NAME).detachAppender(mockAuditAppender);
    }

    @Test
    public void testDefaultFormatAuditEntryNoBatch()
    {
        Slf4jAuditLogger logger = loggerWithConfig(DEFAULT_LOG_FORMAT);
        logger.log(logEntryWithoutBatch);

        assertThat(getSlf4jLogMessage())
        .isEqualTo("client:'127.0.0.1'|user:'user'|status:'ATTEMPT'|operation:'insert into ks.tbl (key, val) values (?, ?)['kalle', 'anka']'");
    }

    @Test
    public void testDefaultFormatAuditEntryWithAll()
    {
        Slf4jAuditLogger logger = loggerWithConfig(DEFAULT_LOG_FORMAT);
        logger.log(logEntryWithAll);

        assertThat(getSlf4jLogMessage())
        .isEqualTo("client:'127.0.0.1'|user:'user'|batchId:'12345678-aaaa-bbbb-cccc-123456789abc'|status:'ATTEMPT'|operation:'insert into ks.tbl (key, val) values (?, ?)['kalle', 'anka']'");
    }

    @Test
    public void testCustomFormatJsonWithDoubleQuotesEscaped()
    {
        String jsonFormat = "{\"timestamp\": \"${TIMESTAMP}\",\"operation\": \"${OPERATION}\",\"user\": \"${USER}\"}";
        Set<String> escapeChars = new HashSet<>();
        escapeChars.add("\"");
        Slf4jAuditLogger logger = loggerWithConfig(jsonFormat, escapeChars);
        AuditOperation auditOperation = mock(AuditOperation.class);
        when(auditOperation.getOperationString()).thenReturn("select somethingX from \"testKeyspace.table1\"");
        AuditEntry logEntry = AuditEntry.newBuilder()
                                        .user(EXPECTED_USER)
                                        .client(new InetSocketAddress(EXPECTED_CLIENT_ADDRESS, EXPECTED_CLIENT_PORT))
                                        .operation(auditOperation)
                                        .status(EXPECTED_STATUS)
                                        .timestamp(EXPECTED_TIMESTAMP)
                                        .batch(EXPECTED_BATCH_ID)
                                        .subject(EXPECTED_SUBJECT)
                                        .build();
        logger.log(logEntry);
        assertThat(getSlf4jLogMessage()).isEqualTo("{\"timestamp\": \""+EXPECTED_TIMESTAMP+"\"," +
                                                   "\"operation\": \"select somethingX from \\\"testKeyspace.table1\\\"\"," +
                                                   "\"user\": \""+EXPECTED_USER+"\"}");
    }

    @Test
    public void testCustomLogFormat()
    {
        Slf4jAuditLogger logger = loggerWithConfig("User = ${USER}, Status = {${STATUS}}, Query = ${OPERATION_NAKED}");
        logger.log(logEntryWithoutBatch);

        assertThat(getSlf4jLogMessage())
        .isEqualTo("User = user, Status = {ATTEMPT}, Query = insert into ks.tbl (key, val) values (?, ?)");
    }

    @Test
    public void testCustomLogFormatWithSubject()
    {
        Slf4jAuditLogger logger = loggerWithConfig("User = ${USER}, Status = {${STATUS}}, Subject = ${SUBJECT}");
        logger.log(logEntryWithAll);

        assertThat(getSlf4jLogMessage())
        .isEqualTo("User = user, Status = {ATTEMPT}, Subject = the_subject");
    }

    @Test
    public void testAnchorCharactersAreEscapedWhenUsedInLogFormat()
    {
        Slf4jAuditLogger logger = loggerWithConfig("{}User=${USER}{}Status=${STATUS}");
        logger.log(logEntryWithoutBatch);

        assertThat(getSlf4jLogMessage())
        .isEqualTo("{}User=user{}Status=ATTEMPT");
    }

    @Test
    public void testAvailableFieldFunctions()
    {
        Slf4jAuditLoggerConfig configMock = mock(Slf4jAuditLoggerConfig.class);
        Map<String, Function<AuditEntry, Object>> availableFieldFunctions = Slf4jAuditLogger.getAvailableFieldFunctionMap(configMock);
        assertThat(availableFieldFunctions).containsOnlyKeys("CLIENT_IP", "CLIENT_PORT", "COORDINATOR_IP", "USER", "BATCH_ID", "STATUS", "OPERATION", "OPERATION_NAKED", "TIMESTAMP", "SUBJECT");

        Function<AuditEntry, Object> clientFunction = availableFieldFunctions.get("CLIENT_IP");
        assertThat(clientFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_CLIENT_ADDRESS);

        Function<AuditEntry, Object> clientIpFunction = availableFieldFunctions.get("CLIENT_PORT");
        assertThat(clientIpFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_CLIENT_PORT);
        assertThat(clientIpFunction.apply(logEntryWithoutClientPort)).isEqualTo(null); // Client port is not guaranteed to be in the log entry

        Function<AuditEntry, Object> coordinatorFunction = availableFieldFunctions.get("COORDINATOR_IP");
        assertThat(coordinatorFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_COORDINATOR_ADDRESS);

        Function<AuditEntry, Object> userFunction = availableFieldFunctions.get("USER");
        assertThat(userFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_USER);

        Function<AuditEntry, Object> batchIdFunction = availableFieldFunctions.get("BATCH_ID");
        assertThat(batchIdFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_BATCH_ID);
        assertThat(batchIdFunction.apply(logEntryWithoutBatch)).isEqualTo(null); // Batch ID is not guaranteed to be in the log entry

        Function<AuditEntry, Object> statusFunction = availableFieldFunctions.get("STATUS");
        assertThat(statusFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_STATUS);

        Function<AuditEntry, Object> operationFunction = availableFieldFunctions.get("OPERATION");
        assertThat(operationFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_STATEMENT);

        Function<AuditEntry, Object> operationNakedFunction = availableFieldFunctions.get("OPERATION_NAKED");
        assertThat(operationNakedFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_STATEMENT_NAKED);

        Function<AuditEntry, Object> timestampFunction = availableFieldFunctions.get("TIMESTAMP");
        assertThat(timestampFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_TIMESTAMP);

        Function<AuditEntry, Object> subjectFunction = availableFieldFunctions.get("SUBJECT");
        assertThat(subjectFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_SUBJECT);
        assertThat(subjectFunction.apply(logEntryWithoutSubject)).isEqualTo(null); // Subject is not guaranteed to be in the log entry
    }

    @Test
    public void testTimestampFieldFunctionWithTimeFormatConfigured()
    {
        Slf4jAuditLoggerConfig configMock = mock(Slf4jAuditLoggerConfig.class);
        when(configMock.getTimeFormatter()).thenReturn(Optional.of(DateTimeFormatter.ISO_INSTANT));

        Function<AuditEntry, Object> timestampFunction = Slf4jAuditLogger.getTimeFunction(configMock);
        assertThat(timestampFunction.apply(logEntryWithAll)).isEqualTo("1970-01-01T00:00:00.042Z"); // 42 millis after EPOCH
    }

    @Test
    public void testInvalidConfig()
    {
        assertThatExceptionOfType(ConfigurationException.class)
        .isThrownBy(() -> loggerWithConfig("value=${INVALID}"))
        .withMessage("Unknown log format field: INVALID");
    }

    private Slf4jAuditLogger loggerWithConfig(String format)
    {
        return loggerWithConfig(format, new HashSet<>());
    }

    private Slf4jAuditLogger loggerWithConfig(String format, Set<String> escapeChars)
    {
        Slf4jAuditLoggerConfig mockConfig = mockAuditConfig(format);
        when(mockConfig.getEscapeCharacters()).thenReturn(escapeChars);
        return new Slf4jAuditLogger(mockConfig, LOG);
    }

    private Slf4jAuditLoggerConfig mockAuditConfig(String logFormat)
    {
        Slf4jAuditLoggerConfig mockConfig = mock(Slf4jAuditLoggerConfig.class);
        when(mockConfig.getLogFormat()).thenReturn(logFormat);
        return mockConfig;
    }

    private String getSlf4jLogMessage()
    {
        verify(mockAuditAppender, atLeast(1)).doAppend(loggingEventCaptor.capture());
        return loggingEventCaptor.getValue().getFormattedMessage();
    }
}
