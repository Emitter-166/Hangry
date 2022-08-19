package org.example;

import java.sql.*;

public abstract class database {
    public static Connection connection;
    static {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:hangry.db");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
