# 百味纪代码架构

面向代码维护；功能见玩家全览，构建见docs/BUILDING.md。实现、自动检查、实机验证分别记录。

## 模块职责

| 模块 | 拥有的内容 | 不应承担 |
| --- | --- | --- |
| recipe | 加载后目录、配方/份额关系、分类/风味定义与查询索引 | 网络传输、客户端缓存、直接发制作奖励 |
| culinary/mastery/progression | 玩家记录、熟练度、品质印记、制作结算与成长 | 凭查看配方冒充真实制作 |
| tracking | 实际投料、产出、领取、贡献账本与工作站上下文 | 以附近玩家或背包差分推断制作 |
| food | 食用准入、模式/营养、槽位、风味库存、宿主食物属性 | 全局编辑锁、宿主原生药水时长接管 |
| data | 文件持久化、编辑检查点、协调租约与恢复 | GUI状态、把第三方副作用承诺为全局原子 |
| network/forge | 权威请求验证、同步、事件与平台接线 | 客户端请求直接授予制作或奖励 |
| client | 图鉴、HUD、提示、编辑草稿、只读规则视图 | 修改服务端进度或直接写服务器文件 |
| compat | 可选宿主识别/反射、统一规则与注入预检 | 将特定实例或安装批次作为运行身份 |
| challenge/milestone/skills | 从已有权威事件推进目标与技能 | 另造一套制作计数或解锁迁移系统 |

独立food-interaction-recipes只展示交互配方，拥有独立产物和依赖。展示支持不等于生产归属支持。

## 允许依赖

这里描述真实边界，不把目前存在的领域协作伪装成无环分层。已有双向边需限制到命名入口。

| 调用方 | 允许方向与用途 | 禁止或需收敛的方向 |
| --- | --- | --- |
| recipe | config/compat；food只读属性与份额；tracking只读工作站定义 | network、client；同步或奖励副作用 |
| culinary/progression | recipe目录/分类查询，mastery规则，权威成长事件 | client；让目录成为第二份玩家档案 |
| tracking | recipe只读身份；culinary/progression真实结算；food变体/生产身份工具 | GUI、把显示查询当领取 |
| food | config及只读目录；tracking只限已命名放置/生产桥 | 通用规则直接读ClientJournalState |
| data协调器 | config/recipe/food/tracking的检查点与恢复入口 | 客户端类、报文和渲染 |
| network/forge | 协调器、领域服务、报文codec与同步 | 任意条目绕开租约/权限/版本检查 |
| client | network投影和命令、只读领域工具，启动注入ClientFoodRuleView | 把ClientLevel传回通用层的直接字段读取 |
| compat通用工具 | 标准库、ASM、Minecraft/Forge宿主契约 | 借工作站布局类提供跨模块反射 |

culinary与recipe有成长查询协作；food与tracking有真实食用/生产桥。此类边不等于整个包允许任意互调。
LayerDependenciesTest约束recipe不指向network/client，并使用ASM SimpleVerifier跟踪通用方法的类型流。
字段/调用返回值经过局部变量、参数、数组及分支合并后，若需要客户端子类型的继承关系才能校验则拒绝；继承信息只读类文件，不反射加载客户端类。
纯客户端包、客户端Mixin/Forge桥及HUD渲染器不按通用类扫描，另防通用类直接链接该HUD；通用快照类仍扫描。
该护栏不代替Forge变换后的专服实际启动，也不是对任意第三方动态字节码的证明。

## 核心数据流

1. 标签/配方加载后建立目录、食材反向索引、份额和风味关系。
2. 实际投料写ProductionLedger，宿主完成产生批次，实际领取按既定宿主时机结算。
3. 成长服务写玩家档案，MealStamp为成品保存品质与制作者；切份和存储继承不重抽。
4. 食用读取真实ItemStack与模式/技能作准入，宿主原生动作在作用域内执行。
5. 只有风味效果进入有限库存；原生药水仍由宿主负责。客户端只显示权威投影。

各宿主完成/交付时机不同，不为重构强行合并。事务提交后的观察者或同步失败不倒写已经发出的成长事件。

## 公共入口

| 需求 | 入口 |
| --- | --- |
| 可选类族/反射 | compat/HostClasses、HostReflection；读取不依赖工作站布局 |
| 工作站布局 | tracking/VerifiedWorkstations、LetsDoStations、ItemRecipeWorkstations |
| 共用实际来源 | food/IngredientProvenance |
| 变体身份和键策略 | food/FoodVariantIdentity：领取、盘子切份、pending输出和忽略记账比较各有明确集合 |
| 放置完整变体 | tracking/PlacedFoodVariants |
| 菜单/加速上下文 | tracking/CookingContextTracker；不负责真实制作归属 |
| 原生效果预览 | food/ModFoodCompat；特定算法留宿主类，不进入库存 |
| 客户端规则读取 | ClientFoodRuleView，由client/JournalFoodRuleView启动时注入 |
| 客户端世界读取 | client/ClientWorldView返回通用Level，避免通用类直接接触ClientLevel校验 |
| 外部食物API | CuisineFoodAPI，CYS确有调用，保留其公开签名 |

