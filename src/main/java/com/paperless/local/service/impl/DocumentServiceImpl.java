package com.paperless.local.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.paperless.local.entity.Document;
import com.paperless.local.mapper.DocumentMapper;
import com.paperless.local.service.DocumentService;

@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {
}
