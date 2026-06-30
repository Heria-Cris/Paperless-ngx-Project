package com.paperless.local.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("document_task")
public class DocumentTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Long userId;

    private String status;

    private String priority;

    private LocalDateTime dueAt;

    private LocalDateTime handledAt;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
