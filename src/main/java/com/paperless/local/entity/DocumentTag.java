package com.paperless.local.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("document_tag")
public class DocumentTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String color;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
