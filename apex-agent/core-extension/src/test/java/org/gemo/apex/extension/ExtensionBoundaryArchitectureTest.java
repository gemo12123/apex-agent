package org.gemo.apex.extension;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionBoundaryArchitectureTest {

    private static final String PACKAGE_PATH = "org/gemo/apex/extension";
    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "java.", "org.gemo.apex.protocol.", "org.gemo.apex.common.",
            "org.gemo.apex.extension.");

    /**
     * 生产字节码只能包含无状态无实现无注解的接口
     */
    @Test
    void productionBytecodeContainsOnlyStatelessUnimplementedUnannotatedInterfaces() throws Exception {
        List<Class<?>> types = productionTypes();

        assertEquals(20, types.size(), "新增或删除端口时必须显式评审接口清单");
        types.forEach(this::validatePortType);
    }

    /**
     * 典型非法fixture会被纯接口规则拒绝
     */
    @Test
    void pureInterfaceRuleRejectsTypicalIllegalFixture() {
        assertThrows(IllegalStateException.class, () -> validatePortType(RecordFixture.class));
        assertThrows(IllegalStateException.class, () -> validatePortType(DefaultMethodFixture.class));
        assertThrows(IllegalStateException.class, () -> validatePortType(AnnotatedFixture.class));
        assertThrows(IllegalStateException.class, () -> validatePortType(FrameworkLeakFixture.class));
        assertThrows(IllegalStateException.class, () -> validatePortType(NestedTypeFixture.class));
    }

    private List<Class<?>> productionTypes() throws IOException, URISyntaxException, ClassNotFoundException {
        Enumeration<URL> roots = Thread.currentThread().getContextClassLoader().getResources(PACKAGE_PATH);
        List<Class<?>> types = new ArrayList<>();
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if (!"file".equals(root.getProtocol()) || root.toString().contains("test-classes")) {
                continue;
            }
            Path directory = Path.of(root.toURI());
            try (var files = Files.walk(directory)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                    String relative = directory.relativize(file).toString().replace('\\', '.');
                    String className = "org.gemo.apex.extension."
                            + relative.substring(0, relative.length() - ".class".length());
                    types.add(Class.forName(className));
                }
            }
        }
        return types.stream().distinct().toList();
    }

    private void validatePortType(Class<?> type) {
        require(type.isInterface(), type, "必须是 interface");
        require(type.getDeclaredAnnotations().length == 0, type, "不得声明注解");
        require(type.getDeclaredFields().length == 0, type, "不得声明字段或常量");
        require(type.getDeclaredClasses().length == 0, type, "不得声明嵌套类型");
        require(List.of(type.getMethods()).stream().noneMatch(Method::isDefault),
                type, "不得继承或声明 default 方法");
        validateAnnotations(type, type);
        for (Type parent : type.getGenericInterfaces()) validateSignature(type, parent);
        for (Method method : type.getDeclaredMethods()) {
            require(Modifier.isAbstract(method.getModifiers()), type, "不得包含 default/static 实现");
            validateAnnotations(type, method);
            for (var parameterAnnotations : method.getParameterAnnotations()) {
                require(parameterAnnotations.length == 0, type, "方法参数不得声明注解");
            }
            validateSignature(type, method.getGenericReturnType());
            for (Type parameter : method.getGenericParameterTypes()) validateSignature(type, parameter);
            for (Type exception : method.getGenericExceptionTypes()) validateSignature(type, exception);
        }
    }

    private void validateAnnotations(Class<?> owner, AnnotatedElement element) {
        for (var annotation : element.getDeclaredAnnotations()) {
            require(false, owner, "不得暴露注解 " + annotation.annotationType().getName());
        }
    }

    private void validateSignature(Class<?> owner, Type type) {
        if (type instanceof Class<?> rawType) {
            if (rawType.isPrimitive()) {
                return;
            }
            if (rawType.isArray()) {
                validateSignature(owner, rawType.getComponentType());
                return;
            }
            require(ALLOWED_PREFIXES.stream().anyMatch(rawType.getName()::startsWith), owner,
                    "签名泄漏禁止类型 " + rawType.getName());
            if (rawType.getName().startsWith("org.gemo.apex.extension.")) {
                require(rawType.isInterface(), owner, "只能组合同模块 interface " + rawType.getName());
            }
        } else if (type instanceof ParameterizedType parameterizedType) {
            validateSignature(owner, parameterizedType.getRawType());
            for (Type argument : parameterizedType.getActualTypeArguments()) validateSignature(owner, argument);
        } else if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) validateSignature(owner, bound);
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) validateSignature(owner, bound);
            for (Type bound : wildcard.getLowerBounds()) validateSignature(owner, bound);
        } else if (type instanceof GenericArrayType array) {
            validateSignature(owner, array.getGenericComponentType());
        } else {
            throw new IllegalStateException(owner.getName() + " 无法识别签名类型 " + type);
        }
    }

    private void require(boolean condition, Class<?> owner, String rule) {
        if (!condition) throw new IllegalStateException(owner.getName() + ": " + rule);
    }

    private record RecordFixture(String value) { }

    private interface DefaultMethodFixture {
        default String value() { return "value"; }
    }

    @FixtureAnnotation
    private interface AnnotatedFixture { }

    private interface FrameworkLeakFixture {
        org.junit.jupiter.api.Test leakedType();
    }

    private interface NestedTypeFixture {
        class Implementation { }
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface FixtureAnnotation { }
}
