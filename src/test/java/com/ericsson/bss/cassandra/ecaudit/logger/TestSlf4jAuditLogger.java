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
import java.util.UUID;
import java.util.function.Function;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

import com.ericsson.bss.cassandra.ecaudit.entry.AuditEntry;
import com.ericsson.bss.cassandra.ecaudit.entry.SimpleAuditOperation;
import com.ericsson.bss.cassandra.ecaudit.entry.Status;
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
    private static final String DEFAULT_LOG_TEMPLATE = "client:'{}'|user:'{}'{}{}{}|status:'{}'|operation:'{}'";
    private static final String EXPECTED_STATEMENT = "select * from ks.tbl";
    private static final String EXPECTED_HOST_ADDRESS = "127.0.0.1";
    private static final String EXPECTED_USER = "user";
    private static final Status EXPECTED_STATUS = Status.ATTEMPT;
    private static final UUID EXPECTED_BATCH_ID = UUID.randomUUID();

    private static AuditEntry logEntryWithBatch;
    private static AuditEntry logEntryWithoutBatch;

    @Mock
    Logger mockLogger;
    @Captor
    ArgumentCaptor arg1;
    @Captor
    ArgumentCaptor arg2;
    @Captor
    ArgumentCaptor arg3;
    @Captor
    ArgumentCaptor arg4;
    @Captor
    ArgumentCaptor arg5;
    @Captor
    ArgumentCaptor arg6;
    @Captor
    ArgumentCaptor arg7;


    @BeforeClass
    public static void setup()
    {
        InetAddress expectedAddress = mock(InetAddress.class);
        when(expectedAddress.getHostAddress()).thenReturn(EXPECTED_HOST_ADDRESS);
        logEntryWithoutBatch = AuditEntry.newBuilder()
                                         .user(EXPECTED_USER)
                                         .client(expectedAddress)
                                         .operation(new SimpleAuditOperation(EXPECTED_STATEMENT))
                                         .status(EXPECTED_STATUS)
                                         .build();

        logEntryWithBatch = AuditEntry.newBuilder()
                                      .basedOn(logEntryWithoutBatch)
                                      .batch(EXPECTED_BATCH_ID)
                                      .build();
    }

    @Test
    public void testDefaultFormatAuditEntryNoBatch()
    {
        Slf4jAuditLogger logger = new Slf4jAuditLogger(mockLogger, null);
        logger.log(logEntryWithoutBatch);

        // Capture and perform validation
        verify(mockLogger, times(1)).info(eq(DEFAULT_LOG_TEMPLATE),
                                          arg1.capture(),
                                          arg2.capture(),
                                          arg3.capture(),
                                          arg4.capture(),
                                          arg5.capture(),
                                          arg6.capture(),
                                          arg7.capture());

        assertThat(arg1.getValue().toString()).isEqualTo(EXPECTED_HOST_ADDRESS);
        assertThat(arg2.getValue().toString()).isEqualTo(EXPECTED_USER);
        assertThat(arg3.getValue().toString()).isEqualTo(""); // Optional parameter - left part - empty
        assertThat(arg4.getValue().toString()).isEqualTo(""); // Optional parameter - value - empty
        assertThat(arg5.getValue().toString()).isEqualTo(""); // Optional parameter - right part - empty
        assertThat(arg6.getValue().toString()).isEqualTo(EXPECTED_STATUS.toString());
        assertThat(arg7.getValue().toString()).isEqualTo(EXPECTED_STATEMENT);
    }

    @Test
    public void testDefaultFormatAuditEntryWithBatch()
    {
        Slf4jAuditLogger logger = new Slf4jAuditLogger(mockLogger, null);
        logger.log(logEntryWithBatch);

        // Capture and perform validation
        verify(mockLogger, times(1)).info(eq(DEFAULT_LOG_TEMPLATE),
                                          arg1.capture(),
                                          arg2.capture(),
                                          arg3.capture(),
                                          arg4.capture(),
                                          arg5.capture(),
                                          arg6.capture(),
                                          arg7.capture());

        assertThat(arg1.getValue().toString()).isEqualTo(EXPECTED_HOST_ADDRESS);
        assertThat(arg2.getValue().toString()).isEqualTo(EXPECTED_USER);
        assertThat(arg3.getValue().toString()).isEqualTo("|batchId:'");                 // Optional parameter - left part
        assertThat(arg4.getValue().toString()).isEqualTo(EXPECTED_BATCH_ID.toString()); // Optional parameter - value
        assertThat(arg5.getValue().toString()).isEqualTo("'");                          // Optional parameter - right part
        assertThat(arg6.getValue().toString()).isEqualTo(EXPECTED_STATUS.toString());
        assertThat(arg7.getValue().toString()).isEqualTo(EXPECTED_STATEMENT);
    }

    @Test
    public void testCustomLogFormat()
    {
        Slf4jAuditLogger logger = new Slf4jAuditLogger(mockLogger, "User = ${USER}, Status = {${STATUS}}, Query = ${OPERATION}");
        logger.log(logEntryWithoutBatch);

        // Capture and perform validation
        verify(mockLogger, times(1)).info(eq("User = {}, Status = {{}}, Query = {}"),
                                          arg1.capture(),
                                          arg2.capture(),
                                          arg3.capture());

        assertThat(arg1.getValue().toString()).isEqualTo(EXPECTED_USER.toString());
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
    public void testAvailableParameterFunctions()
    {
        assertThat(Slf4jAuditLogger.AVAILABLE_PARAMETER_FUNCTIONS).containsOnlyKeys("CLIENT", "USER", "BATCH_ID", "STATUS", "OPERATION");

        Function<AuditEntry, Object> clientFunction = Slf4jAuditLogger.AVAILABLE_PARAMETER_FUNCTIONS.get("CLIENT");
        assertThat(clientFunction.apply(logEntryWithBatch)).isEqualTo(EXPECTED_HOST_ADDRESS);

        Function<AuditEntry, Object> userFunction = Slf4jAuditLogger.AVAILABLE_PARAMETER_FUNCTIONS.get("USER");
        assertThat(userFunction.apply(logEntryWithBatch)).isEqualTo(EXPECTED_USER);

        Function<AuditEntry, Object> batchIdFunction = Slf4jAuditLogger.AVAILABLE_PARAMETER_FUNCTIONS.get("BATCH_ID");
        assertThat(batchIdFunction.apply(logEntryWithBatch)).isEqualTo(EXPECTED_BATCH_ID);
        assertThat(batchIdFunction.apply(logEntryWithoutBatch)).isEqualTo(null); // Batch ID is not guaranteed to be in the log entry

        Function<AuditEntry, Object> statusFunction = Slf4jAuditLogger.AVAILABLE_PARAMETER_FUNCTIONS.get("STATUS");
        assertThat(statusFunction.apply(logEntryWithBatch)).isEqualTo(EXPECTED_STATUS);

        Function<AuditEntry, Object> operationFunction = Slf4jAuditLogger.AVAILABLE_PARAMETER_FUNCTIONS.get("OPERATION");
        assertThat(operationFunction.apply(logEntryWithBatch)).isEqualTo(EXPECTED_STATEMENT);
    }

    @Test
    public void testThatConfigurationExceptionIsThrownWhenParameterIsMissing()
    {
        assertThatThrownBy(() -> Slf4jAuditLogger.getParameterFunctions("Value = ${NON_EXISTING}"))
        .isInstanceOf(ConfigurationException.class).hasMessage("Unknown log format parameter: NON_EXISTING");
    }

    @Test
    public void testThatConfigurationExceptionIsThrownWhenOptionalParameterIsMissing()
    {
        assertThatThrownBy(() -> Slf4jAuditLogger.getParameterFunctions("{? optional ${NON_EXISTING2} ?}"))
        .isInstanceOf(ConfigurationException.class).hasMessage("Unknown log format parameter: NON_EXISTING2");
    }
}
