# 百味纪 / Cuisine Journal

面向多食物模组组合的料理图鉴、烹饪成长与食物玩法模组。

当前为发布前测试版本，支持 **Minecraft 1.20.1 / Forge 47.x / Java 17**。
已有适配不表示所有组合、自动化或多人路径均已实测。开发测试版存档不承诺兼容。

## 功能

- 料理与食材图鉴、配方与灶具、发现记录和标签整理。
- 逐料理或小类熟练度、星级、完美料理、制作人和切份继承。
- 默认使用原版饥饿与饱和玩法，可选开启 Valheim 食物槽、食物生命与恢复。
- 双模式技能树、今日料理、挑战、里程碑、徽章和菜谱追踪。
- 可编辑分类、风味、方案及数据包导出。
- 可选物品统一模式，按明确规则接入配方、掉落与种植。

默认分类和风味为空；提供“基础料理分类”和“手配风味”供管理员按需载入。
整合包可通过 [`cuisinejournal:disabled_items`](INGREDIENT_COMPAT_DATAPACK.md#整合包禁用物品) 标签过滤已停用食物，并阻止自动交互展示重新补出其获取关系。
原生食物药水由宿主处理，有限效果库存只管理风味效果。

## 安装

百味纪需要 Forge 和 [Pufferfish's Skills](https://modrinth.com/mod/skills)（1.20 Forge，`0.18.3 ≤ 版本 < 0.20`）。
客户端与服务端均需安装匹配版本的百味纪及必需依赖。
建议同时安装 Lychee（Forge 1.20.1，5.2.1至6.0之前的版本）；百味纪代码层不强制依赖它。
Farmer's Delight、JEI、JEED 和其他食物模组按需安装，不是强制前置。
同时安装 sol_valheim 时，百味纪食物模式回退原版，避免冲突。
第三方模组仍须满足各自前置要求。升级测试版前备份存档和配置。

工程输出：

- cuisinejournal-<version>.jar：百味纪，已包含交互配方展示。
- food-interaction-recipes-<version>.jar：独立交互配方展示，不含百味纪玩法。

安装百味纪时不必再装独立展示产物。

## 构建

安装 JDK 17，设置 JAVA_HOME，在仓库根目录执行：

```powershell
.\gradlew.bat build
```

Linux/macOS：`bash ./gradlew build`。首次构建需要联网；不依赖本机 libs/ 或相邻 CYS 工程。
产物在 build/libs/。[构建说明](docs/BUILDING.md)包含固定依赖版本与测试边界。

## 文档

- [功能全览](百味纪功能全览.md)
- [配置与命令](CONFIGURATION_AND_COMMANDS.md)
- [文档导航](docs/README.md)
- [仓库提交范围](docs/REPOSITORY_PREPARATION.md)

## 反馈

请提供 Minecraft/Forge/百味纪版本、相关模组版本、最短复现步骤和截图。
崩溃或同步错误可附相关日志片段，公开前去掉令牌、服务器地址和私人信息。
无需先完成整套兼容测试才反馈；单一路径通过也不代表全部组合均通过。

## 许可

公开源码不等于授予任意再分发许可。新增内容沿用保留所有权利，未替作者选择开源许可证。
上游 Meal Mastery 的 MIT 许可及其他来源权利保持不变，详见[许可范围](LICENSE.md)和[第三方声明](THIRD_PARTY_NOTICES.md)。
