package com.paperless.local.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("document_tag_rel")
public class DocumentTagRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long tagId;

    private LocalDateTime createdAt;
}
