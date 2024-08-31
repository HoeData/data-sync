package com.manniu.datasync.sqlite;

import com.manniu.datasync.doMain.SnapshotDo;
import lombok.extern.log4j.Log4j2;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
public class SqliteUtil {

    private static Integer sqliteSize = 50;


    //获取数据库连接
    public static Connection getConnection() throws SQLException {
        return  DriverManager.getConnection("jdbc:sqlite:data-sync.db");
    }

    static {
        //项目初始化后先创建表
        executeSQL("CREATE TABLE IF NOT EXISTS \"sync_snapshot\" (\"id\" real NOT NULL,\"TagFullName\" TEXT,\"Time\" text,\"Type\" integer,\"Value\" TEXT,\"Qualitie\" integer,PRIMARY KEY (\"id\"));");
    }

    //执行SQL
    public static void executeSQL(String sql){
        try(
                Connection connection = getConnection();
                Statement statement = connection.createStatement();
        ) {
            boolean execute = statement.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 一次拿几条
     * @return
     */
    public static List<SnapshotDo> getSnapshotDoList(){
        try(
                Connection connection = getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT * FROM sync_snapshot LIMIT "+sqliteSize);
        ) {
            List<SnapshotDo> result = new ArrayList<>();
            while(rs.next()){
                SnapshotDo snapshotDo = new SnapshotDo();
                snapshotDo.setId(rs.getString("id"));
                snapshotDo.setTagFullName(rs.getString("TagFullName"));
                snapshotDo.setTime(rs.getString("Time"));
                snapshotDo.setType(rs.getInt("Type"));
                snapshotDo.setQualitie(rs.getInt("Qualitie"));
                snapshotDo.setValue(rs.getString("Value"));
                result.add(snapshotDo);
            }
            return result;
        } catch (SQLException e) {
            log.error("批量新增数据报错，查询报错");
        }
        return new ArrayList<>();
    }
    public static void batchInsertSnapshotDoList(List<SnapshotDo> param){
        synchronized (SqliteUtil.class){
            if(param.size() == 0) return;
            try(
                    Connection connection = getConnection();
                    PreparedStatement ps = connection.prepareStatement("INSERT INTO 'sync_snapshot' ('id', 'TagFullName', 'Time', 'Type', 'Value', 'Qualitie') VALUES (?,?,?,?,?,?);");
            ){
                for(SnapshotDo snapshotDo : param){
                    ps.setString(1,snapshotDo.getId());
                    ps.setString(2,snapshotDo.getTagFullName());
                    ps.setString(3,snapshotDo.getTime());
                    ps.setInt(4,snapshotDo.getType());
                    ps.setString(5,snapshotDo.getValue());
                    ps.setInt(6,snapshotDo.getQualitie());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                log.error("批量新增数据报错,报错数量{}",param.size());
            }
        }
    }

    public static void deleteById(List<String> ids){
        synchronized (SqliteUtil.class){
            if(ids.size() == 0) return;
            String param = ids.stream().map(v -> "?").collect(Collectors.joining(","));
            try {
                Connection connection = getConnection();
                PreparedStatement ps = connection.prepareStatement("DELETE FROM sync_snapshot WHERE id IN ("+param+") ");
                for (int i = 0; i < ids.size(); i++) {
                    ps.setString(i+1,ids.get(i));
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                log.error("删除报错,报错数量{}",ids.size());
            }
        }
    }
}
