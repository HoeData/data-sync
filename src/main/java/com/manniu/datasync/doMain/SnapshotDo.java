package com.manniu.datasync.doMain;

import lombok.Data;


/**
 * get 写入中台的值的对象
 */
@Data
public class SnapshotDo {
    private String Id;
    private String TagFullName;
    // "2021/5/11 14:34:08" 此格式
    private String Time;
    private Integer Type;
    private String Value;
    private Integer Qualitie = 0;
}
