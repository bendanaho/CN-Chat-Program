package com.cncd.ch04.common;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.util.Base64;
/**
 * 消息体加密层(功能十三)。
 * 位置:UTF-8 编码之后、0xFF 帧结束符之前——把整段明文字节(含 0xFD 命令标记)
 * AES/CBC 加密(随机 IV 前置),再 Base64 成纯 ASCII 字节上线。
 * Base64 字符集不含 0xFF/0xFD/0x01,因此既有帧协议(0xFF 分帧)原样可用。
 * 抓包演示:两端都加 -Dchat.plain=true 启动即回到明文,可对比"密文 vs 明文"。
 */
public class CryptoUtil {
    public static final boolean ENABLED = !Boolean.getBoolean("chat.plain");
    private static final String PASSPHRASE = "cncd-ch04-exp4-secret"; // 预共享密钥口令(课设简化:不做密钥协商)
    private static SecretKeySpec key;
    private static final SecureRandom rnd = new SecureRandom();
    static {
        try {
            byte[] k = MessageDigest.getInstance("MD5").digest(PASSPHRASE.getBytes("UTF-8"));
            key = new SecretKeySpec(k, "AES"); // MD5 口令派生 128 位 AES 密钥
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    /** 明文字节 → [IV(16B) + AES密文] 的 Base64 字节;关闭加密时原样返回 */
    public static byte[] wrap(byte[] plain) {
        if(!ENABLED) return plain;
        try {
            byte[] iv = new byte[16];
            rnd.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            byte[] enc = c.doFinal(plain);
            byte[] all = new byte[16 + enc.length];
            System.arraycopy(iv, 0, all, 0, 16);
            System.arraycopy(enc, 0, all, 16, enc.length);
            return Base64.getEncoder().encode(all);
        } catch(Exception e) { throw new RuntimeException("encrypt failed", e); }
    }
    /** wrap 的逆操作;解不开通常意味着两端加密开关不一致 */
    public static byte[] unwrap(byte[] wire) {
        if(!ENABLED) return wire;
        try {
            byte[] all = Base64.getDecoder().decode(wire);
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, key,
                   new IvParameterSpec(java.util.Arrays.copyOfRange(all, 0, 16)));
            return c.doFinal(java.util.Arrays.copyOfRange(all, 16, all.length));
        } catch(Exception e) { throw new RuntimeException("decrypt failed (两端加密开关是否一致?)", e); }
    }
}
