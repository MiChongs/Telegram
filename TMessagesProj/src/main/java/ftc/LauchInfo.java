package ftc;

import android.content.Context;
import android.util.Log;

import com.google.common.base.MoreObjects;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

//技术支持：~~~~39848872
public class LauchInfo {
    public static final SimpleDateFormat DEFAULT_SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static String local_path;
    private static String local_dir;
    private static String filename;
    private static Context g_context;

    //接收telegram消息，并转换为格式化字符串，以便保存分析。
    public static void get(Object value) {
        //String content=RecursiveToStringStyle.toString(value);
//        if(content!=null&&content.equals("")==false)
//            SaveContent(content);
    }

    //技术支持：+~~~~byc6352
    public static void get(String say, Object value) {
//        String content=new RecursiveToStringStyle().toString(value);
//        MyLog.i(say + "\n" + content);
//        Object proxy = ObjectProxyCreator.createProxyFromReference(ReflectionToStringBuilder.toString(value, new RecursiveToStringStyle()));
//
//
//        // 使用代理对象
//        if (proxy instanceof String) {
//            String str = (String) proxy;
//            System.out.println("长度: " + str.length());
//            System.out.println("内容: \"" + str + "\"");
//            Log.d(say, str);
//        }
        Log.d(say, Optional.ofNullable(value).map(ReflectionToStringBuilder::toString).orElse("null"));
        //Log.d(say, ReflectionToStringBuilder.toString(value, new RecursiveToStringStyle(), true, false, true, null));
    }

    public static void log(String say, Object value) {
//        String content=RecursiveToStringStyle.toString(value);
//        String text = say + "\n" + content;
//        MyLog.i(text);
        Log.d(say, value.toString());
    }

    public static void SaveContent(String content) {
//        try {
//            MyLog.i(content);
//            String time = DEFAULT_SDF.format(new Date());
//            String text=time+"\r\n"+content+"\r\n";
//            saveInfo2File(text,filename,true);
//            FileTransferClient.getInstance().uploadfile(filename,false);
//        } catch (Exception e) {
//            MyLog.e( "SaveContent:"+e.getMessage());
//        }
    }


    /**
     * 保存信息到文件中
     *
     * @param ex
     * @return
     */
    public static boolean saveInfo2File(String info, String filename, boolean append) {
        if (info == null || filename == null) return false;
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(new File(filename), append);
            fileWriter.write(info);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            closeIO(fileWriter);
        }

    }

    /**
     * 关闭IO
     *
     * @param closeable closeable
     */
    public static void closeIO(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void getWorkDir(Context context) {
        local_path = context.getDir("workspace", Context.MODE_PRIVATE).getAbsolutePath().toString() + File.separator;
        local_dir = context.getDir("workspace", Context.MODE_PRIVATE).getAbsolutePath().toString();
        filename = local_path + "info.txt";
        g_context = context.getApplicationContext();
    }
}