package ftc;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class ObjectProxyCreator {

    public static void main(String[] args) {
        String reference = "java.lang.String@864ca4[count=2]";

        // 创建代理对象
        Object proxiedObject = createProxyFromReference(reference);

        // 使用代理对象
        if (proxiedObject instanceof String) {
            String str = (String) proxiedObject;
            System.out.println("长度: " + str.length());
            System.out.println("内容: \"" + str + "\"");
        }
    }

    /**
     * 从对象引用字符串创建代理对象
     */
    public static Object createProxyFromReference(String reference) {
        try {
            // 解析类名和属性
            int atIndex = reference.indexOf('@');
            String className = reference.substring(0, atIndex);

            // 提取属性
            Map<String, String> properties = extractProperties(reference);

            // 获取类的Class对象
            Class<?> originalClass = Class.forName(className);

            // 如果是接口，直接创建代理
            if (originalClass.isInterface()) {
                return createInterfaceProxy(originalClass, properties);
            }

            // 如果是具体类，尝试找到它实现的所有接口
            else if (!originalClass.isPrimitive()) {
                return createConcreteClassProxy(originalClass, properties);
            }

            // 对于原始类型，直接返回默认值
            else {
                return getDefaultValue(originalClass);
            }
        } catch (Exception e) {
            System.err.println("创建代理对象失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 提取对象属性
     */
    private static Map<String, String> extractProperties(String reference) {
        Map<String, String> properties = new HashMap<>();

        int openBracket = reference.indexOf('[');
        if (openBracket > 0) {
            int closeBracket = reference.indexOf(']', openBracket);
            if (closeBracket > 0) {
                String propertiesStr = reference.substring(openBracket + 1, closeBracket);
                String[] pairs = propertiesStr.split(",");

                for (String pair : pairs) {
                    String[] keyValue = pair.trim().split("=");
                    if (keyValue.length == 2) {
                        properties.put(keyValue[0].trim(), keyValue[1].trim());
                    }
                }
            }
        }

        // 添加哈希码信息
        int atIndex = reference.indexOf('@');
        int bracketOrEnd = reference.indexOf('[');
        if (bracketOrEnd < 0) bracketOrEnd = reference.length();

        if (atIndex > 0 && atIndex < bracketOrEnd) {
            String hashCode = reference.substring(atIndex + 1, bracketOrEnd);
            properties.put("hashCode", hashCode);
        }

        return properties;
    }

    /**
     * 为接口创建代理
     */
    @SuppressWarnings("unchecked")
    private static <T> T createInterfaceProxy(Class<T> interfaceClass, Map<String, String> properties) {
        // 创建调用处理器
        InvocationHandler handler = new PropertiesInvocationHandler(properties);

        // 创建代理
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[] { interfaceClass },
                handler
        );
    }

    /**
     * 为具体类创建代理（基于其实现的接口）
     */
    private static Object createConcreteClassProxy(Class<?> concreteClass, Map<String, String> properties) {
        Class<?>[] interfaces = concreteClass.getInterfaces();

        // 如果没有实现接口，尝试创建实例
        if (interfaces.length == 0) {
            try {
                // 尝试使用无参构造函数创建实例
                return concreteClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println("无法创建类的实例: " + e.getMessage());

                // 特殊处理常见类
                if (concreteClass == String.class) {
                    // 尝试创建特定长度的字符串
                    if (properties.containsKey("count")) {
                        int count = Integer.parseInt(properties.get("count"));
                        char[] chars = new char[count];
                        // 填充一些占位符字符
                        for (int i = 0; i < count; i++) {
                            chars[i] = '*';
                        }
                        return new String(chars);
                    } else {
                        return "";
                    }
                }

                return null;
            }
        }

        // 如果实现了接口，创建代理
        InvocationHandler handler = new PropertiesInvocationHandler(properties);
        return Proxy.newProxyInstance(
                concreteClass.getClassLoader(),
                interfaces,
                handler
        );
    }

    /**
     * 获取原始类型的默认值
     */
    private static Object getDefaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return null;
    }

    /**
     * 基于属性的调用处理器
     */
    private static class PropertiesInvocationHandler implements InvocationHandler {
        private final Map<String, String> properties;

        public PropertiesInvocationHandler(Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            // 处理特殊方法
            if (methodName.equals("toString")) {
                return createToString(proxy.getClass().getInterfaces()[0], properties);
            }

            if (methodName.equals("hashCode")) {
                if (properties.containsKey("hashCode")) {
                    try {
                        return Integer.parseInt(properties.get("hashCode"), 16);
                    } catch (NumberFormatException e) {
                        return System.identityHashCode(proxy);
                    }
                }
                return System.identityHashCode(proxy);
            }

            if (methodName.equals("equals")) {
                return proxy == args[0];
            }

            // 处理getter方法
            if (methodName.startsWith("get") && args == null) {
                String propertyName = methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
                if (properties.containsKey(propertyName)) {
                    return convertValue(properties.get(propertyName), method.getReturnType());
                }
            }

            // 处理is方法
            if (methodName.startsWith("is") && args == null) {
                String propertyName = methodName.substring(2, 3).toLowerCase() + methodName.substring(3);
                if (properties.containsKey(propertyName)) {
                    return Boolean.valueOf(properties.get(propertyName));
                }
            }

            // 处理length方法（针对String等）
            if (methodName.equals("length") && args == null) {
                if (properties.containsKey("count")) {
                    return Integer.parseInt(properties.get("count"));
                }
            }

            // 处理其他方法
            return getDefaultReturnValue(method.getReturnType());
        }

        /**
         * 创建toString方法的返回值
         */
        private String createToString(Class<?> interfaceClass, Map<String, String> properties) {
            StringBuilder sb = new StringBuilder();
            sb.append(interfaceClass.getName()).append("@");

            // 添加哈希码
            String hashCodeStr = properties.getOrDefault("hashCode", "0");
            sb.append(hashCodeStr);

            // 添加属性
            if (!properties.isEmpty()) {
                sb.append("[");
                boolean first = true;
                for (Map.Entry<String, String> entry : properties.entrySet()) {
                    if (entry.getKey().equals("hashCode")) continue;

                    if (!first) {
                        sb.append(",");
                    }
                    first = false;
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                }
                sb.append("]");
            }

            return sb.toString();
        }

        /**
         * 将字符串值转换为指定类型
         */
        private Object convertValue(String value, Class<?> type) {
            if (type == String.class) {
                return value;
            } else if (type == int.class || type == Integer.class) {
                return Integer.parseInt(value);
            } else if (type == long.class || type == Long.class) {
                return Long.parseLong(value);
            } else if (type == double.class || type == Double.class) {
                return Double.parseDouble(value);
            } else if (type == float.class || type == Float.class) {
                return Float.parseFloat(value);
            } else if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(value);
            } else if (type == char.class || type == Character.class) {
                return value.charAt(0);
            } else {
                return null;
            }
        }

        /**
         * 获取默认返回值
         */
        private Object getDefaultReturnValue(Class<?> returnType) {
            if (returnType == void.class) {
                return null;
            } else if (returnType.isPrimitive()) {
                return getDefaultValue(returnType);
            } else {
                return null;
            }
        }
    }
}