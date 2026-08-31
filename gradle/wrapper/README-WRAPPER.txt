# Gradle Wrapper JAR Missing

由于网络限制，gradle-wrapper.jar 未包含在此项目中。

## 获取方式1：使用 Gradle 自动生成

在项目根目录运行：
```bash
gradle wrapper --gradle-version 8.5
```

这将自动下载并生成完整的 wrapper 文件。

## 获取方式2：手动下载

从以下地址下载 gradle-wrapper.jar：
https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar

将下载的文件放置在：
gradle/wrapper/gradle-wrapper.jar

## 获取方式3：使用 IntelliJ IDEA

1. 用 IntelliJ IDEA 打开项目
2. IDEA 会自动检测并提示下载 Gradle
3. 点击 "Download" 或 "Sync" 即可

## 获取方式4：完整安装 Gradle

如果你已经安装了 Gradle，可以直接使用：
```bash
gradle buildPlugin
```

不需要 wrapper。
