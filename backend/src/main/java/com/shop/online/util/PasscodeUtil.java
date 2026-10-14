package com.shop.online.util;

import com.shop.online.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 口令码工具（按组员C 技术方案第 5 节实现）
 *
 * 流程：
 *  1. SecureRandom 生成 16 字节随机值 → 转为 32 位十六进制字符串（原始口令码，仅展示给买家一次）
 *  2. 用环境变量里的密钥计算 HMAC-SHA256 → 32 字节摘要（存入 purchase_intent.code_hash BINARY(32)）
 *  3. 有效期默认 7 天
 *
 * 密钥来源：环境变量 PASSCODE_HMAC_KEY，未配置则回退到固定值（仅开发/演示用，严禁生产环境）
 */
public final class PasscodeUtil {

    public static final int RAW_BYTES = 16;
    public static final String ALGO = "HmacSHA256";
    /** 口令码默认有效期（天） */
    public static final int DEFAULT_EXPIRE_DAYS = 7;

    /** 环境变量名：HMAC 密钥 */
    private static final String ENV_KEY = "PASSCODE_HMAC_KEY";
    /** 开发/演示环境的回退密钥（不要用于生产） */
    private static final String DEV_FALLBACK_KEY = "shop-demo-hmac-key-change-me";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasscodeUtil() {}

    /**
     * 生成新口令码
     * @return 32 位十六进制原始口令码（只展示给买家一次，数据库不存）
     */
    public static String generateRawCode() {
        byte[] bytes = new byte[RAW_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    /**
     * 计算原始口令码的 HMAC-SHA256 摘要
     * @param rawCode 32 位十六进制原始口令码
     * @return 32 字节摘要（对应 purchase_intent.code_hash BINARY(32)）
     */
    public static byte[] computeHash(String rawCode) {
        try {
            String keyValue = System.getenv(ENV_KEY);
            if (keyValue == null || keyValue.isBlank()) {
                keyValue = DEV_FALLBACK_KEY;
            }
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(keyValue.getBytes(java.nio.charset.StandardCharsets.UTF_8), ALGO));
            return mac.doFinal(rawCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BusinessException(500, "口令码计算失败：" + e.getMessage());
        }
    }

    /**
     * 计算口令码的过期时间（UTC + 默认 7 天）
     */
    public static LocalDateTime defaultExpiry() {
        return LocalDateTime.now(ZoneOffset.UTC).plusDays(DEFAULT_EXPIRE_DAYS);
    }

    /**
     * 比对原始口令码与存储摘要（不暴露原始值，适合 lookup 接口）
     */
    public static boolean verify(String rawCode, byte[] storedHash) {
        if (rawCode == null || storedHash == null) return false;
        byte[] computed = computeHash(rawCode);
        // 恒定时间比对，防时序攻击
        if (computed.length != storedHash.length) return false;
        int diff = 0;
        for (int i = 0; i < computed.length; i++) diff |= computed[i] ^ storedHash[i];
        return diff == 0;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
