package com.manniu.datasync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//@SpringBootTest
class DataSyncApplicationTests {

    @Test
    void contextLoads() {
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:jdbc:E:\\慢牛\\sqlite:data-sync.db");
            Map<String, Class<?>> typeMap = connection.getTypeMap();
            System.out.println("完成");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    @Test
    void zhengze(){
        String  param = "^T9998\\..+?$";
        Pattern r = Pattern.compile(param);
        Matcher m = r.matcher("T9998.user");
        System.out.println(m.matches());
    }

}