## 操作边界

CatalogueEditCoordinator是唯一忙状态与租约所有者，EDIT/EXPORT/UNIFICATION是阶段，不是三把独立锁。
每份租约具有对象身份和会话代次；停服使旧租约失效，迟到回调不能解锁新操作。
LocalEditSnapshot统一保存本地文件与可恢复运行层，checkpoint/apply/rollback共用恢复策略。
命令和方案载入使用apply传入变更、重建与成功同步；其余请求共享同一租约及检查点。
导出包交换、统一包来源回滚仍由各自宿主事务处理，不能拿普通JSON快照替代异步资源系统事务。
编辑乐观版本继续由EditVersions处理，和独占操作租约是不同职责。
原版标签重载触发的目录重建也取得租约；忙时只留一次待重建，当前租约释放后读最终资源状态补做，停服清待处理请求。
导出在清理备份前显式验证目录重建，失败仍进入原导出回滚；统一操作保自己的提交重建。
此规则协调百味纪的目录重建，不拦截Minecraft或第三方自身的资源重载、数据监听器副作用。

ProductionFrames注册全部tracking临时线程状态，tick末/停服在当前线程统一清理。
拥有设备身份的结束操作按FrameStacks/ProductionFrames.take匹配，不弹无关外层；被跳过的内层用无效帧隔离外层。
能包住同步宿主动作时使用run/finally；HEAD/RETURN注入若宿主中途抛错，仍依赖tick末兜底，不能声称全宿主异常严格事务。

## 宿主版本降级

CuisineMixinPlugin只预检可选宿主的Pseudo适配，不关闭原版/Forge核心注入校验。
方法、回调参数、Shadow字段或INVOKE/FIELD调用点不匹配时跳过该完整Mixin并警告，避免只装一半begin/end。
宿主继续原生运行，但被跳过的制作追踪/品质/加速不算覆盖；不使用全局require=0把失败静默隐藏。
预检不是未来任意版本兼容保证，未知注入类型/其他模组变换仍需构建及实际启动验证。

## 命名与长期契约

运行类与资源按宿主/职责命名，B00x仅调查/回归分组。CuisineJournal是入口，Culinary是成长记录，Meal是料理记录，Food是真实食用。
自有NBT根键cuisinejournal:<snake_case>，SavedData文件名cuisinejournal_<用途>；宿主自己的quality/BrewLevel/Effects/HotUntil不改。
首次正式发布后数据/协议修改需版本化或明确迁移；当前不承诺旧测试档兼容。许可证与真实署名保留。
Mixin独立根包为com.chi__you.cuisinejournal.mixin，同一配置以相对子包名注册，注册顺序不随目录分组改变。

| 子包 | 负责 | 数量 |
| --- | --- | --- |
| production | 投料、完成、领取、归属、份额与存放继承 | 105 |
| speed | 进度、等待及厨具加速 | 49 |
| unification | 物品统一、数据包来源、配方及掉落纠错 | 17 |
| food | 准入、营养、效果、食用生命周期与访问器 | 33 |
| client | 客户端HUD、提示、语言和界面跳转；仅client数组注册 | 13 |

少数森罗处理站在同一对回调里同时追踪产出和修改进度，按事务所有者归production，不为目录迁移拆开其时机。
客户端创造栏统一访问器仍归client，职责与加载侧隔离优先；独立交互配方模块的Mixin不参与本项目迁移。
跨组访问器必须显式import，业务类按实际类型引用；注册路径、源码路径、编译身份与正式JAR无旧包残留均有检查。
目录分组不能替代宿主容错或实际启动验证。
不按体积拆大类，不为小模组造空注册框架；性能先测基线，再改实现。

## 构建与验证

build.gradle负责正式/独立产物；gradle/verification.gradle显式列回归。测试源码不进入运行JAR。
源码卫生、模块边、可选注入契约、帧作用域、协调租约各有测试；未编译/未运行的新增测试不能当作通过。
配置损坏/同步拒绝/投递失败的日志和管理员诊断工具是必要功能，不按debug名字删除。
本机历史资料与构建日志不提交；当前构建/安装结论见维护者状态文件，公开源码的基础构建不依赖它。
