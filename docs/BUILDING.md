# 构建与测试

## 环境

JDK 17，JAVA_HOME 指向该 JDK；Gradle Wrapper 8.8。首次联网访问 Gradle、Forge、Mojang、
Maven Central 和 Modrinth Maven，不需要系统 Gradle、本地 libs/、相邻 CYS 工程或测试实例。

```powershell
.\gradlew.bat build
```

Linux/macOS：`bash ./gradlew build`。用户缓存目录遵循 GRADLE_USER_HOME。
ForgeGradle 6.0.54 与 MixinGradle 0.7.38 使用固定版本。

## 编译依赖

Modrinth Maven 坐标使用不可变版本 ID：

| 项目 | 版本 | 坐标 | 用途 |
| --- | --- | --- | --- |
| JEI | 15.49.0.188 | maven.modrinth:u6dRKJwZ:hVXQAD0D | 可选 API，compileOnly |
| Pufferfish's Skills | 0.19.2，1.20 Forge | maven.modrinth:hqQqvaa4:zfbPzmtb | 必需技能 API，重映射 |
| Pufferfish's Skills | 0.18.3，1.20 Forge | maven.modrinth:skills:eLDXb816 | 旧版回归专用，不参与正式编译或打包 |
| JEED | 1.20-2.2.6 | maven.modrinth:EO27GKs1:6zbEptBe | 可选 API，compileOnly |

依赖从发布者下载，不提交或嵌套第三方 JAR。Skills 为自定义许可，JEED 保留所有权利，
JEI 为 MIT。编译使用不等于获得任意打包许可。

Youkai适配使用可选类名/反射，不直接引用宿主编译类型；已移除无必要的整份模组编译依赖。
其运行支持仍保留，宿主专项检查仍可通过已有实例目录执行。

Skills运行范围为`[0.18.3,0.20)`；编译仍使用0.19.2。build会隔离两版classpath，分别运行同一组双树解析、路线互斥、点数/退款与存取测试，并对两版核对全部生产API及lambda方法句柄。
测试检查实际载入的版本，避免旧版回归误用编译版。旧版测试使用发布者slug别名区分ForgeGradle重映射输入，不改版本ID、不引入本地JAR依赖；不保证0.18.3之前或0.20及之后版本。
这些自动检查不等于整合包实机已验收；Skills客户端与服务端还须彼此匹配，整合包技能桥等附属也须满足其自身版本要求。

## 测试边界

build 运行 gradle/verification.gradle 的显式回归入口，并检查重混淆、refmap、正式产物内容与独立产物隔离。
纯 Java/ASM 检查不启动 Minecraft。可选宿主检查可指向已有目录：

```powershell
$env:CUISINEJOURNAL_COMPAT_MODS = 'E:/Minecraft/test-instance/mods'
.\gradlew.bat build
```

不指定时宿主专项明确跳过，不能写成兼容通过。个别测试另有 CUISINEJOURNAL_* JAR 参数，
以测试源码为准。调查工具生成的 build/ 证据不是基础构建前提。

可选宿主注入有契约预检，方法或调用点漂移时会明确警告并跳过对应适配，不意味着该版本完整支持。
另有通用共享目标冲突检查，识别第三方Overwrite与争用指令的Redirect后整组避让；原版/Forge和必需注入不降级。verifyOptionalMixinConflicts核所用Mixin库准备顺序与目标映射契约；CUISINEJOURNAL_CONFLICT_MODS可给实际冲突实例mods目录，读取直接及内嵌JAR作回归，不改实例或启动游戏。
CUISINEJOURNAL_LEGACY_MODS可给旧代宿主目录，核预期降级；新版检查不应因容错而被静默跳过。
涉及注入/专服隔离的新改动必须再编译及启动验证，不能以源码语法检查代替。

## 干净副本

Python 3.10+ 与 Git 用于核对范围并复制干净源码：

```powershell
python tools/prepare_repository.py --report build/repository-preparation/report.json
python tools/prepare_repository.py --copy-to build/repository-preparation/clean-source
cd build/repository-preparation/clean-source
.\gradlew.bat build
```

工具只复制 Git 忽略规则允许的文件，不初始化原项目仓库、不暂存/提交/推送，不覆盖已存在副本。
这验证不依赖旧项目输出或相邻文件。若要验证首次下载，再设置全新 GRADLE_USER_HOME；
复用已有依赖缓存的结果不能称为首次下载验证。不要默认关闭 TLS 校验，也不要提交代理密码或私钥。

产物在 build/libs/，源码提交不含成品 JAR；发行时另放 Releases。GitHub Actions 只构建，不发布。
