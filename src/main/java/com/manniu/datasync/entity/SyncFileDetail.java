package com.manniu.datasync.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;

import java.io.Serializable;

/**
 * (SyncFileDetail)表实体类
 *
 * @author makejava
 * @since 2024-08-21 15:49:22
 */
@SuppressWarnings("serial")
@Data
public class SyncFileDetail{

    @TableId(type = IdType.INPUT)
    private String fileDetailId;
    
    private String fileId;
    
    private String fileName;
    
    private String filePath;
    //时间戳
    private Long createTime;
    
    private Integer sort;

    //0 未发送 1已发送
    private Integer status;
    }

