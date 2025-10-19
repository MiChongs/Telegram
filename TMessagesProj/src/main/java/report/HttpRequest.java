package report;

import android.os.Build;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.Cipher;

public class HttpRequest {

    public final static String BASE_URL = "http://localhost:8080";
    private final static String PUBLIC_KEY_PATH = "/api/public-key";
    private final static String TYPE = "application/json";

    public static final class UploadResult {
        public final boolean success;   // 是否成功
        public final int httpCode;  // HTTP 状态码，若未连上服务返回 -1
        public final String message;   // 服务端响应体 or 异常信息

        UploadResult(boolean ok, int code, String msg) {
            this.success = ok;
            this.httpCode = code;
            this.message = msg;
        }
    }

    public static UploadResult uploadSafe(String targetUrl, File file) {
        HttpURLConnection conn = null;
        final String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        final String LINE_FEED = "\r\n";

        try {
            // ① 建立连接
            conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            // 避免一次性写入内存，Android 推荐分块：
            conn.setChunkedStreamingMode(0);

            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

            // ② 写入文件 Part
            String mime = guessMime(file);      // 兼容 API19 的简易 MIME 推断
            dos.writeBytes("--" + boundary + LINE_FEED);
            dos.writeBytes("Content-Disposition: form-data; name=\"log_file\"; filename=\"" + file.getName() + "\"" + LINE_FEED);
            dos.writeBytes("Content-Type: " + mime + LINE_FEED);
            dos.writeBytes(LINE_FEED);

            // 文件内容 —— 手动拷贝流，比 Files.copy() 更兼容
            copyFileToStream(file, dos);

            dos.writeBytes(LINE_FEED);
            dos.writeBytes("--" + boundary + "--" + LINE_FEED);
            dos.flush();
            dos.close();

            Log.d("TG / 上传响应", conn.getResponseMessage());
            // ③ 读取响应
            int code = conn.getResponseCode();
            String msg = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            return new UploadResult(code >= 200 && code < 300, code, msg);

        } catch (Exception e) {
            Log.d("TG / 上传响应", e.getMessage());
            return new UploadResult(false, -1, e.toString());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /* ========== 辅助方法 ========== */

    /**
     * 读取整个流为字符串，异常时返回空串
     */
    private static String readStream(InputStream in) {
        if (in == null) return "";
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (IOException ignore) {
            return "";
        } finally {
            try {
                if (br != null) br.close();
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * 用 8 KB buffer 把文件写入 OutputStream
     */
    private static void copyFileToStream(File file, OutputStream out) throws IOException {
        byte[] buf = new byte[8 * 1024];
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            int len;
            while ((len = fis.read(buf)) != -1) out.write(buf, 0, len);
        } finally {
            try {
                if (fis != null) fis.close();
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * 尝试猜测 MIME，API19 无法用 Files.probeContentType
     */
    private static String guessMime(File file) {
        // 先从后缀猜
        String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        String mime = ext != null ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) : null;
        if (mime != null) return mime;

        // 再退回 URLConnection
        mime = URLConnection.guessContentTypeFromName(file.getName());
        return mime != null ? mime : "application/octet-stream";
    }

    public static JsonObject get(String url, Map<String, Object> headers, Map<String, Object> query) {
        StringBuilder queryString = new StringBuilder();
        if (query != null && !query.isEmpty()) {
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                if (queryString.length() > 0) {
                    queryString.append("&");
                }
                queryString.append(entry.getKey()).append("=").append(entry.getValue());
            }
            url += "?" + queryString;
        }

        try {
            URL obj = new URL(BASE_URL + url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            con.setRequestMethod("GET");

            if (headers != null) {
                for (Map.Entry<String, Object> entry : headers.entrySet()) {
                    con.setRequestProperty(entry.getKey(), entry.getValue().toString());
                }
            }

            int responseCode = con.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JsonParser parser = new JsonParser();
                return parser.parse(response.toString()).getAsJsonObject();
            } else {
                Log.e("TG 调试 / HttpRequest", "发送 GET 请求响应失败");
            }
        } catch (Exception e) {
            Log.e("TG 调试 / HttpRequest", "发送 GET 请求错误");
            Log.e("TG 调试 / HttpRequest", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static JsonObject post(String url, Map<String, Object> headers, Map<String, Object> query, Map<String, Object> body) {
        StringBuilder queryString = new StringBuilder();
        if (query != null && !query.isEmpty()) {
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                if (queryString.length() > 0) {
                    queryString.append("&");
                }
                queryString.append(entry.getKey()).append("=").append(entry.getValue());
            }
            url += "?" + queryString;
        }

        try {
            URL obj = new URL(BASE_URL + url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            con.setRequestMethod("POST");

            if (headers != null) {
                for (Map.Entry<String, Object> entry : headers.entrySet()) {
                    con.setRequestProperty(entry.getKey(), entry.getValue().toString());
                }
            }
            con.setRequestProperty("Content-Type", TYPE);

            con.setDoOutput(true);
            con.setDoInput(true);

            Map<String, Object> encryptedBody = new HashMap<>();
            encryptedBody.put("encrypted_data", encryptWithPublicKey(new Gson().toJson(body)));

            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(new Gson().toJson(encryptedBody));
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JsonParser parser = new JsonParser();
                return parser.parse(response.toString()).getAsJsonObject();
            } else {
                Log.e("TG 调试 / HttpRequest", "发送 POST 请求响应失败");
            }
        } catch (Exception e) {
            Log.e("TG 调试 / HttpRequest", "发送 POST 请求错误");
            Log.e("TG 调试 / HttpRequest", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static String encryptWithPublicKey(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, parsePublicKey());
        byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } else {
            return null;
        }
    }

    private static PublicKey parsePublicKey() throws Exception {
        JsonObject jsonObject = get(PUBLIC_KEY_PATH, null, null);
        JsonObject dataObject = jsonObject.getAsJsonObject("data");

        String publicKeyPEM = dataObject.get("public_key").getAsString().replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace("-----BEGIN RSA PUBLIC KEY-----", "").replace("-----END RSA PUBLIC KEY-----", "").replaceAll("\r", "").replaceAll("\n", "");

        byte[] decodedKey = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            decodedKey = Base64.getDecoder().decode(publicKeyPEM);
        }

        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(keySpec);
    }

}
