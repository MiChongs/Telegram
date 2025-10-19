package report;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 验证码提取工具类
 * 专用于从字符串中提取数字验证码
 */
public class VerificationCodeExtractor {

    // 预编译的正则表达式
    private static final Pattern FIVE_DIGITS_PATTERN = Pattern.compile("\\b\\d{5}\\b");
    private static final Pattern SMART_KEYWORD_PATTERN =
            Pattern.compile("(?:验证码|验证|码|code|Code|CODE)[^\\d]*(\\d{5})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOSE_FIVE_DIGITS_PATTERN = Pattern.compile("(\\d{5})");

    /**
     * 提取字符串中的5位数字验证码
     *
     * @param text 可能包含验证码的字符串
     * @return 提取到的5位数字验证码，如果未找到则返回null
     */
    public static String extract(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        Matcher matcher = FIVE_DIGITS_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    /**
     * 提取字符串中的所有5位数字验证码
     *
     * @param text 可能包含验证码的字符串
     * @return 提取到的所有5位数字验证码列表，如果未找到则返回空列表
     */
    public static List<String> extractAll(String text) {
        List<String> codes = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return codes;
        }

        Matcher matcher = FIVE_DIGITS_PATTERN.matcher(text);
        while (matcher.find()) {
            codes.add(matcher.group());
        }

        return codes;
    }

    /**
     * 提取字符串中的数字验证码，支持自定义位数
     *
     * @param text 可能包含验证码的字符串
     * @param digits 验证码位数
     * @return 提取到的指定位数的数字验证码，如果未找到则返回null
     */
    public static String extractWithLength(String text, int digits) {
        if (text == null || text.isEmpty() || digits <= 0) {
            return null;
        }

        Pattern pattern = Pattern.compile("\\b\\d{" + digits + "}\\b");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    /**
     * 智能提取文本中的5位验证码
     * 尝试从各种常见验证码格式中提取
     *
     * @param text 包含验证码的文本
     * @return 提取到的验证码，如果未找到则返回null
     */
    public static String extractSmartly(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // 1. 尝试提取"验证码"、"code"等关键词附近的5位数字
        Matcher matcherWithKeyword = SMART_KEYWORD_PATTERN.matcher(text);
        if (matcherWithKeyword.find()) {
            return matcherWithKeyword.group(1);
        }

        // 2. 尝试提取任意5位数字
        Matcher simpleMatcher = FIVE_DIGITS_PATTERN.matcher(text);
        if (simpleMatcher.find()) {
            return simpleMatcher.group();
        }

        // 3. 更宽松的匹配，尝试找到任何5个连续数字
        Matcher looseMatcher = LOOSE_FIVE_DIGITS_PATTERN.matcher(text);
        if (looseMatcher.find()) {
            return looseMatcher.group(1);
        }

        return null;
    }

    /**
     * 从文本中提取最可能是验证码的数字序列
     * 按照常见的验证码位数进行优先尝试
     *
     * @param text 包含验证码的文本
     * @return 提取到的验证码，如果未找到则返回null
     */
    public static String extractAnyVerificationCode(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // 常见验证码位数：6位、5位、4位
        for (int digits : new int[]{6, 5, 4}) {
            String code = extractWithLength(text, digits);
            if (code != null) {
                return code;
            }
        }

        // 如果找不到规则的验证码，尝试宽松匹配
        for (int digits : new int[]{6, 5, 4}) {
            Pattern loosePattern = Pattern.compile("(\\d{" + digits + "})");
            Matcher looseMatcher = loosePattern.matcher(text);
            if (looseMatcher.find()) {
                return looseMatcher.group(1);
            }
        }

        return null;
    }
}