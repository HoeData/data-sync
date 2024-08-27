package com.manniu.datasync.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manniu.datasync.dao.SyncFileDetailDao;
import com.manniu.datasync.entity.SyncFileDetail;
import com.manniu.datasync.service.SyncFileDetailService;
import org.springframework.stereotype.Service;

/**
 * (SyncFileDetail)表服务实现类
 *
 * @author makejava
 * @since 2024-08-21 15:49:22
 */
@Service("syncFileDetailService")
public class SyncFileDetailServiceImpl extends ServiceImpl<SyncFileDetailDao, SyncFileDetail> implements SyncFileDetailService {

}

