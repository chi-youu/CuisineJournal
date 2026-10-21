# 可复用工具

Gradle 基础构建不依赖 Python。建议 Python 3.10+，公开 Python 工具仅使用标准库。

| 工具 | 用途 |
| --- | --- |
| prepare_repository.py | 盘点公开文件、敏感检查与干净副本 |
| test_prepare_repository.py | 临时目录验证范围、检查与复制保护 |
| audit_workstation_display.py --mods <目录> --report <文件> | 只读核灶具图标/翻译 |
| audit_letsdo_hosts.py --help | 只读扫描宿主资源 |
| generate_effect_icons.ps1 -MinecraftAssets <JAR> | Windows 效果图标生成 |
| generate_nutrition_icons.ps1 -MinecraftJar <JAR> -AppleSkinJar <JAR> | Windows 营养图标生成 |

PowerShell 图像工具依赖 Windows System.Drawing。宿主资源不随仓库分发，工具结果不是实机结论。
不要未经许可再分发原始素材。其他留在本机的旧调查工具不在公开范围，可能有机器路径或实例操作。
