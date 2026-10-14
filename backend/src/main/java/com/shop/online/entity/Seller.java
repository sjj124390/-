package com.shop.online.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 卖家账号实体（对应组员C schema 的 seller 表）
 * 组员C 约束：id 只能为 1（唯一卖家）
 */
@Data
public class Seller implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 固定为 1 */
    private Integer id;

    /** 卖家账号 */
    private String username;

    /** 登录密码哈希（BCrypt/Argon2，JSON 序列化时忽略） */
    @JsonIgnore
    private String passwordHash;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
