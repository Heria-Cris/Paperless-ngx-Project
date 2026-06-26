package com.paperless.local.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("document_category")
public class DocumentCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
