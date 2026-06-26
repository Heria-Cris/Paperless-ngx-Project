package com.paperless.local.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.paperless.local.entity.DocumentCategory;
import com.paperless.local.mapper.DocumentCategoryMapper;
import com.paperless.local.service.DocumentCategoryService;

@Service
public class DocumentCategoryServiceImpl extends ServiceImpl<DocumentCategoryMapper, DocumentCategory> implements DocumentCategoryService {
}
