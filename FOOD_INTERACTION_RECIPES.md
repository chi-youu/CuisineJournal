# 放置食物获取配方展示

2026-10-08 设计与实现记录。Forge 1.20.1 / Java 17 / 已安装 Lychee 5.2.1。

## 已确认范围

2026-10-08 22:35 Lychee预览点击/名称修复：用户实测列巴/战争面包等预览左键不跳本体，霜冠慕斯预览显示block语言键。核对5.2.1确认getMatchedItemStacks仅服务配方索引，BaseJEICategory.clickBlock另直接调用Block.asItem；特殊盾牌式放置物品为AIR故点击无反应。新增client-only LycheeJeiBlockClickMixin精确重定向该调用到共享source映射，沿用原左键OUTPUT/右键INPUT焦点，不引用百味纪UI。getTooltips直接Block.getName导致未翻译键，新增client-only名称回退：已有方块翻译保留，缺失时读取对应整块物品getHoverName（资源包翻译/语言选择照常）。不硬编码中文、不改第三方语言资源。独立/内置两份构建均有补丁，双装仍按Plugin选独立版；没JEI不加载JEI目标Mixin。

build-interaction-preview-fix.log完整build成功，宿主契约33项（包含两处唯一Redirect调用与client配置），展示23项、本体关联24项、制作165项/账本71项与双包隔离通过。两JAR已重建22:35，未部署/实测修后点击。用户截图证明原展示配方可以加载，不再把它们整体列为未启动；本轮只补预览与名称。

2026-10-08 21:59 切片链接本体（用户确认）：百味纪在料理目录重建时从生成的cutting获取展示及单输入砧板切片关系提取来源。没有独立烹饪路线、来源唯一且本体有真实制作配方时自动关联；多本体、自身循环、链循环、普通原料加工不猜测合并。下界披萨虽无需刀也标记切片关系。寿司拼盘仍排除。

客户端DishView新增preparationTarget（协议13，客户端/服务端须同步更新）。详情/命令/API制作次数及制作日期读本体，食用/分餐记录和图标、属性保留切片；DISH熟练点写到本体，SUBCATEGORY仍按实际小类且同小类共享本体次数去重。百味纪查制作配方跳本体，详情原料/配方ID读取本体真实配方，同时保留切片获取展示。JEI自身查询切片不重定向。

非食品本体（战争面包等）有真实制作配方时生成后台preparationOnly入口，允许现有真实工作站计制作；不进入visible targets/entries、不新增整块料理卡片。存档仍分别保存本体制作记录和切片食用记录，不搬移/重写旧切片制作历史，关联后的制作显示以本体为准。新增关联不补发历史XP或制作次数，切片/分装处理不成为新制作。

验证：build-portion-origin.log完整构建成功，PortionOriginsTest24项含多源/循环/独立路线排除、客户端读取/日期/消费/去重/增量、协议字段、服务器存档及隐藏非食品本体。既有展示23项、宿主29项、制作注入165项等通过。build/libs两份JAR重建，未覆盖实例或实机验收本轮。

2026-10-08 21:33 新增截图缺口：下界披萨 NetherPizzaBlock 直接给 NETHER_PIZZA_SLICE 并递减slices，不检查刀具；展示空手（minecraft:air）取一份，不伪造刀切。传奇珍馐 CommonCakeBlock（含苔石蛋糕）以公开sliceItem读取产物，工具为 farmersdelight:tools/knives 或 forge:tools/knives，按整个实现家族接入。永恒蛋糕切片实际来自神秘遗物 BlockCosmicCake 与 Enigmatic Delicacy EtheriumMachete.onCakeInteraction 联动，只接受以太砍刀 ETHERIUM_MACHETE，bites<6；扫描读取两项实际注册字段，不用普通刀标签代替。湮灭魔眼派/仰望烈空派原本就继承FD PieBlock，无需重复适配，已包含上一轮候选列表格式修复。

验证：build-interaction-more-slices.log build --offline成功；展示23项、宿主接口/行为29项，双产物隔离与既有制作165项等通过。两份JAR已重新生成（21:33），未部署或游戏内验证新显示。未知覆写仍保守跳过，已有配方仍阻止额外生成，实际互动和计次不修改。

2026-10-08 21:25 现场反馈修复：旧 audit 显示普通碗装已生成37条，但刀/斧候选全部因直接写 forge:compound 被拒绝。现输入支持 JsonElement，多工具以 item_in:[[候选1,候选2,...]] 表示同一只手的替代物；Lychee 外层数组为双手，不能用扁平候选数组。已核对安装5.2.1的 Serializer 对每只手调用 parseIngredientOrAir，再委托Forge Ingredient.fromJson。移除自动 comment 及其语言资源，JEI不再额外显示“分装或切片不计作烹饪”备注，ghost及成长隔离机制不变。

本轮新增确认：樱途旅事填馅嗅探兽蛋/嗅探兽蛋浓汤仅在原 FeastBlock.takeServing 成功后处理空蛋壳，允许继承读取；竹筒饭额外要求先斧开，展示匹配 open=true 的实际状态；星喵 BaseFeastBlock 是独立复制的同机制基类，已接入整族而非只写叉烧饭ID。玉黍烙 ZeaPancakeBLock 不继承 PieBlock/StackedFoodBlock，核对cut实际产物ZEA_PANCAKE_SLICE及ItemUtils.isKnives后补独立读取。战争面包/烤馕/列巴/霜冠慕斯之前属于生成错误，本轮统一工具格式修复，不改变已有配方跳过规则。其它未知覆写仍needs_review。

