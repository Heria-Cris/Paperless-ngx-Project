package com.paperless.local.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.paperless.local.entity.DocumentTask;
import com.paperless.local.mapper.DocumentTaskMapper;
import com.paperless.local.service.DocumentTaskService;

@Service
public class DocumentTaskServiceImpl extends ServiceImpl<DocumentTaskMapper, DocumentTask> implements DocumentTaskService {
}
