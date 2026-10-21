# 工作站多能力配置

更新：2026-10-08，Forge 1.20.1。无需填写槽号；生产入口由已安装适配器提供，能力声明不能自动生成生产回调。多模组固体/液体适配已入源码，仍需实际运行验证，具体归属与例外见 WORKSTATION_TRACKING_STATUS.md。

## 文件

使用 data/<namespace>/cuisinejournal/compat/*.json，允许与同一模组的食材兼容放在一个文件，也可以分文件。required_mods、priority、enabled 与食材规则一致。相同资源路径按 Minecraft 数据包优先级整文件覆盖；不同路径按 priority 小到大、资源 ID 排序。每次重载重新构建，移除文件不残留。

内置默认定义在：

- data/cuisinejournal/cuisinejournal/compat/workstations/minecraft.json
- data/cuisinejournal/cuisinejournal/compat/workstations/farmersdelight.json

同目录还含 brewinandchewin、youkai_solid、youkai_fluid、miners_delight、torchesbecomesunlight、immortalers_delight、aether、deep_aether、cataclysm、dungeonsdelight、bosses_delight 分模组定义，由 required_mods 控制加载。

## 示例（通用声明格式）

    {
      "required_mods": ["some_mod"],
      "priority": 100,
      "workstations": [{
        "block": "some_mod:barrel",
        "adapter": "auto",
        "method": "some_mod:fermentation",
        "capabilities": [
          "item_input", "fluid_input", "timed_processing",
          "item_output", "fluid_output", "container_fill",
          "menu_take", "right_click_take", "automation_extract"
        ],
        "recipe_types": ["some_mod:fermentation", "some_mod:solid_processing"]
      }]
    }

workstations 数组按方块 ID 合并。未写字段保留低优先级内容。capabilities 明确写出时替换整个能力集合，add_capabilities/remove_capabilities 可局部增删。recipe_types 明确写出时替换配方类型集合，空数组不额外限制。条目 enabled=false 禁用这个方块的工作站识别和现有生产适配；文件 enabled=false 跳过整文件。

例：高优先级包只追加右键能力：

    {"priority": 200, "workstations": [{
      "block": "some_mod:barrel",
      "add_capabilities": ["right_click_take"]
    }]}

## 能力

| ID | 含义 |
| --- | --- |
| item_input / fluid_input | 接收物品 / 液体 |
| item_output / fluid_output | 产生物品 / 液体 |
| timed_processing / fuel_processing | 等待处理 / 使用燃料 |
| direct_interaction | 玩家直接操作 |
| container_fill | 使用容器装盛 |
| menu_take / right_click_take | 菜单领取 / 右键领取 |
| direct_drop | 实际生产时掉落成品 |
| automation_extract | 可自动化提取 |

同一方块支持多个能力；不同输出位置分别记账，共用账本不表示固体与液体共享所有贡献者。

## 现有适配

adapter 可选 auto、crafting、furnace、cooking_pot、skillet、stove、cutting_board、campfire、item_recipe_container。auto 使用已有实现检测。继承原版熔炉或农夫乐事工作站的方块实体可以复用已接入基类的入口；若子类覆写生产逻辑并绕开基类，仍需核对实际入口。item_recipe_container 用于已适配的物品配方容器，不表示任意容器自动具备制作追踪。

指定 cooking_pot 不能把任意自定义方块变成厨锅：实际方块实体必须匹配已支持实现，否则拒绝接入，保留配置供排查。Brewin/妖怪归家等液体生产和领取已有专用适配；仍没有“通用任意 IItemHandler 机器完成事件”，也不保证任意第三方管道正确结算。新机器须检查实际生产机制，不能只补 JSON。

capabilities 是声明和识别数据。现有追踪已经对 item_input、item_output、menu_take、right_click_take、container_fill、direct_drop 入口设置开关；recipe_types 约束当前适配器的原生配方类型。2026-10-10 新增加速计时适配：timed_processing允许已验证计时器使用CookingSpeed倍率，移除此能力可关闭；fuel_processing仍不额外消耗燃料或tick整台机器。声明timed_processing不能自动适配未知机器。覆盖和倍率来源见CONFIGURATION_AND_COMMANDS.md。

method 当前用于方块识别/菜单提示；真实生产记录仍使用适配器确认的原生配方方式，避免改显示名后错误匹配原配方或误记成长。自定义多配方方式映射留待生产适配契约后续接入。

compatibility.extraWorkstationBlocks作为本地方法映射，优先于数据包method；数据包明确禁用方块仍生效。cuisinejournal:workstations标签只提供工作站提示，不自动证明制作追踪已支持；不再加载旧命名空间别名。

## 排查

- /cj audit recipe_workstations：按料理配方类型反查已配置工作站和配方自己提供的工作站图标，导出 config/cuisinejournal-recipe-workstations-audit.tsv。启动、数据包重载也会自动生成；读取全部实际配方类型，包含未追踪、空物品输出和读取失败的类型。
- 同时生成 config/cuisinejournal-catalogue-only-audit.tsv：明确分类收录、但缺少可读物品配方的料理。不可凭此猜测瓶装茶的流体配方关系。
- 反查包含料理数量、配方数量、配方实现类、示例配方、候选方块、证据和配置来源。一个类型可对应多个工作站；没有启用映射的已追踪类型标记 needs_workstation_mapping。存在映射也只标记 mapped_needs_runtime_verification。
- 配方的 getToastSymbol 默认返回工作台，自动审查忽略该默认图标；只有覆写该方法的配方图标作为候选。非方块物品图标保留为待审查线索，不伪造方块。
- 本轮未接 JEI/REI/EMI 工作站图标查询。报告来自服务端配方数据和数据包声明，不需要安装配方查看器，不自动修改工作站映射或贡献者规则。

- /cj debug：看向工作站方块，显示目标方块、实际检测的生产适配器、声明能力、待接入能力和来源文件。保持已支持适配器不等于已经实机验收。
- /cj audit workstations：导出 config/cuisinejournal-workstations-audit.tsv，含方块、启用状态、adapter、method、能力、配方类型、来源。该表记录声明，不冒称每个方块已实际可生产。

## 验证与发布

2026-10-08 最新 clean build 通过，WorkstationDefinitionsTest 44、ProductionLedgerTest 66、ProductionHookContractTest 158、RecipeWorkstationAuditTest 17 项通过；部署与实机状态见 CURRENT_STATUS.md。以下计数是早期开发记录。

完整离线 build 通过：WorkstationDefinitionsTest 16 项、IngredientCompatTest 47 项、ProductionLedgerTest 50 项、JournalBrowserTest 40 项和现有检查。新增同名标签回归修复 aether_redux:golden_swet_ball 导致整个料理目录为空的问题。未启动游戏验收制作人恢复和多能力工作站；运行中的实例不覆盖 JAR。

反查入口新增 RecipeWorkstationAuditTest 17 项（去重、多站、默认图标排除、空输出、错误读取、禁用映射、导出字段）。完整构建成功；实际整合包报告在加载世界后生成，本轮没有启动游戏获取现场报告。
