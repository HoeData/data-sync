package com.manniu.datasync.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manniu.datasync.dao.SyncFileDao;
import com.manniu.datasync.entity.SyncFile;
import com.manniu.datasync.service.SyncFileService;
import org.springframework.stereotype.Service;

/**
 * (SyncFile)表服务实现类
 *
 * @author makejava
 * @since 2024-08-21 15:49:22
 */
@Service("syncFileService")
public class SyncFileServiceImpl extends ServiceImpl<SyncFileDao, SyncFile> implements SyncFileService {

}

