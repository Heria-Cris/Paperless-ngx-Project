package com.paperless.local.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.paperless.local.entity.OperationLog;
import com.paperless.local.mapper.OperationLogMapper;
import com.paperless.local.service.OperationLogService;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {
}
