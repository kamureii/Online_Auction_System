package com.auction.shared.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class AppConfig {
    private static final Map<String, String> LOCAL_VALUES = loadLocalValues();

    private AppConfig() {}

    public static String get(String propertyName, String envName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (hasText(propertyValue)) {
            return propertyValue.trim();
        }

        String envValue = System.getenv(envName);
        if (hasText(envValue)) {
            return envValue.trim();
        }

        String localValue = LOCAL_VALUES.get(envName);
        if (hasText(localValue)) {
            return localValue.trim();
        }

        localValue = LOCAL_VALUES.get(propertyName);
        if (hasText(localValue)) {
            return localValue.trim();
        }

        return defaultValue == null ? "" : defaultValue.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Map<String, String> loadLocalValues() {
        Map<String, String> values = new HashMap<>();
        Path root = findProjectRoot();
        loadDotEnv(root.resolve(".env"), values);
        loadProperties(root.resolve("config").resolve("application.properties"), values);
        loadProperties(root.resolve("application.properties"), values);
        return values;
    }

    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path cursor = current;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml")) || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return current;
    }

    private static void loadDotEnv(Path path, Map<String, String> values) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator).trim();
                String value = unquote(line.substring(separator + 1).trim());
                if (!key.isEmpty() && hasText(value)) {
                    values.putIfAbsent(key, value);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void loadProperties(Path path, Map<String, String> values) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            properties.stringPropertyNames().forEach(key -> {
                String value = properties.getProperty(key);
                if (hasText(value)) {
                    values.putIfAbsent(key, value.trim());
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
