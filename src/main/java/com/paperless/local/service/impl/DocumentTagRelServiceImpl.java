package com.paperless.local.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.paperless.local.entity.DocumentTagRel;
import com.paperless.local.mapper.DocumentTagRelMapper;
import com.paperless.local.service.DocumentTagRelService;

@Service
public class DocumentTagRelServiceImpl extends ServiceImpl<DocumentTagRelMapper, DocumentTagRel> implements DocumentTagRelService {
}
