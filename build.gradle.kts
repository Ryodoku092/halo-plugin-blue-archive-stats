plugins {
    java
}

group = "plugin.bluearchive"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven("https://maven.aliyun.com/repository/public/")
}

sourceSets {
    main {
        java { srcDirs("src/main/java", "stub-src") }
        resources { srcDir("src/main/resources") }
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    // Spring / Reactor 由 Halo 运行时提供（Halo 基于 Spring Boot WebFlux），仅编译期使用
    compileOnly("org.springframework:spring-web:6.1.1")
    compileOnly("org.springframework:spring-context:6.1.1")
    compileOnly("org.springframework:spring-beans:6.1.1")
    compileOnly("org.springframework:spring-webflux:6.1.1")
    compileOnly("jakarta.annotation:jakarta.annotation-api:2.1.1")
    compileOnly("io.projectreactor:reactor-core:3.6.3")
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    implementation("org.slf4j:slf4j-api:2.0.9")
    // BasePlugin stub 继承 org.pf4j.Plugin；运行时由 Halo 提供
    compileOnly("org.pf4j:pf4j:3.10.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Fat JAR：合并第三方依赖（jackson/slf4j），Halo 是 WebFlux 应用不提供这些库；
// HTTP 用 JDK 自带 java.net.http，不再打包 httpclient5
// Spring/Reactor/pf4j 由 Halo 运行时提供（compileOnly），保持不打包
tasks.jar {
    archiveBaseName.set("plugin-blue-archive-stats")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // 编译期 stub 不打包：运行时由 Halo 提供真实的 run.halo.app.plugin.* 类
    exclude("run/**")
    // 合并运行时依赖的类
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/MANIFEST.MF")
        exclude("module-info.class")
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
