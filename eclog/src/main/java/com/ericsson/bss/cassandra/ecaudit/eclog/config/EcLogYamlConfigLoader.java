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
package com.ericsson.bss.cassandra.ecaudit.eclog.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.ericsson.bss.cassandra.ecaudit.eclog.ToolOptions;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class EcLogYamlConfigLoader
{
    private static final EcLogYamlConfig DEFAULT_CONFIG = new EcLogYamlConfig();
    private static final String FILE_NAME = "eclog.yaml";

    private EcLogYamlConfigLoader()
    {
        // static class
    }

    public static EcLogYamlConfig load(ToolOptions options)
    {
        return getConfigFileLocations(options).stream()
                                              .filter(Files::isReadable)
                                              .map(EcLogYamlConfigLoader::loadConfigFromUrl)
                                              .findFirst()
                                              .orElse(DEFAULT_CONFIG); // No config file - use default
    }

    static List<Path> getConfigFileLocations(ToolOptions options)
    {
        List<Path> paths = new ArrayList<>();
        options.config().ifPresent(paths::add); // First - (optionally) specified at command line
        paths.add(Paths.get(System.getProperty("user.dir"), FILE_NAME)); // Second - working directory
        paths.add(options.path().resolve(FILE_NAME)); // Third - same directory as chronicle files
        return paths;
    }

    static EcLogYamlConfig loadConfigFromUrl(Path filePath)
    {
        SafeConstructor constructor = new CustomClassLoaderConstructor(EcLogYamlConfig.class, Thread.currentThread().getContextClassLoader());
        Yaml yaml = new Yaml(constructor);
        try
        {
            String fileAsString = new String(Files.readAllBytes(filePath), UTF_8);
            EcLogYamlConfig auditYamlConfig = (EcLogYamlConfig) yaml.load(fileAsString);
            return auditYamlConfig == null
                   ? new EcLogYamlConfig() // File is valid but empty
                   : auditYamlConfig;
        }
        catch (YAMLException | IOException e)
        {
            throw new IllegalArgumentException("Invalid configuration file: " + filePath, e);
        }
    }
}
