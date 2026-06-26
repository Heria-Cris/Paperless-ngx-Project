package com.paperless.local.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.paperless.local.entity.DocumentTag;
import com.paperless.local.mapper.DocumentTagMapper;
import com.paperless.local.service.DocumentTagService;

@Service
public class DocumentTagServiceImpl extends ServiceImpl<DocumentTagMapper, DocumentTag> implements DocumentTagService {
}
