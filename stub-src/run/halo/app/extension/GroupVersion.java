package run.halo.app.extension;

/**
 * Halo Plugin API Stub - 用于编译
 * 形状与真实 Halo API 完全一致（record GroupVersion(String group, String version)），
 * 运行时由 Halo 提供真实类，此 stub 不会打包进最终 JAR。
 */
public record GroupVersion(String group, String version) {

    public static GroupVersion parseAPIVersion(String apiVersion) {
        var groupVersion = apiVersion.split("/");
        return switch (groupVersion.length) {
            case 1 -> new GroupVersion("", apiVersion);
            case 2 -> new GroupVersion(groupVersion[0], groupVersion[1]);
            default -> throw new IllegalArgumentException("Unexpected APIVersion string: " + apiVersion);
        };
    }
}
