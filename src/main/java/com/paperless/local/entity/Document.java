package com.paperless.local.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String originalFilename;

    private String storedFilename;

    private String storagePath;

    private Long fileSize;

    private String fileType;

    private Long categoryId;

    private Long uploadUserId;

    private String description;

    @TableLogic
    private Integer deleted;

    private LocalDateTime uploadedAt;

    private LocalDateTime updatedAt;
}
