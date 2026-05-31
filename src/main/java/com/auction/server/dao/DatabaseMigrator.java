package com.auction.server.dao;

import com.auction.shared.config.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseMigrator {
    private static final String MIGRATION_PATH = "database/migrate.sql";

    private DatabaseMigrator() {
    }

    public static boolean isAutoMigrationEnabled() {
        return Boolean.parseBoolean(AppConfig.get(
                "auction.db.autoMigrate",
                "AUCTION_DB_AUTO_MIGRATE",
                "true"
        ));
    }

    public static int migrateIfEnabled() throws SQLException {
        if (!isAutoMigrationEnabled()) {
            System.out.println("Database auto-migration is disabled.");
            return 0;
        }
        String script = loadMigrationScript();
        List<String> statements = splitSqlStatements(script);
        if (statements.isEmpty()) {
            System.out.println("Database migration skipped: no statements found.");
            return 0;
        }

        int executed = 0;
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                try {
                    statement.execute(sql);
                    executed++;
                } catch (SQLException e) {
                    throw new SQLException("Database migration failed near: " + preview(sql), e);
                }
            }
        }
        System.out.println("Database migration completed. Statements executed: " + executed);
        return executed;
    }

    static List<String> splitSqlStatements(String script) {
        List<String> statements = new ArrayList<>();
        if (script == null || script.isBlank()) {
            return statements;
        }

        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';

            if (!inSingleQuote && !inDoubleQuote && !inBacktick) {
                if (c == '-' && next == '-' && startsSqlLineComment(script, i)) {
                    i = skipUntilNewline(script, i);
                    current.append('\n');
                    continue;
                }
                if (c == '/' && next == '*') {
                    i = skipBlockComment(script, i + 2);
                    current.append('\n');
                    continue;
                }
            }

            current.append(c);

            if (c == '\'' && !inDoubleQuote && !inBacktick) {
                if (inSingleQuote && next == '\'') {
                    current.append(next);
                    i++;
                } else {
                    inSingleQuote = !inSingleQuote;
                }
                continue;
            }
            if (c == '"' && !inSingleQuote && !inBacktick) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (c == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
                continue;
            }
            if (c == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
                addStatement(statements, current);
                current.setLength(0);
            }
        }
        addStatement(statements, current);
        return statements;
    }

    private static String loadMigrationScript() throws SQLException {
        Path localPath = findLocalMigrationPath();
        if (localPath != null) {
            try {
                return Files.readString(localPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new SQLException("Cannot read migration file: " + localPath, e);
            }
        }

        try (InputStream input = DatabaseMigrator.class.getResourceAsStream("/" + MIGRATION_PATH)) {
            if (input != null) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new SQLException("Cannot read migration resource: " + MIGRATION_PATH, e);
        }
        throw new SQLException("Migration file not found: " + MIGRATION_PATH);
    }

    private static Path findLocalMigrationPath() {
        Path cursor = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        while (cursor != null) {
            Path candidate = cursor.resolve(MIGRATION_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private static boolean startsSqlLineComment(String script, int index) {
        if (index > 0) {
            char before = script.charAt(index - 1);
            if (before != '\n' && before != '\r') {
                return false;
            }
        }
        int afterDash = index + 2;
        return afterDash >= script.length() || Character.isWhitespace(script.charAt(afterDash));
    }

    private static int skipUntilNewline(String script, int index) {
        int i = index;
        while (i < script.length() && script.charAt(i) != '\n') {
            i++;
        }
        return i;
    }

    private static int skipBlockComment(String script, int index) {
        int i = index;
        while (i + 1 < script.length()) {
            if (script.charAt(i) == '*' && script.charAt(i + 1) == '/') {
                return i + 1;
            }
            i++;
        }
        return script.length() - 1;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String sql = current.toString().trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (!sql.isBlank()) {
            statements.add(sql);
        }
    }

    private static String preview(String sql) {
        String compact = sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
        return compact.length() <= 160 ? compact : compact.substring(0, 157) + "...";
    }
}
