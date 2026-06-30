package com.paperless.local.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.paperless.local.entity.OperationLog;
import com.paperless.local.model.LoginUser;

public interface OperationLogService extends IService<OperationLog> {

    void record(LoginUser user, String operationType, String targetType, Long targetId, String ipAddress, String result);
}
