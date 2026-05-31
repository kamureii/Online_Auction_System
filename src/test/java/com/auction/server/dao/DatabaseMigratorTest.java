package com.auction.server.dao;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigratorTest {
    @Test
    void splitSqlStatementsKeepsQuotedSemicolonsAndPreparedSql() {
        String script = """
                -- leading comment
                USE online_auction;
                SET @sql := IF(@column_exists = 0,
                    'ALTER TABLE items ADD COLUMN note VARCHAR(80) DEFAULT ''semi;colon''',
                    'SELECT ''already exists'' AS message'
                );
                PREPARE stmt FROM @sql;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
                """;

        List<String> statements = DatabaseMigrator.splitSqlStatements(script);

        assertEquals(5, statements.size());
        assertEquals("USE online_auction", statements.get(0));
        assertTrue(statements.get(1).contains("semi;colon"));
        assertEquals("PREPARE stmt FROM @sql", statements.get(2));
    }

    @Test
    void splitSqlStatementsSkipsBlockCommentsAndBlankStatements() {
        String script = """
                /* migration note; with semicolon */
                ;
                CREATE TABLE IF NOT EXISTS demo_table (id INT);
                """;

        List<String> statements = DatabaseMigrator.splitSqlStatements(script);

        assertEquals(List.of("CREATE TABLE IF NOT EXISTS demo_table (id INT)"), statements);
    }
}
