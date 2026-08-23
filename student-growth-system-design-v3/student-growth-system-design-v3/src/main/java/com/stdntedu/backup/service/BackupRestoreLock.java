package com.stdntedu.backup.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class BackupRestoreLock {
    private static final String NAME = "stdntedu-backup-restore-v1";
    private final DataSource dataSource;

    public BackupRestoreLock(DataSource dataSource) { this.dataSource = dataSource; }

    public boolean run(Runnable work) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT GET_LOCK('" + NAME + "',0)")) {
            if (!result.next() || result.getInt(1) != 1) return false;
            try { work.run(); }
            finally { try (Statement release = connection.createStatement()) {
                release.execute("SELECT RELEASE_LOCK('" + NAME + "')");
            } }
            return true;
        } catch (Exception ex) {
            throw new IllegalStateException("backup/restore database lock failed", ex);
        }
    }
}