验证：build-interaction-missing-foods.log，clean build --offline成功；InteractionRecipeTest20项、InteractionHostContractTest21项已安装API/读取行为、双JAR隔离验证及165项制作注入契约通过。曾尝试离线Bootstrap真实Ingredient解析，Forge缺运行时事件变换而无法启动，已撤下不适用测试；替换为解析器字节码契约及嵌套工具JSON回归，不能宣称完整Lychee运行测试通过。本轮未部署实例，需新包游戏确认。

- 同一份源码提供百味纪内置功能和独立 food_interaction_recipes 模组。独立版以 Lychee 为必需前置；百味纪缺 Lychee 时停用生成器，其它功能不受影响。两者同装时独立版负责生成，独立产物重定位共享源码，避免 Forge 分包冲突。
- 只为没有有效获取配方的食品补充 Lychee ghost 展示配方。已存在的工作台、烹饪、砧板等配方都能阻止重复生成；每次重载从当前实际配方重新判断，不用历史导出表决定运行行为。
- 寿司拼盘不生成额外获取配方。妖怪归家厨锅/动态汤品已存在配方兼容，本轮不处理。
- 不执行方块互动，不修改容器消耗、工具耐久、星级继承、制作人或成长计次。
- 已知实现适配一次覆盖同类注册方块：农夫乐事 FeastBlock/PieBlock；千古乐事 StackedFoodBlock。继承但覆写右键/分装/切片方法的类先标记 needs_review 并跳过，允许用明确数据包规则补充；不猜测任意 Java 代码。
- 分装识别容器取成品 crafting remaining item，不能写死碗。切片识别真实刀/斧条件。千古乐事战争面包/熔烬烤馕为刀，古苜蓿大列巴为斧，巨型蛋挞已有砧板配方应跳过。

## 实现顺序

1. 共享只读扫描、稳定配方 ID、现有配方去重、规则覆盖、报告。
2. 配方重载完成时生成 ghost 进服务器 RecipeManager，沿用原版网络同步和 Lychee 的 JEI 注册。
3. 百味纪单次通用读取展示产物/来源，不把工具或容器当作食材，不把展示获取当烹饪。
4. 独立 JAR 打包检查、专门回归及全工程构建；游戏内验证单装/双装、JEI 输入输出查询、重载撤销、原互动完全不变。

## 可行性结论

pass-with-notes：上述范围和职责已明确，可以实现。反射调用仅限核对过的只读输出/份数/工具判定；没有静态编译前置。未知实现报告为待适配，不执行右键模拟。游戏内 JEI 和多人同步须作为后续实测证据，不用编译代替。

## 数据包与查看

规则位置：data/<namespace>/food_interaction_recipes/compat/*.json。相同文件资源路径按 Minecraft 数据包优先级整文件覆盖，不同文件按资源 ID 排序，后面的同方块规则替换前面的规则；删除文件后重载重新构建，无持久覆盖残留。

    {
      "required_mods": ["some_mod"],
      "blocks": [
        {"block": "some_mod:excluded_feast", "enabled": false},
        {"block": "some_mod:special_feast", "output": "some_mod:food_portion",
         "count": 1, "input": {"item": "minecraft:bowl"},
         "state": {"servings": "4"}, "kind": "serving"}
      ]
    }

明确补充规则也遵守“产物已有配方就跳过”，不强制显示重复获取路径。规则不执行互动；不得把新增不存在的获取方式写成事实。

识别报告：config/food-interaction-recipes-audit.tsv，记录 candidate/generated/skipped_existing_recipe/needs_review/failed_generation 等状态。通过 Minecraft /reload 刷新；新增或删除食物模组仍应重启游戏。生成 ID 使用 food_interaction_recipes 命名空间，稳定包含方块、产物和代表份数状态；不写入世界存档。

百味纪的详情请求会附带这类配方 ID 与来源整块食物图标，沿用现有点击 JEI 入口。获取关系单独保存在 CulinaryRegistry.interactionRecipes，不进入普通 MealEntry.recipes/ingredients/methods，因此碗、刀、斧不会作为烹饪原料，新增展示关系不授予制作次数。缺少其它配方但符合料理资格的切片可以补入图鉴；整块战争面包仍不因此成为料理。

## 构建与使用

2026-10-08 最终 build --offline 通过（build-interaction-recipes.log）：InteractionRecipeTest 15 项、InteractionHostContractTest 13项已安装 JAR API、双产物包隔离/独立入口/Mixin manifest 验证通过；既有制作注入165项、账本71项、浏览44项和refmap检查通过。当前没有运行目标游戏或生成实例现场识别报告，实际覆盖数量及JEI显示不得以这些静态检查代替。

- build/libs/cuisinejournal-0.1.0.jar：内置功能，有 Lychee 5.2.1 时启用。
- build/libs/food-interaction-recipes-0.1.0.jar：独立模组，要求 Lychee 5.2.1（5.x）。不依赖百味纪。
- 使用百味纪时不需要再装独立版；双装时事件与可选 Lychee Mixin 均由独立版负责。共享源码在独立构建时重定位到私有包，避免分包冲突；注册逻辑与数据规则共用同一源码。
- 未自动部署 JAR、启动目标游戏或宣称 JEI 实机验收通过。

实测关注：战争面包/熔烬烤馕切片显示刀，大列巴切片显示斧；巨型蛋挞切片已有砧板配方不重复生成；寿司拼盘不新增；拿错工具的原互动不变；/reload 不累积重复配方；独立单装、百味纪单装、两者同装及百味纪无 Lychee 均需启动确认。
