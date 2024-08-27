package com.manniu.datasync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;

import java.io.Serializable;

/**
 * (SyncFile)表实体类
 *
 * @author makejava
 * @since 2024-08-21 15:49:22
 */
@SuppressWarnings("serial")
@Data
public class SyncFile  {

    @TableId(type = IdType.INPUT)
    private String fileId;
    //1发送方  2接收方
    private Integer platform;
    
    private String fileName;
    
    private String filePath;
    //时间戳
    private Long createTime;

    //0未发送/未接收  1已发送 2已接收
    private Integer status;

    }

