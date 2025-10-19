package org.telegram.messenger;

import android.util.Base64;
import android.util.Log;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;

/**
 * RSA加密工具类
 * 用于与服务器进行加密通信
 */
public class RSAEncryption {
    private static final String TAG = "RSAEncryption";
    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final String CHARSET = "UTF-8";

    /**
     * 使用RSA公钥加密文本
     * @param plainText 明文
     * @param publicKeyStr Base64编码的公钥字符串
     * @return Base64编码的密文
     */
    public static String encrypt(String plainText, String publicKeyStr) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        if (publicKeyStr == null || publicKeyStr.isEmpty()) {
            Log.w(TAG, "Public key is empty, returning plain text");
            return plainText;
        }

        try {
            // 清理公钥字符串（移除头尾和换行符）
            String cleanPublicKey = publicKeyStr
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

            // 解码公钥
            byte[] publicKeyBytes = Base64.decode(cleanPublicKey, Base64.DEFAULT);

            // 生成公钥对象
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // 初始化加密器
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            // 加密数据
            byte[] plainBytes = plainText.getBytes(CHARSET);
            byte[] encryptedBytes = cipher.doFinal(plainBytes);

            // 返回Base64编码的密文
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "RSA encryption failed", e);
            // 加密失败时返回原文，确保消息能够发送
            return plainText;
        }
    }

    /**
     * 使用RSA公钥加密字节数组
     * @param data 要加密的字节数组
     * @param publicKeyStr Base64编码的公钥字符串
     * @return Base64编码的密文
     */
    public static String encryptBytes(byte[] data, String publicKeyStr) {
        if (data == null || data.length == 0) {
            return null;
        }

        if (publicKeyStr == null || publicKeyStr.isEmpty()) {
            Log.w(TAG, "Public key is empty");
            return null;
        }

        try {
            // 清理公钥字符串
            String cleanPublicKey = publicKeyStr
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

            // 解码公钥
            byte[] publicKeyBytes = Base64.decode(cleanPublicKey, Base64.DEFAULT);

            // 生成公钥对象
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // 初始化加密器
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            // 加密数据
            byte[] encryptedBytes = cipher.doFinal(data);

            // 返回Base64编码的密文
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "RSA encryption failed for bytes", e);
            return null;
        }
    }

    /**
     * 验证公钥格式是否有效
     * @param publicKeyStr 公钥字符串
     * @return true如果公钥有效
     */
    public static boolean isValidPublicKey(String publicKeyStr) {
        if (publicKeyStr == null || publicKeyStr.isEmpty()) {
            return false;
        }

        try {
            // 清理公钥字符串
            String cleanPublicKey = publicKeyStr
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

            // 尝试解码和生成公钥对象
            byte[] publicKeyBytes = Base64.decode(cleanPublicKey, Base64.DEFAULT);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            return publicKey != null;

        } catch (Exception e) {
            Log.e(TAG, "Invalid public key format", e);
            return false;
        }
    }

    /**
     * 分块加密大数据
     * RSA加密有长度限制，这个方法会自动分块加密
     * @param plainText 明文
     * @param publicKeyStr Base64编码的公钥字符串
     * @return Base64编码的密文
     */
    public static String encryptLargeText(String plainText, String publicKeyStr) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        if (publicKeyStr == null || publicKeyStr.isEmpty()) {
            Log.w(TAG, "Public key is empty, returning plain text");
            return plainText;
        }

        try {
            // 清理公钥字符串
            String cleanPublicKey = publicKeyStr
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

            // 解码公钥
            byte[] publicKeyBytes = Base64.decode(cleanPublicKey, Base64.DEFAULT);

            // 生成公钥对象
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // 初始化加密器
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            // 获取最大加密块大小（通常是密钥长度/8 - 11）
            // 对于2048位的RSA密钥，最大块大小是245字节
            int keySize = ((java.security.interfaces.RSAPublicKey) publicKey).getModulus().bitLength();
            int maxBlockSize = keySize / 8 - 11; // PKCS1Padding需要11字节

            // 将明文转换为字节数组
            byte[] plainBytes = plainText.getBytes(CHARSET);

            // 如果数据小于最大块大小，直接加密
            if (plainBytes.length <= maxBlockSize) {
                byte[] encryptedBytes = cipher.doFinal(plainBytes);
                return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
            }

            // 分块加密
            StringBuilder result = new StringBuilder();
            int offset = 0;

            while (offset < plainBytes.length) {
                int blockSize = Math.min(maxBlockSize, plainBytes.length - offset);
                byte[] block = new byte[blockSize];
                System.arraycopy(plainBytes, offset, block, 0, blockSize);

                byte[] encryptedBlock = cipher.doFinal(block);
                result.append(Base64.encodeToString(encryptedBlock, Base64.NO_WRAP));

                if (offset + blockSize < plainBytes.length) {
                    result.append("|"); // 分隔符
                }

                offset += blockSize;
            }

            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "RSA encryption failed for large text", e);
            // 加密失败时返回原文
            return plainText;
        }
    }
}