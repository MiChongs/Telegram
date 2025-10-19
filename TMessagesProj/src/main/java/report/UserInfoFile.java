package report;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import report.entity.UserInfo;

public class UserInfoFile {

    private static final String FILE_NAME = "userinfo.json";
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public static UserInfo readFile(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);

        if (!file.exists()) {
            UserInfo userInfo = new UserInfo();

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(GSON.toJson(userInfo).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.e("TG 调试 / UserInfoFile", "读取用户信息文件时，写入默认用户信息文件失败", e);
            }

            return userInfo;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(isr);

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            return GSON.fromJson(builder.toString(), UserInfo.class);
        } catch (Exception e) {
            Log.e("TG 调试 / UserInfoFile", "读取用户信息文件失败", e);

            return new UserInfo();
        }
    }

    public static void writeFile(Context context, UserInfo userInfo) {
        try (FileOutputStream fos = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)) {
            fos.write(GSON.toJson(userInfo).getBytes());
        } catch (Exception e) {
            Log.e("TG 调试 / UserInfoFile", "写入用户信息文件失败", e);
        }
    }

}
