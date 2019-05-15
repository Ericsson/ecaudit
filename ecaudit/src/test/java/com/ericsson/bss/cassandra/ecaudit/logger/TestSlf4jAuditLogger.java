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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.common.record.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.common.record.Status;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class TestSlf4jAuditLogger
{
    private static final String DEFAULT_LOG_FORMAT = "client:'${CLIENT_IP}'|user:'${USER}'{?|batchId:'${BATCH_ID}'?}|status:'${STATUS}'|operation:'${OPERATION}'";
    private static final String DEFAULT_LOG_TEMPLATE = "client:'{}'|user:'{}'{}{}{}|status:'{}'|operation:'{}'";
    private static final String EXPECTED_STATEMENT = "select * from ks.tbl";
    private static final String EXPECTED_CLIENT_ADDRESS = "127.0.0.1";
    private static final Integer EXPECTED_CLIENT_PORT = 789;
    private static final String EXPECTED_COORDINATOR_ADDRESS = "127.0.0.2";
    private static final String EXPECTED_USER = "user";
    private static final Status EXPECTED_STATUS = Status.ATTEMPT;
    private static final UUID EXPECTED_BATCH_ID = UUID.randomUUID();
    private static final Long EXPECTED_TIMESTAMP = 42L;


    private static AuditEntry logEntryWithAll;
    private static AuditEntry logEntryWithoutBatch;
    private static AuditEntry logEntryWithoutClientPort;

    @Mock
    private Logger loggerMock;
    @Captor
    private ArgumentCaptor arg1;
    @Captor
    private ArgumentCaptor arg2;
    @Captor
    private ArgumentCaptor arg3;
    @Captor
    private ArgumentCaptor arg4;
    @Captor
    private ArgumentCaptor arg5;
    @Captor
    private ArgumentCaptor arg6;
    @Captor
    private ArgumentCaptor arg7;


    @BeforeClass
    public static void setup()
    {
        InetAddress expectedCoordinatorAddress = mock(InetAddress.class);
        when(expectedCoordinatorAddress.getHostAddress()).thenReturn(EXPECTED_COORDINATOR_ADDRESS);
        logEntryWithAll = AuditEntry.newBuilder()
                                    .user(EXPECTED_USER)
                                    .client(new InetSocketAddress(EXPECTED_CLIENT_ADDRESS, EXPECTED_CLIENT_PORT))
                                    .coordinator(expectedCoordinatorAddress)
                                    .operation(new SimpleAuditOperation(EXPECTED_STATEMENT))
                                    .status(EXPECTED_STATUS)
                                    .timestamp(EXPECTED_TIMESTAMP)
                                    .batch(EXPECTED_BATCH_ID)
                                    .build();

        logEntryWithoutBatch = AuditEntry.newBuilder()
                                         .basedOn(logEntryWithAll)
                                         .batch(null)
                                         .build();

        logEntryWithoutClientPort = AuditEntry.newBuilder()
                                              .basedOn(logEntryWithAll)
                                              .client(new InetSocketAddress(EXPECTED_CLIENT_ADDRESS, 0))
                                              .build();
    }

    @Test
    public void testDefaultFormatAuditEntryNoBatch()
    {
        Slf4jAuditLogger logger = loggerWithConfig(DEFAULT_LOG_FORMAT);
        logger.log(logEntryWithoutBatch);

        // Capture and perform validation
        verify(loggerMock, times(1)).info(eq(DEFAULT_LOG_TEMPLATE),
                                          arg1.capture(),
                                          arg2.capture(),
                                          arg3.capture(),
                                          arg4.capture(),
                                          arg5.capture(),
                                          arg6.capture(),
                                          arg7.capture());

        assertThat(arg1.getValue().toString()).isEqualTo(EXPECTED_CLIENT_ADDRESS);
        assertThat(arg2.getValue().toString()).isEqualTo(EXPECTED_USER);
        assertThat(arg3.getValue().toString()).isEqualTo(""); // Optional field - left part - empty
        assertThat(arg4.getValue().toString()).isEqualTo(""); // Optional field - value - empty
        assertThat(arg5.getValue().toString()).isEqualTo(""); // Optional field - right part - empty
        assertThat(arg6.getValue().toString()).isEqualTo(EXPECTED_STATUS.toString());
        assertThat(arg7.getValue().toString()).isEqualTo(EXPECTED_STATEMENT);
    }

    @Test
    public void testDefaultFormatAuditEntryWithAll()
    {
        Slf4jAuditLogger logger = loggerWithConfig(DEFAULT_LOG_FORMAT);
        logger.log(logEntryWithAll);

        // Capture and perform validation
        verify(loggerMock, times(1)).info(eq(DEFAULT_LOG_TEMPLATE),
                                          arg1.capture(),
                                          arg2.capture(),
                                          arg3.capture(),
                                          arg4.capture(),
                                          arg5.capture(),
                                          arg6.capture(),
                                          arg7.capture());

        assertThat(arg1.getValue().toString()).isEqualTo(EXPECTED_CLIENT_ADDRESS);
        assertThat(arg2.getValue().toString()).isEqualTo(EXPECTED_USER);
        assertThat(arg3.getValue().toString()).isEqualTo("|batchId:'");                 // Optional field - left part
        assertThat(arg4.getValue().toString()).isEqualTo(EXPECTED_BATCH_ID.toString()); // Optional field - value
        assertThat(arg5.getValue().toString()).isEqualTo("'");                          // Optional field - right part
        assertThat(arg6.getValue().toString()).isEqualTo(EXPECTED_STATUS.toString());
        assertThat(arg7.getValue().toString()).isEqualTo(EXPECTED_STATEMENT);
    }

    @Test
    public void testCustomLogFormat()
    {
        Slf4jAuditLogger logger = loggerWithConfig("User = ${USER}, Status = {${STATUS}}, Query = ${OPERATION}");
        logger.log(logEntryWithoutBatch);

        // Capture and perform validation
        verify(loggerMock, times(1)).info(eq("User = {}, Status = {{}}, Query = {}"),
                                          arg1.capture(),
                                          arg2.capture(),
                                          arg3.capture());

        assertThat(arg1.getValue().toString()).isEqualTo(EXPECTED_USER);
        assertThat(arg2.getValue().toString()).isEqualTo(EXPECTED_STATUS.toString());
        assertThat(arg3.getValue().toString()).isEqualTo(EXPECTED_STATEMENT);
    }

    @Test
    public void testGetDescriptionIfValuePresentWithoutValue()
    {
        Function<Object, String> f = Slf4jAuditLogger.getDescriptionIfValuePresent("Hello");
        assertThat(f.apply(null)).isEmpty();
    }

    @Test
    public void testGetDescriptionIfValuePresentWithValue()
    {
        Function<Object, String> f = Slf4jAuditLogger.getDescriptionIfValuePresent("Hello");
        assertThat(f.apply(new Object())).isEqualTo("Hello");
    }

    @Test
    public void testGetValueOrEmptyStringWithoutValue()
    {
        assertThat(Slf4jAuditLogger.getValueOrEmptyString(null)).isEqualTo("");
    }

    @Test
    public void testGetValueOrEmptyStringWithValue()
    {
        Object value = new Object();
        assertThat(Slf4jAuditLogger.getValueOrEmptyString(value)).isSameAs(value);
    }

    @Test
    public void testAvailableFieldFunctions()
    {
        Slf4jAuditLoggerConfig configMock = mock(Slf4jAuditLoggerConfig.class);
        Map<String, Function<AuditEntry, Object>> availableFieldFunctions = Slf4jAuditLogger.getAvailableFieldFunctionMap(configMock);
        assertThat(availableFieldFunctions).containsOnlyKeys("CLIENT_IP", "CLIENT_PORT", "COORDINATOR_IP", "USER", "BATCH_ID", "STATUS", "OPERATION", "TIMESTAMP");

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

        Function<AuditEntry, Object> timestampFunction = availableFieldFunctions.get("TIMESTAMP");
        assertThat(timestampFunction.apply(logEntryWithAll)).isEqualTo(EXPECTED_TIMESTAMP);
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
    public void testThatConfigurationExceptionIsThrownWhenFieldIsMissing()
    {
        Slf4jAuditLogger slf4jAuditLogger = loggerWithConfig("");
        assertThatThrownBy(() -> slf4jAuditLogger.getFieldFunctions("Value = ${NON_EXISTING}"))
        .isInstanceOf(ConfigurationException.class).hasMessage("Unknown log format field: NON_EXISTING");
    }

    @Test
    public void testThatConfigurationExceptionIsThrownWhenOptionalFieldIsMissing()
    {
        Slf4jAuditLogger slf4jAuditLogger = loggerWithConfig("");
        assertThatThrownBy(() -> slf4jAuditLogger.getFieldFunctions("{? optional ${NON_EXISTING2} ?}"))
        .isInstanceOf(ConfigurationException.class).hasMessage("Unknown log format field: NON_EXISTING2");
    }

    private Slf4jAuditLogger loggerWithConfig(String format)
    {
        Slf4jAuditLoggerConfig mockConfig = mockAuditConfig(format);
        return new Slf4jAuditLogger(mockConfig, loggerMock);
    }

    private Slf4jAuditLoggerConfig mockAuditConfig(String logFormat)
    {
        Slf4jAuditLoggerConfig mockConfig = mock(Slf4jAuditLoggerConfig.class);
        when(mockConfig.getLogFormat()).thenReturn(logFormat);
        return mockConfig;
    }
}
