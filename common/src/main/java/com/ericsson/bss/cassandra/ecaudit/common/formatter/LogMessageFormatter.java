/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
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
package com.ericsson.bss.cassandra.ecaudit.common.formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Log message formatter that transforms parameterized log message into a formatting string and its corresponding arguments.
 * <p>
 * The format for a field is {@code ${<Field Name>}}. With a formatting string like this: {@code "${USER} executed '${OPERATION}' from ${CLIENT_IP}"}
 * and the anchor string {@code "{}"}. {@link #getLogTemplate()} will generated a log template like this:
 * <ul>
 * <li>"{} executed '{}' from {}"</li>
 * </ul>
 * {@link #getArgumentsForEntry(Object)} creates the arguments for a given log entry. The template and arguments together
 * will let a logger (e.g. Slf4J {@code Logger.info(template, arguments)}) create a log message like this:
 * <ul>
 * <li>"Duke executed 'select * from students;' from 1.2.3.4"</li>
 * </ul>
 * <p>
 * Conditional formatting of fields is also available, which makes it possible to only log the field value and
 * its descriptive text if a value exists. The formatting string {@code "user:${USER}, client:${CLIENT_IP}{?, executed from batch:${BATCH_ID}?}"}
 * will log different things depending on if there is a BATCH_ID or not:
 * <ul>
 * <li>"user:Duke, client:1.2.3.4, executed from batch:e501f872-9aab-4f6b-9a52-8ed2f67b1320"</li>
 * <li>"user:Duke, client:1.2.3.4"</li>
 * </ul>
 *
 * @param <T> The type of long entries the log message formatter should operate on.
 */
public class LogMessageFormatter<T>
{
    private static final String FIELD_EXP = "\\$\\{(.*?)}"; // Non-greedy matching of field
    private static final String OPTIONAL_FIELD_EXP = "\\{\\?(.*?)\\$\\{(.*?)}(.*?)\\?}";
    private static final String COMBINED_FIELDS_EXP = FIELD_EXP + '|' + OPTIONAL_FIELD_EXP;
    private static final Pattern FIELD_PATTERN = Pattern.compile(COMBINED_FIELDS_EXP);

    private final String logTemplate;
    private final List<Function<T, String>> configuredFieldFunctions;

    private LogMessageFormatter(Builder<T> builder)
    {
        logTemplate = getTemplateFromFormatString(builder);
        configuredFieldFunctions = getConfiguredFieldFunctions(builder);
    }

    private static String getTemplateFromFormatString(Builder builder)
    {
        return builder.format.replaceAll(builder.escapeExpr, builder.escapeWith)
                             .replaceAll(COMBINED_FIELDS_EXP, builder.anchor);
    }

    private static <T> List<Function<T, String>> getConfiguredFieldFunctions(Builder<T> builder)
    {
        List<Function<T, String>> fieldFunctions = new ArrayList<>();
        Matcher matcher = FIELD_PATTERN.matcher(builder.format);
        while (matcher.find())
        {
            String normalField = matcher.group(1);
            if (normalField != null)
            {
                Function<T, Object> fieldFunction = getFieldFunctionOrThrow(normalField, builder);
                fieldFunctions.add(fieldFunction.andThen(String::valueOf));
            }
            else // Optional field
            {
                String descriptionLeft = matcher.group(2);
                String optionalField = matcher.group(3);
                String descriptionRight = matcher.group(4);
                Function<T, Object> fieldFunction = getFieldFunctionOrThrow(optionalField, builder);
                fieldFunctions.add(fieldFunction.andThen(getOptionalDescriptionAndValueIfPresent(descriptionLeft, descriptionRight)));
            }
        }
        return fieldFunctions;
    }

    static <T> Function<T, Object> getFieldFunctionOrThrow(String field, Builder<T> builder)
    {
        if (!builder.availableFields.containsKey(field))
        {
            throw new IllegalArgumentException("Unknown log format field: " + field);
        }
        return builder.availableFields.get(field);
    }

    private static Function<Object, String> getOptionalDescriptionAndValueIfPresent(String descriptionLeft, String descriptionRight)
    {
        return value -> value == null ? "" : descriptionLeft + value + descriptionRight;
    }

    public String getLogTemplate()
    {
        return logTemplate;
    }

    public Object[] getArgumentsForEntry(T logEntry)
    {
        return configuredFieldFunctions.stream()
                                       .map(valueSupplier -> valueSupplier.apply(logEntry))
                                       .toArray(Object[]::new);
    }

    public static <T> Builder<T> builder()
    {
        return new Builder<>();
    }

    public static class Builder<T>
    {
        private String format;
        private String anchor;
        private String escapeExpr;
        private String escapeWith;
        private Map<String, Function<T, Object>> availableFields;

        public LogMessageFormatter<T> build()
        {
            return new LogMessageFormatter<>(this);
        }

        public Builder<T> format(String format)
        {
            this.format = format;
            return this;
        }

        public Builder<T> anchor(String anchor)
        {
            this.anchor = anchor;
            return this;
        }

        public Builder<T> escape(String escapeExpr, String escapeWith)
        {
            this.escapeExpr = escapeExpr;
            this.escapeWith = escapeWith;
            return this;
        }

        public Builder<T> availableFields(Map<String, Function<T, Object>> availableFields)
        {
            this.availableFields = availableFields;
            return this;
        }
    }
}
