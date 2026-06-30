package com.paperless.local.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.paperless.local.entity.OperationLog;
import com.paperless.local.mapper.OperationLogMapper;
import com.paperless.local.model.LoginUser;
import com.paperless.local.service.OperationLogService;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    public void record(LoginUser user, String operationType, String targetType, Long targetId, String ipAddress, String result) {
        OperationLog log = new OperationLog();
        if (user != null) {
            log.setUserId(user.id());
            log.setUsername(user.username());
        }
        log.setOperationType(operationType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIpAddress(ipAddress);
        log.setResult(result == null || result.isBlank() ? "SUCCESS" : result);
        log.setCreatedAt(LocalDateTime.now());
        save(log);
    }
}
