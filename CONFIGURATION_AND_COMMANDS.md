# 百味纪配置、命令与数据契约

契约整理：2026-10-21；挑战字段/设置来源等已按当前源码修正。本文是按需参考，不是状态流水账；当前协议、构建与部署唯一见 CURRENT_STATUS.md。

百味纪Mixin按职责放com.chi__you.cuisinejournal.mixin.production/speed/unification/food/client；同一mixins.cuisinejournal.json以相对子包名注册，保原顺序、宿主目标、注入条件与refmap资源名。客户端组仅client数组，不改NBT/配置/报文/技能/配方ID；独立交互展示模块保持原Mixin结构。引用和契约测试按新路径读取，正式JAR禁止旧food/mixin平铺包残留。
这份文档只说明有效契约；未实现事项统一见 CUISINEJOURNAL_PENDING.md。重写前完整参考已归档。

## 审计后的保护行为

- 客户端Envelope仅接受明确的请求ID/方向，服务端同步ID不能反向提交；入站payload最多1MiB，集合计数先校验非负、类型上限与剩余字节再分配，尾随垃圾拒绝。Netty入队前与处理端均有限流；单玩家共享24令牌/每秒补12，Profile至少2秒间隔、详情/预览75ms，旧StartChallenge不触发重算。正常主动同步不按客户端请求限额扣费。新增独立SyncFailure通知超大/编码失败，当前协议号只查CURRENT_STATUS验证块。
- cuisinejournal-categories/ingredients/flavors三份本地JSON读坏时生成唯一.unreadable-UUID.bak恢复副本，保留可读状态并禁写，修好原文件后重载才解锁；不能再用空层覆盖原文。分类/风味方案须有正确的内容对象，缺字段或坏条目不等于“载入空方案成功”；内置默认是明确的空方案。
- 注册表构建失败恢复相关运行层及相关本地文件；重建/默认方案、风味修改、方案载入、分类命令/reload和模式设置均保护旧状态，恢复写盘失败明确记录且运行配置先恢复。第三方回调/世界资源系统的任意副作用及磁盘完全不可写不作全局原子承诺，逐项复核见审计附录C。旧配置升级保手调风味/营养，不误当首次新配置重套模式默认。
- 玩家档案退出写盘失败保留内存脏数据重试，重连复用；停服最终失败尝试写UUID.unsaved-唯一ID.dat供手动恢复，恢复文件不是自动替换正常档案。更高schema拒绝覆盖；损坏档案.corrupt副本不覆盖既有恢复副本。磁盘完全不可写只能报错，不能承诺永不丢失。服务端/cj reload不再连带重读客户端TOML，TOML写入使用原子替换。
- 普通食物睡眠扣时可降至0，不再有把不足60秒的记录拉回60秒的行为；在线重载/容量API下调裁剪最新超额记录。公共锅取份不自动成为制作者，非贡献者代捡保pending；流体装瓶已扣部分不在帧结束重复扣。
- 图鉴多目标挑战卡自适应高度，超额进度显示实数；确认招牌后退出/切页会复位。弹窗15秒超时保留草稿并提示可能仍在处理、允许退出，不自动重发破坏性请求；结果按请求ID保存，方案左列表可滚动。游戏内观感/迟到响应仍待验收。客户端设置250ms合并写盘、关页强制保存，不延迟运行选项生效。
- 导出/统一设置异步操作绑定服务端会话，停服主动复位busy，旧世界回调不提交新世界设置。公开工作站标签包括既有明确的工作站定义，成员均optional；不代表任意同类新模组已兼容。regenSpeedModifier仍是回血间隔倍率，提示的每秒回血按实际五tick间隔换算，不反转旧配置含义。
- 编辑独占状态现在归data/CatalogueEditCoordinator：EDIT/EXPORT/UNIFICATION是同一租约的阶段；旧EditVersions.busy和两个Handler的busy查询只作视图，不各保布尔锁。分类/风味/设置/方案/命令用同一租约与checkpoint/rollback；导出包交换、统一包来源的异步恢复仍保专用事务，不承诺第三方副作用全局原子。停服使旧Lease失效，过期close不能解锁新操作。
- tracking临时线程状态统一注册ProductionFrames，服务器tick末及停服清当前线程全部已注册帧；end按设备身份匹配，妖怪料理被跳过的begin压无效帧，防end误弹外层。能包装宿主调用的入口用finally；HEAD/RETURN若宿主抛错仍需tick末兜底，不把它称为所有异常即时恢复。
- 可选宿主Mixin新增CuisineMixinPlugin预检：Pseudo宿主方法/回调/字段/调用点不符时跳过完整适配并WARN，保原生运行；原版/Forge核心不宽松。厨房1.3.0缺applyRecipe/applyFlexRecipe、千古1.1.9温泉用spawnItemEntity而非splitIntoStacks已读JAR确认。源码与当前/旧宿主专项已编译检查通过，但未实际启动，不能宣称所有旧版可用。

## 图鉴大数据同步

Forge服务端下发统一走PacketFragments：每片256KiB，带包ID、传输ID、逻辑总字节数与片序号；整包收齐后解码，不以半包覆盖缓存。单逻辑包最多32MiB，客户端同时最多8批、总预留64MiB，30秒未完成回收；同类型新批次取代旧批次，重复/迟到片不覆盖新数据，断线清空。普通客户端请求仍沿原Envelope，分片消息只允许PLAY_TO_CLIENT。无须修改Minecraft的1MiB上限；总量安全上限不是无限内容承诺。当前协议号见CURRENT_STATUS。

静态目录JournalSyncPackets.Directory按当前CulinaryRegistry实例缓存：分类结构、食材/标签成员、类型、料理关联、风味绑定等只在首次连接、注册表重建或显式故障恢复时发送。料理基础SyncCatalogue也不在每次开图鉴时重复发。目录不包含首个玩家的发现/使用记录；分类/风味编辑和数据重载沿已有注册表重建失效缓存。

首次或故障恢复由JournalSyncServer同一入口依次发送SyncCatalogue、Directory、完整Progress；技能/挑战先触发sendExtras也必须发齐，不以附加目录的玩家基线推断料理目录已经发送。客户端附加目录就绪但基础料理目录缺失时仍保留30秒恢复计时，恢复请求重发两类目录，避免所有页面永久卡在读取状态。

玩家JournalSyncPackets.Progress带目录generation和递增sequence：首次为完整进度，之后只发变化的食材记录和分类统计，不重发成员/配方图；挑战/灶具/最多40条活动保留小型完整快照，无变化时不发送。序列缺口请求恢复，不把缺失增量当空列表；首份目录与对应完整进度一起提交，新版本收齐前保留旧列表。连接退出及服务端停止清理基线。

标签的tagSources/tagUses详细来源和逐配方acceptedItems从首份目录移出；选中标签时请求TagRequest，服务端校验当前目录版本及真实标签后下发TagDetails，同样受分片保护。列表搜索/筛选所需members、tagIds、meals仍立即可用，不截断候选；客户端缓存详情直到目录替换，迟到旧版本响应丢弃。详情请求最多重试3次、间隔3秒；服务端每玩家50毫秒节流。

标签发现与用过不等于成员食材进度：TagIngredientProgress从registry.tagUses（显式引用真实标签的料理关联，含显示合并）取得料理，只汇总MealRecord.prepared；按preparationTarget去重，同菜多格/多标签/份额链接只算一次。prepared>0才发现，日期取相关菜first/lastPreparedDay，灶具取相应制作方式；拿到/吃过成员或用于其他菜均不影响标签。次数沿原料理制作数量口径，不是输入格数之和；批量4份记4。原食材成员自身的发现/使用不改，标签内成员发现数量仍反映食材自身状态。初始与增量同步共用此规则，客户端直接读服务器字段，不重算成员之和。复用旧制作记录即时重算，无存档/协议结构变更；当前为料理级统计，多配方料理历史未保存实际recipe ID，不声称逐配方分支次数精确。下方“参与料理已发现X/Y”仍按料理发现规则计数，可因获得/食用已发现但尚未制作而与标签自身发现不同。

初次未收齐显示“正在同步图鉴”，失败显示同步未完成；目录故障最多自动重试3次、间隔3秒，恢复请求服务端每玩家2秒节流，重新打开允许再次尝试。已有旧数据不会因失败清空。仅增加空态反馈，不调整图鉴布局，不修改料理/发现存档。

## 配置文件

| 文件 | 职责/生效 |
| --- | --- |
| config/cuisinejournal-common.toml | Forge公共配置：foodSystem=valheim/vanilla、槽位、生命/回血/时间、preserveNativeFoodEffects、酒类ID；遵循Forge加载，重进/重启核对 |
| config/cuisinejournal-server.toml | 自定义TOML：经验、星级、发现、挑战、共享、方案、开关、兼容；/cj reload，多人由服务端决定 |
| config/cuisinejournal-client.toml | 本机图鉴、图标、等级经验HUD、通知；GUI即时保存，手改宜关游戏 |
| config/cuisinejournal-categories.json | 游戏内分类覆盖，高于包；手改后/cj reload |
| config/cuisinejournal-ingredients.json | 食材/标签风味绑定与兼容覆盖；/cj reload |
| config/cuisinejournal-flavors.json | 本地风味预设overrides，高于数据包；/cj reload |
| config/cuisinejournal/schemes/ | flavor/category个人方案和current.json，实例共用；保存不是导出 |
| 存档/datapacks/cuisinejournal_custom/ | 游戏导出的方案数据包，按kind替换并异步启用/reload，须等待成功结果 |
| config/cuisinejournal-*-audit.tsv | 审查输出，不是输入配置或配方 |

Minecraft配方/标签变更用/reload，/cj reload不能替代。旧server/client JSON兼容迁TOML并保留备份；未知废弃字段读入忽略，正常写回去掉。

分类items当前也会显式补收录可食物品，即使没有普通制作配方；不是只管已收录料理的排布。本次内置simple_meals名单已清37项原始/纯加工原料，未改变这一通用契约。旧导出或外部数据包仍点名这些原料时需另核覆盖来源，单改内置资源不改外部包。制作判定仍沿可食产物、配置排除与拆箱过滤；processingRecipeTypes将纯加工产物留作食材，独立切片原型关联另行保留。食材存储循环隐藏与料理排除是不同机制，隐藏食材不等于料理黑名单。

## 玩法开关和模式组合

标签自动展示合并：完整原始成员集合相同（已有合并组取其真实标签并集）的条目参与自动比较，优先保已有手动组ID，再沿命名/forge及ID顺序选择。显式keep、名称/类型/风味配置冲突保留分开；成员子集或因隐藏/物品别名才看似相同不合并，原始配方接受范围不扩大。空的真实标签别名不显示，未来有成员仍能参与；不是从第三方数据包删除标签。

| Server键 | 当前行为 |
| --- | --- |
| mastery.enabled | 星级总开关；停新增熟练/完美判定，食物与风味/显示中性二星，已有记录/NBT保留 |
| mastery.scope | 默认DISH逐食物，仍可选SUBCATEGORY；切换不删历史，两套门槛独立。现有显式配置不被新默认强改 |
| foodEffects.enabled | 默认true，不随Valheim/Vanilla切换改写；独立控制原生/配置效果及tooltip，不管风味效果或外部药水。设置页普通开关、不再显示随模式，旧RESET_POTIONS恢复true。Valheim兼容旧common的preserveNativeFoodEffects显式限制；GUI开启/恢复会解除并保存，同步快照。已有false配置不强改 |
| flavors.enabled | 风味总开关，关闭继承、药水/等级、生命回血时间修正；配置/编辑/方案不删 |
| nutritionDisplay.enabled | 营养显示总开关，百味纪图标行/Vanilla详情、AppleSkin附加HUD、Obscure API营养图标；AppleSkin物品营养tooltip及Quark鸡腿图标恒隐藏，不改真实营养 |
| discovery.discoverOnObtain | 默认true，每秒检查背包获得目录料理；独立叠加mode，不给制作XP/熟练/计数 |
| discovery.mode | 游戏设置仅COOK_RECIPE默认、EAT_OUTPUT、OBTAIN_OUTPUT；查看详情发现已移除，旧VIEW_RECIPE迁制作。ALWAYS_VISIBLE仅文件配置，自动永久写全发现，切回不会撤销，不允许设置网络开启 |
| discovery.shared | PERSONAL默认关共享，TEAM原版scoreboard队伍，SERVER全服；只有发现，离线补齐、退队不撤历史 |
| discovery.hideUndiscoveredDetails | 默认false，开启隐藏未发现料理详情属性/风味/配方；物品提示不受影响 |

有效模式第一次应用或改变时：Valheim风味开/营养关，Vanilla风味关/营养开；原生药水独立默认开，首次应用和模式切换均不改已有选择。foodModeDefaults.lastAppliedMode确保同模式重载/重启保留风味/营养手动覆盖，不每tick强制改写。本轮暂停制作默认风味/分类内容，不删系统或用户方案。

两种“百味纪默认”均为空，不内置自动生效的分类/风味。可选方案位于data/cuisinejournal/cuisinejournal/schemes/：category/basic_categories（基础料理分类，4大类/23小类/304归属）和flavor/handcrafted_flavors（手配风味，45定义/41物品绑定/26标签绑定）。保原ID/阶段/数值；前者保其他饮品自动兜底。新配置schemes.defaultFlavorScheme/defaultCategoryScheme仍为空，不自动载入；已有实例配置与外部启用数据包不被本次源码更新清除。
common.foodSystem是唯一食物模式入口；安装sol_valheim时回退Vanilla。服务器设置通过CuisineJournalServer.setFoodSystem切换、重建并同步，已接管理员玩法设置；首发前移除旧experimentalRuntime隐藏开关。

百味纪侧栏技能树入口保存当前JournalScreen实例作为本次访问返回目标，Skills界面Esc关闭时回该实例，保留页签、选择和滚动；独立K键、宿主命令打开不建立返回路由。请求10秒未开/已离开原界面、跳到其他界面、死亡或断线时丢弃路由；模式切换重开Skills仍沿原返回目标。不改网络包/协议、存档或宿主技能树JAR。
营养tooltip固定优先级：有Obscure API仅用它的饥饿/饱和行，无Obscure用百味纪自有图标行；安装AppleSkin/Diet等不再阻止百味纪回退。AppleSkin的shouldShowTooltip恒false、Quark的makeTooltip第一个布尔参数（鸡腿图标）恒false，保第二参数控制的“小吃/全餐”等文字。营养总开关关闭时Obscure及CJ最终营养行均隐藏，不强制打开第三方自有配置，不修改其文件；AppleSkin HUD逻辑不受此tooltip优先级变化。自有图标行放在星级下方、不要求Shift。图标为原版半鸡腿，以及AppleSkin饱和叠层改琥珀金/黑描边版本；独立nutrition字体11px/ascent9、两个图标各占12px（半鸡腿11+1补宽），数值紧随字形、两组无额外空格，饱和系数乘100截断取整数（0.4→40%），零值组不显示。详情栏使用同一format。两种芭菲在有Obscure API时只扩展其buildIcons食物显示判定，直接复用原图标行；没有Obscure时保留独立兜底。资源可用性不依赖第三方，生成脚本tools/generate_nutrition_icons.ps1，来源/Unlicense见assets/cuisinejournal/NUTRITION_ICON_SOURCES.txt；不含Obscure代码/贴图，不加硬依赖、不修改物品isEdible。
药水关闭对绕过通用addEffect/瞬时食用上下文的第三方程序不保证覆盖；同名冻结库存等级也不保证能拆分风味与原生贡献。

## 数值与成长

### 双模式技能树

Skills数据包入口config.json只写version/categories，不得放模组配置专用的show_warnings；0.19.2将其视为Unused field并拒绝整个数据包。打开入口在树缺失时会给出加载失败提示。测试需先走PackConfig解析，再解析类别，不能只验证readCategory。

强制前置Pufferfish’s Skills 0.19.2（Forge，版本范围[0.19.2,0.20)），不内嵌宿主；独立food-interaction-recipes产物不新增此依赖。技能树ID cuisinejournal:valheim与cuisinejournal:vanilla，数据在data/cuisinejournal/puffish_skills/config.json及categories/<mode>/，使用category.json/definitions.json/skills.json/connections.json（不是general.json）。各68节点、同坐标/ID/连接，四处通路互斥加两个互斥结局；选通路开放对应侧支，任一通路可进入汇合，单向连接不允许回绕另一侧。模式切换锁定/隐藏另一棵树、保留其配点，奖励以tree/id分别记录且仅当前模式生效；档案skillMode防止离线与切换过渡读取另一模式聚合。
护盾支线两生命节点合并为s4_b_side_1生命+2、费用2，移除s4_b_side_2并直接接s4_b_side_3。档案skillTreeRevision记录一次性迁移：通过Skills官方export/import保留其余树与点数来源；原两节点都买过保留合并节点，部分购买取消并返还占用点数。

- /cj skills或图鉴技能入口：打开当前模式树，K宿主入口也只显示已解锁当前树；界面打开时模式切换随宿主同步刷新。
- /cj skills reset：显示费用/清食物警告；/cj skills reset confirm：花5级原版经验，仅重置当前模式树并退技能点，保留另一树；Valheim额外清当前食物/饮品，Vanilla不清饥饿资源但裁剪超出新上限的数值；不会恢复生命。
- 等级L为两树分别累计授予L点，来源cuisinejournal:cooking_level使用setPointsSilently对账；升级/重登/重载核对，保留其他来源点。Valheim32种完整路线92点，Vanilla32种完整路线94点；无额外整数等级点。
- 最终价格：生命+1/完美+1/耗时-2或-3各1，生命+2/完美+2/耗时-5/公共速度+10/临时+5秒/星级+1/保底+1/扣率-0.1各2；2B、3B的侧支保底各1点；容量5、护盾2、终点各5，负面0。效率路口+10为2、侧支+10/+10/+20为1/1/2。各段两路线全投入分别20/20/21/19/12，总92。
- 新配置默认常规2格、临时0格、基础5心；解锁临时时长5秒，普通10/极端20秒。既有common配置值不被覆盖：旧文件若仍写3/1，将作为可配置基础继续生效，测试新平衡需手动核对maxFoodSlots=2/baseTemporaryFoodSlots=0。
- 自身固定生命技能按生命点加算（2点1心），位于食物与饮品贡献之外；总生命仍带外部属性，非每道食物乘算。常规槽上限按技能+旧外部容量叠加，开胃额外2格仍保留。
- 星级初始上限1，主干逐次+1至5；小类发现0种上限1，不授予熟练度；小类与逐食物均受技能基础上限。今日成品+1与完美有效+1可突破此上限，但最终5；旧成品不改星，档案熟练不丢。
- 普通完美加成直接增加百分点，保底加成提高最终底线，批量节点减少每多一份的0.01扣减，最低扣减0。计算max(配置保底+技能保底,min(1,8%配置×基础星/5+技能完美+今日0.20-(count-1)×max(0,0.01-技能扣减减免)))；星级或奖励关闭仍不抽；在线和离线制作者通用。
- 工作站速度增量加算，两模式技能启用时不叠旧熟悉度加速；总速度普通/效率满路线1.5/2.0，速度终点后2.0/2.5；其他已注册provider保留。
- 进食耗时按原始时间加算百分比，最终同步客户端/服务端，消食的倍时在此之后计算；不引入连续进食疲劳。食物tooltip显示玩家技能耗时。
- 常规时长减半只在下一次吃普通食物时应用，不影响饮品/临时，不反复重缩已有记录；仅风味持续效果按食物时长与有限库存同步缩短；原生药水时长不因技能或临时槽截短，仍执行宿主逻辑。退出/进入Valheim清除原食物状态以避免模式套利。
- 临时槽实际持续时间按玩家技能5+修正秒数，存档可恢复20秒记录；开胃容量与时长分开，不把其旧常量10秒当技能记录时长。
- 独立护盾在技能启用后等待10秒补至4点，战斗中也补；先由原版吸收与原有减伤处理，再在LivingDamageEvent LOWEST吸收剩余伤害，金苹果护盾不被读写。剩余量另同步，在食物HUD旁以盾牌物品图标和心数显示；死亡/洗点/模式关闭移除，重登不立即补盾。
- 管理员/重载导致容量缩小时，移除最新超额记录并释放拥有的效果；洗点则已提示整组清空。增加生命容量不直接补血，低上限至少1点。合法普通路线最低固定10心，牺牲终点后最低3心。

实现入口skills/SkillsIntegration、CookingSkills、SkillPackets；宿主保存已购节点，百味纪档案保存有效聚合供离线制作读取。新增投影含技能上限、进食倍率、普通/保底/批量概率、护盾和时长修正；客户端断线清空。协议/产物只见CURRENT_STATUS。
Vanilla替换：5个容量位（一次路线取4个）各同时hunger_cap+4/saturation_cap+4，完整路线双上限36；temporary_seconds正向改saturation_regen+0.1，上支duration惩罚也改同效果，下支temporary_seconds负向改health+2，后两项各2点使第四段两路线各21点。普通生命加在原版/外部生命上，终点health_multiplier=-0.5用独立MULTIPLY_TOTAL；regen_double将(1+saturation_regen)乘2，饱和度快速回血间隔/耗竭消耗保持原样，无饱和度慢速回血不放大。吃食/needsFood/满饱快速恢复阈值按扩容上限，饱和度吃入仍不高于当前饱食度和饱和度上限；资源存档沿原FoodData字段，另发Nutrition包同步真实饱和度。Vanilla不增加常规/临时槽，不读取食物时长修正。原10鸡腿条保留，扩容以额外鸡腿/饱和轮廓显示，不再补饱食度/饱和度数字；独立护盾读数保留，具体显示及AppleSkin避让见“本机显示配置”。

### 芭菲与扩容兼容

ExquisitoParfaits专项只匹配exquisito:nightshade_berry_parfait和exquisito:ether_bulb_parfait，强制food分类（喝动画保留），默认用每口4/0.4计算属性/时长/占格；满杯第一口遵循普通常规入槽/替换规则，含不足一分钟刷新同种常规记录，不走临时兜底；无同种有效常规记录时任意剩余口数重走普通入槽，存在时damage>0的后续口只进临时槽，受真实临时容量约束，同种临时刷新。默认全新配置临时0格时续吃会被拒，不额外送容量，亦不借消食绕过。专用右键开始前和HUD预览共用专项placement，不允许时不开始动画。原生阶段效果在宿主finish窗口独立豁免原生效果关闭/临时截时/加级与技能缩时，现场添加/消费不耗空同ID风味库存；风味仍跟槽内记录寿命和flavors开关。营养显示遵守nutritionDisplay，有Obscure时接其原图标入口，无Obscure时百味纪图标兜底；数值营养入口修复isEdible=false问题；无新配置/协议/NBT字段，旧已吃drink记录不自动迁移。

直接回血显示：DirectFoodHealing只读已核实宿主的无条件固定回血（Neapolitan四类Healing*Item的healAmount、草莓蛋糕每口1生命），与当前tooltip已解析的瞬间治疗等级数值相加，在“吃下立即恢复”显示一行；CYS已装沿既有线性4*等级，否则原版4*2^(等级-1)。直接值不乘风味药水等级、不受药水开关控制，无新增配置/网络字段，无额外治疗调用；条件/概率/阶段回血需另行按状态适配，当前不报固定数值。Parfait营养/分类及其他B002缺口未在该显示任务中修改。

B002可选Neapolitan联动：本维度SavedData cuisinejournal_milkshake_cauldrons持久化牛奶/奶昔液位、真实贡献batch与同质存放原物NBT；重新装瓶不重算制作，混合品质/快照不一致保守清继承。宿主真实启用addMilkshakeInteractions时登记关系，含Collector's Reap；新增百味纪来源配方cuisinejournal:milkshake_cauldron/<namespace>/<bottle>/1..3（类型neapolitan:milkshake_cauldron），每1..3份牛奶配一冰淇淋输出1..3瓶。无新增网络字段/配置开关；非当前Mod列表或配置未启用不硬加载宿主类。宿主蛋糕按每口值投射到百味纪食物模型，不能从整块物品数量推断一口等于整块。

B001方块加工归属新增主世界/对应维度SavedData：cuisinejournal_cheese_molds（奶酪模具人工凝乳投入/原物存放）、cuisinejournal_brazil_pots（牛奶锅→奶油→奶酪真实投入），旧已放置加工不会凭空补制作者。烘焙垫跨阶段在BlockEntity persistentData的cuisinejournal:ube_baking保存贡献batch，最终完成/手动取回后清除；既有cuisinejournal:production保留。cuisinejournal_placed_meals现对确认多状态分份/烛台转换继续保留星级和制作人；静态混合补放清原记录，不猜混合品质。无新增网络包/命令，协议与构建见CURRENT_STATUS。

可选twilight_spark_delight营养兼容：双方同时安装时饱食度与饱和度上限逐项max，不将两种成长加算。宿主ExtendedFoodProgression接口保留其进度计算，返回时与百味纪自有上限合并；百味纪读取宿主原生上限有防重入以免两项互相污染，统一用于吃入/裁剪/needsFood/快速恢复满饱阈值与同步。FoodData.eat整数Math.min的上限参数取max，避开宿主ModifyConstant；快速回血用ModifyArg允许宿主自身增益继续修改。没有宿主时保持独立路径，不增加硬前置；未改宿主额外耗竭与奖励设定。

### 招牌菜与菜谱追踪

多人编辑保存契约：风味预设、物品/标签风味绑定、分类外观/移动/删除/恢复、批量料理归属请求携带打开草稿时的expectedVersions，服务端按受影响条目和依赖比较；同条目变化返回冲突且不写盘，客户端保留草稿/目标选择，不随广播自动换基线。不同条目独立；删除分类还检查子分类与成员，批量任一冲突整批拒绝。版本只用于编辑并发，不写进物品NBT；删除保留版本墓碑，改回原内容不恢复旧令牌。冲突后重新打开条目核对，不提供静默覆盖或自动合并。
方案保存/覆盖/载入/改名/复制/删除/导出按风味或分类各自集合版本检查，风味与分类互不冲突；导出因全局资源重载临时拒绝全部编辑，完成后解除。载入失败回滚已写的本地层，导出先暂存再交换数据包，重载失败恢复旧数据包并重载原选择。同步包携带服务端版本表；网络版本与构建见CURRENT_STATUS。

导出活动方案时由SchemeStore.forExport取得当前本地内容，包含未另存的修改；首次未选/已记方案不存在时，默认行代表当前本地层，不能导出固定空快照而丢修改。明确选择另一个未载入方案则导出该方案快照，不把当前编辑混进去。打开方案窗口无有效当前项时选空默认行，不自动选列表末尾。导出不覆盖保存快照、不清编辑；载入默认依然清本地层，外部数据包保持生效，不承诺关闭外部包。

方案dirty比较先用FlavorPresets/Flavor.fromJson转为规范内容，避免旧格式省略默认字段造成刚载入就“未保存”。分类层子类新增drinkFallback布尔字段（默认false），经合层/本地复制/保存/导出保留饮品兜底，不扩展其他分类条件。

招牌菜在已发现料理详情的星级区后选择，按钮仅本人制作过且当前精通时出现；已选条目即使失去资格仍显示暂停状态/取消入口。点击后再确认，首次免费，此后选择或取消均花signatureDish.changeExperienceLevels（默认5）级原版经验，每个DailyCalendar游戏日最多一次变更，取消不恢复首次免费、不清当天限制。默认signatureDish.perfectChanceBonus=0.10，要求当前星级开启及精通、本人的制作记录，按preparationTarget匹配同源产物；普通加成直接+10个百分点，之后才算今日/批量/保底，在线与离线制作者共用。不再额外加星。旧signature选择迁入已选择状态，不补收费，缺日期默认从未用过当天额度；资格失效只停加成，不删除选择。旧SIGNATURE请求也进入同一服务端校验。

菜谱追踪复用现有HUD并独立于经验HUD的开关/暂显计时；本机cookingHud.showPinnedRecipes默认true，客户端设置加入“菜谱追踪HUD”。详情配方区在工作站名后放图钉：点一下按当前配方追踪（从同料理另一配方切换时沿用原份数，否则1份），再点一次取消；追踪中在原料下显示“追踪中 − N 份 +”，−/+直接改目标1..4096份。追踪的是另一配方时提示“正在追踪配方 N · 点击图钉改为追踪该配方”。最大同时追踪advanced.maxPinnedRecipes默认5（可配置1..16，已有配置值保留）；已满时新料理被服务端拒绝并提示“已达追踪上限”，图钉变暗不可点，不再挤掉最早条目；已追踪料理换配方/改份数不受上限影响，下调上限不删除已有追踪。概览页“追踪中”可单条取消。保存的是具体recipe ID与目标，不取所有配方合集；服务器从真实RecipeEntry计算每批输出数量与全部候选，目标按ceil(target/outputCount)批折算，重复槽合并计数。旧pin在有空位时迁当前第一个合法配方、目标1份，已满则清除该旧pin；配方缺失/过大/无原料不显示其需求，不猜一个错误配方。

HUD显示料理目标份数、灶具、原料图标与“已备/所需”；标签列#ID、可用成员计数，候选图标轮换。同一配方内用残量网络最大流分配重叠候选，避免同一物品在多个需求重复计；不同追踪配方各自检查备料，不合并成全局采购单。背包约每秒扫描、修订立即失效缓存，不每帧扫描库存；一次需求分配缓存到下次库存刷新。计数只看物品ID，不处理成分NBT/流体量/耐久工具，不声称通用流体/特殊配方完整还原；不递归展开原料加工链、不自动消费或把产出加入背包，不自动减少目标或完成取消。HUD在屏幕边界裁剪，原位置/缩放保留，窄详情控件换行。

网络新增JournalFeaturePackets.Track(dish,recipe,target)、Signature(nullable dish)、Sync(signature/chosen/lastChange/day/bonus/cost/eligible/tracks/trackLimit)；每个Tracked包含dish/recipe/method/target/output/slots(items完整候选/tag/needed)。服务端验证料理确实收录、配方属于该料理及目标范围；未发现但详情允许查看时也可追踪，不额外发现或授予制作/经验。hideUndiscoveredDetails=true时未发现追踪被拒绝，已有选择保留但不发HUD需求，取消不受发现限制；客户端不上传原料需求。最大16追踪、每配方64槽、每槽8192候选，超限拒绝而不截断候选造成误判。档案新增recipeTracks/signatureChosen/signatureChangedDay，旧signature/pinned及RecordView字段兼容；退出世界清客户端缓存。当前协议和构建见CURRENT_STATUS。

### 属性与成长公式

N为营养值，S为饱和系数。S<0.6时S'=0.3+0.5S，否则S'=S。
占格由风味前Q=N(0.75+0.25S')决定：Q≤10一格，其余仅N>18且N(0.5+0.5S')>16.2三格，否则两格。明确foods.slot_cost优先于本地列表、标签、自动。
生命原值X=N(0.5+0.5S')×占格系数（一/二/三格为1.2/1.5/1.8）再分段：≤16完整；16..36增加段×0.75；>36增加段×0.2。源码单位生命点，2点=1心；最终取整生命点等于半心取整。一格二星基准上限11点，但星级/风味增量可突破；全局maxFoodHearts仍生效。
各星0..5生命系数offset为-0.2/-0.1/0/+0.1/+0.2/+0.3；回血系数（心/秒）为0.02/0.025/0.03/1÷30/11÷300/0.04。回血原值N×S'×系数，0.6心/秒以内完整，以上增加段×0.25。
普通食品默认N分钟，饮品10分钟；单物品foods覆盖可改基值。有效五星只补本次净增加的生命容量，不把整个缺口直接回满。
Vanilla每星原饱和系数×(1+0.05星数)，有效五星额外1实际饱和；完美有效+1星封顶5，营养×1.5取整，额外饱和不重复乘。
无真实制作来源原料默认二星，不写NBT；测试quality标记可覆盖。整批只判定一次，未启用技能时完美概率=max(保底,min(1,满星配置概率×earned/5+今日0.20-(本次数量-1)*0.01))；启用技能的公式见上节。满星配置默认0.08；mastery.bonuses.minimumPerfectChance默认0.01、范围0..1，最后保底不随星级或数量扣减，零星也可抽；count≤0仍为0，星级/成长奖励关闭仍不抽，继承/不允许抽品质路径不绕过。
普通领取实际数量，温泉一次转化总数，在线与离线制作者均用配置保底；旧印记与切片不重抽。鼠标已有料理堆继续合并时不重抽，以免合并后改品质；Shift预留普通/完美两种可能品质都能放入的空间，满包拒绝不抽奖。Shift tooltip显示当前玩家单份制作概率（含今日与保底），不是按鼠标堆叠数量实时报价；小数显示到两位，不再把1.6%四舍五入成2%。SyncRules新增minimumPerfectChance，协议/构建见CURRENT_STATUS。
制作XP分档累计；实验经验日限额已接，极端多配方不继续完善。社交默认别人每吃1熟练/8XP，日衰减1..3全额、4..10一半、11..20四分之一、21起十分之一，小数余额存档；自己的首次吃与他人奖励分开。
工作站只加原生制作进度，不加燃料/整机tick；普通熔炉/高炉排除、烟熏炉包括。CookingSpeed与容量API已接Valheim技能奖励，外部provider接口保留。

## 风味与药水契约

- 预设data/ns/cuisinejournal/flavors/id.json；name/description/icon/icon_effect/group/enabled/stages。
- stages最多6个唯一min_stars（0..5）；取最高满足阶段整体替换，不叠加所有旧阶段。
- effect_target=all/food/drink控制阶段药水及等级加成。数值组modifiers/food_modifiers/drink_modifiers独立，不能把两者当同一个开关。
- 数值字段health_points、health_multiplier、regen_per_second、regen_multiplier、duration_seconds、duration_multiplier、drink_bonus_multiplier。风味间固定值与倍率增量逐项相加，最终max(0,(base+flat)×max(0,1+multiplier))。
- drink_bonus_multiplier保留旧JSON名，但现在是加算到饮品增益，不是乘算；两项-50%时间合计到-100%，受最低时间约束。
- effect_level_bonuses含id/levels；不同风味加级相加，同预设去重；没有原效果也可给对应等级，有则加级。瞬时效果按一次触发，不持续续期。
- Valheim active_fraction按食物时长，Vanilla duration_seconds按秒；风味同效果取更高等级后更长有效时间。通用配方来源取合集，已适配动态料理可读取实际来源快照。原生效果另由宿主执行，不纳入此集合或套风味加级。
- foods.flavor_sources未配置=自动，[]=明确禁止继承，非空=替代自动；source=true中间原料为追溯止点。多成员标签只传标签风味，不展开所有成员。
- 编辑保存、方案、导出重载与配置层独立；失败不能提前显示成功或丢本地修改。缺模组且无数值内容的风味普通浏览隐藏，编辑保留。
- 只有风味效果是槽内有限资源：外部消耗/时间缩短等量扣库存，标准牛奶清风味实例保余量，瞬时每entry一次。原生效果不捕获/回放，不因临时或食物槽结束移除/续期，也不套技能时长或风味加级；原生总开关仍有效，芭菲沿原豁免。旧foods.effects及legacy extraEffects仍可解析保文件兼容，但不作为库存/额外发药来源，新增药水内容用风味。
- CuisineFoodEntry只保存风味的effect_reserves、owned_effects、triggered_effects及初始化状态；不再保旧原生/风味混合库存的effect_scope或其迁移分支。原生效果不由此入口读写；触发历史持久化防重播瞬时。

## 槽位与特殊效果

新配置常规默认2、临时默认0，饮品独立；Valheim技能可扩容，临时基础5秒/技能最长20秒，保持实际占格、允许战斗回血、不增生命容量；同种刷新不增数量，异种按容量替换。旧配置不会被自动覆盖，见技能树契约。

| ID | 行为 |
| --- | --- |
| fruitsdelight:appetizing | Valheim临时容量+2 |
| fruitsdelight:digesting | Valheim额外食用时长×2×占格，完成瞬回该食物生命贡献；与临时同时可治愈 |
| farmersdelight:nourishment | Valheim脱战恢复+25%，战斗获得未加成脱战恢复量50% |
| minecraft:saturation | Valheim瞬时清战斗计时，并120tick攻击/受击不入战斗保护，重复重新计满 |
| brewinandchewin:sweet_heart | Valheim脱战等待减半，默认6秒→3秒，不按等级继续减半 |
| fruitsdelight:chorus | 宿主未标instant的特殊tick效果，适配为每entry一次 |
| cuisinejournal:ice_ice_ice | 真瞬时效果，Player冻结计数直接设140，不按等级/时长锁定 |
| cuisinejournal:mature | 未装Youkai时注册；装后只用youkaishomecoming:mature，两种引用自动解析 |

熟成沿Youkai2.7.0：min(⌊damage×0.2×等级⌋,⌊滋养tick/100⌋)抵扣，每点耗100tick滋养，跳过bypass effects/resistance/invulnerability；无农夫乐事滋养不减伤。
百味纪酒劲机制已移除：无跨模组醉酒累积/同步、无酒劲倍率、无满级禁饮或酒劲tooltip。酒类仍识别为饮品、保留瓶装来源和原补饮规则；饮品增益按普通配置/风味生效，药水按普通等级和时长，不跳过醉酒效果或给其特殊保留例外。宿主mod自行产生的tipsy/drunk不删除、不扫除现有实例，遵循普通药水开关；删除的是百味纪额外规则。

## 荣誉与挑战

徽章定义data/ns/cuisinejournal/badges/id.json，icon物品，纯元数据无独立条件。挑战rewards.badge或里程碑badge解锁；永久保存，仅已获得可佩戴一个或取消，不加属性。
荣誉统一徽章身份，仅存selectedBadge，不保旧title/一次性补发迁移或同路径别名。只允许佩戴已获得徽章，读取存档也检查归属；不自动佩戴最高徽章。未佩戴时侧栏不绘制荣誉行，里程碑/徽章入口用户已确认整理完成。
挑战/里程碑data/ns/cuisinejournal/challenges|milestones，仅用这一套目录。里程碑只有累计条件与可选徽章，没有经验/物品奖励。
PERMANENT/CHAIN/DAILY全部自动统计，前置解锁后生效。manual和duration_seconds废弃，旧包读入不会重新启用接取或倒计时；旧StartChallenge请求无效，网络兼容字段固定为manual=false/remainingTicks=-1。单日目标按日历周期重新开始，已完成的一次性挑战不重置/重奖，无单日条件则进度跨日累计。铁人厨师自动认定同日10种菜，不要求接取或20分钟倒计时。WEEKLY已删除，旧包定义跳过，记录/荣誉保留；weekly配置忽略写回删除，原每周筛选是旧GUI残留，后续清理。
所有日期规则共享DailyCalendar和CulinaryClock：每日内容、制作经验衰减、社交奖励、实验额度、发现/制作/食用历史、连续纪录同日刷新。原版主世界dayTime凌晨4点，睡醒跨点一次；BetterTime1.4跟随getDays语义（dayTime/24000）。存档data/cuisinejournal_daily_calendar.dat保存最高日期与日序号。首次升级用旧gameTime日序号一次锚定以保留当前记录，此后无运行tick日判定；安装/移除BetterTime不额外刷新，倒退日期不退进度/重奖。真实dayTime加速/减速/暂停会同步影响日期，独立视觉时钟需适配。REAL_DAYS旧连续设置迁MINECRAFT_DAYS，只有日历或关闭两档。

默认1目标、各基础40XP，乘挑战经验倍率和总倍率；数量可配0..20。无截止倒计时/按秒失败；日任务只在日历换日重置。每日模板去重：已用灶具3..7份；分类2..4种（仅星级开且SUBCATEGORY）；任意3..6种；同日3..5种；完美一次；今日制作5次；进食25..40。可用模板少于数量配置时不重复补满。

自动每日挑战可在server TOML的challenges.dailyRewardItems配置每项完成后的物品奖励，默认[]；写法为物品ID或物品ID加数量。所有生成模板共用此列表，奖励随当天定义冻结存档，修改配置并/cj reload不改已生成的当天奖励，下一轮使用新配置。数据包DAILY/PERMANENT/CHAIN各用rewards.items，不叠全局列表。奖励按物品堆叠上限拆份进包，剩余掉落；未知物品跳过并记日志。单项数量1..4096，格式错误/非正/超限跳过，最多64项，不支持物品NBT。

挑战完成在玩家档案rewardDeliveries冻结实际奖励组件；先持久化完成和待发记录，保存失败不开始发奖。actions每项kind/amount/value/state，state=0未执行、1已执行、2外部执行中断或结果不明。待发项可跨日/重登继续，已发项不重复；对物品/原版经验/进度/命令先保存state=2再执行，成功后存1，避免崩溃后盲重发。状态不明项保留记录、提醒管理员人工核对，不自动再发，也不阻止其它项。烹饪经验/熟练度/徽章和投递状态同属一个档案保存。不能在玩家档案、原版玩家数据、世界掉落和任意命令之间提供严格恰好一次事务；未宣称断电自动补偿或已有管理员恢复GUI。显式CHALLENGES/EVERYTHING重置会清该待发记录，正常换日不会。

完美与今日目标按成功制作结算事件加1，不按一堆份数；完美检查本次MealStamp，旧切片继承不算新制作，今日目标保存当日选中物品ID并关联preparationTarget。进食每次成功食用真正FoodProperties食品加1，包括未收录原料，不计普通药水/非食物，也不额外发原料食用成长奖励。灶具以成功制作记录为用过，不是只打开界面；世界并集合并离线档案一次并持续记录，限制在当前有制作配方的方式。任务与已用方式保存在data/cuisinejournal_daily_challenges.dat，重启/重载同日不重抽，新使用方式参与后续日。切换DISH当日分类任务隐藏/停推，恢复模式后仍使用原目标。任务生成不推断物资可获得性，分类/一般种类任务不用今日白名单或维度阶段筛选。
挑战目标全部满足后自动奖励，rewards支持cooking_xp/vanilla_xp/items/advancement/badge/commands；commands默认关闭。mastery_xp必须配mastery_dish明确目标，不猜料理。
旧主题集合及/cj collections退役，真实料理分类和个人纪录保留。每日料理现已独立，见下节。

## 今日料理

服务端todayDish.enabled默认true，独立于challenges.enabled。轮换和fromDay解锁使用上文DailyCalendar，fromDay从1计；升级保留旧日序号、之后跟随昼夜/BetterTime日期。可追加多组配置：

```toml
[todayDish]
enabled = true
requireVisitedDimension = true

[[todayDish.groups]]
fromDay = 1
mods = ["minecraft", "farmersdelight", "delightful", "brewinandchewin", "youkaishomecoming"]
weight = 1.0
```

已解锁组继续参与，先按weight抽组，再从组内有制作配方的料理均匀抽取。未到日期、权重0、缺mod/空组跳过；groups=[]不回退全部料理。重复mod跨组会提高机会。默认单组内各料理等概率，不保证各mod总概率相同。

requireVisitedDimension对照foods JSON的dimension（默认minecraft:overworld），任意玩家登录/进入维度记录世界访问，真实食物归属后续配置，不猜Boss稀有度。全服同一道，选择/访问保存于data/cuisinejournal_today_dish.dat，重启不重抽。/cj reload当天选择合法保留、无效重选；当日新维度访问不立即换菜。

新制作今日料理基础星+1最高5，当前完美率+20个百分点再扣数量；关闭星级不加品质，完美受成长奖励开关。旧物品不随日期变化，无额外经验。温泉单制作者离线仍固定产出品质，不发离线即时成长；多人混合沿已有归属结算。SyncTodayDish提供客户端day/dish，GUI本轮不动。旧recipeOfTheDayEnabled迁todayDish.enabled，旧未生效bonusXP删除。

## 兼容与物品统一

巴西晚餐常驻纠错：安装braziliandelight才注册高优先级内置包cuisinejournal:brazilian_dinner_fix，包含同ID菜谱和deltaboxlib:fried_egg标签两资源，避免普通mod资源被宿主后加载覆盖。配方为原两豆＋具体farmersdelight:cooked_rice＋forge:cooked_eggs，产出不变；旧煎蛋标签用Forge remove移除农夫/森罗米饭，不添加森罗煎蛋、不清其他原成员。已撤掉侧聊forge:foods/cooked_rice扩充。无配置键/协议变化，不受统一开关影响，不改宿主JAR；新版实际优先级/显示待实机。

常驻鱿鱼兼容不受统一开关限制：补forge:tentacles与forge:raw_fishes/calamari，新增cuisinejournal:raw_squid_tentacles；矿工章鱼烧去双ID重复、两种寿司和海洋生须串改对应标签，海之三种烧制拆普通/发光物品。补forge:cooked_fishes/squid（9种）供指定熟制配方及矿工三明治，新cuisinejournal:glow_squid（5种）供发光炖/已有汤锅变体；汉堡两处蘑菇OR保留。所有成员可选追加，缺模组不报缺失。配方补丁在米饭路径补丁后处理，不改其他配料/数量/时间/条件，不改掉落/旧物品；详docs/compatibility/鱿鱼标签与配方整理.md。

Compat1.20.1临时配方补丁：仅装kaleidoscope_compat且实际加载unite_farmersdelight时，在RecipeManager反序列化前修已知多元8条/乡村5条与youkaisfeasts或youkaishomecoming刨冰盖饭的farmersdelight:cooked_rice输入为森罗米饭。沿用已选中的原配方JSON，只改ingredients/key及Forge条件分支里的对应item，保留Delightful触手标签、原条件、数量/输出。宿主JAR中旧错误recipe/文件存在且正确命名空间recipes/文件缺失才启用；上游修正或撤除旧文件则跳过。没有配置键/协议变化，不改第三方JAR、不新增缺失配方，不推断空白煎蛋文件用途。服务器详情过滤出空格时跳过并记录配方ID/原候选，客户端也跳过空格，防止取余除零；不凭空填替代食材。

Compat隐藏联动：百味纪读取已加载`kaleidoscope_cookery:united`物品标签，排除对应料理收录（包括显式分类与份额自动补入）、食材及标签显示成员/详情来源，料理配方的轮换候选也过滤。原始配方和玩家历史记录保留，不修改第三方注册物品；标签移除并重建目录后恢复。无需额外开关、无需硬前置；无标签/空标签不隐藏任何物品。标签每次服务端资源重载后重建快照，不以尚未生效的Compat配置值推断成员。

标签翻译辅助仅在食材图鉴管理员编辑模式出现，可选全部/未翻译优先/仅未翻译；无配置显示名且当前语言的主标签及合并别名均无译名才视为未翻译。组内保当前排序，同步/语言变化自动重算；退出编辑或离页/失权即清除，不写TOML、存档或网络规则，普通玩家无入口。

服务器设置页“物品统一”仅“默认／开”。`compatibility.unifiedMode`默认false，已接轻量规则框架及首批明确食物组。管理员切换时安装自己的cuisinejournal:unification数据包；若有Kaleidoscope Compat，额外写其general.datapack_mode（默认COMPAT/开UNITE）并替换六个已知包来源。不重复广播其他mod注册事件，保留其他包与相对优先级。随后异步reloadResources，重建目录同步全服，JEI与创造随标签刷新。应用期间阻止重复/方案导出冲突，失败恢复原配置/来源/选择并重载。无Compat也独立工作，不增加硬前置/不扫描旧背包。首批实际原料标签、配方、重复肉掉落、标准收获/装瓶与指定野生生成入口随已加载策略切换，风味不叠加成员绑定；果酱/水母/金蛾和嗅探腿尾不合并。资源覆盖及路径边界见[物品统一规则](docs/UNIFICATION.md)，优先级见内置unification.json；无新配置键/网络字段。

设置展示和CJ规则生成统一读百味纪compatibility.unifiedMode；手工修改Compat配置不等于已应用双方资源重载。打开设置或启动不因此强制覆盖宿主NONE模式，需通过设置明确应用。文件直接修改百味纪字段不会代写第三方文件，模式切换请用设置页；本地规则文件修改后/reload重读。设置页无需重启或重进存档；实际双向显示与多人效果待实机。

统一规则B005阶段补充（54组/145候选为该阶段历史子集，不是当前总量）：组可显式keep_recipes=true保留不同制作渠道并将产物转代表，数量/工具/配料/条件不变，默认false仍停低优先重复产出；priority_when_loaded按首个已安装mod的声明顺序覆盖代表优先级，候选仍限items。有森罗Compat时面团沿其森罗代表、无时保持农夫优先；草莓包装有Berry Good才选其解锁的那不勒斯篮。kept_recipe_outputs仅是重载资源策略字段，旧无字段视为空，不改网络或玩家存档。确切组与边界见统一规则及候选盘点第6节；盐不追加，葡萄/奶酪不统一，番茄/洋葱/卷心菜不重复接管。

## 本机显示配置

journal：defaultPage、recentActivityLines、allFoodsView、sidePanel（ALWAYS/CULINARY/OFF，个人背包/创造栏排除）。
foodHud：useLargeFoodIcons。cookingHud：enabled默认false、onlyWhileActive默认true、visibleTicks/anchor/offset/scale；名称等级经验HUD，与食物槽无关。showPinnedRecipes已接菜谱追踪HUD，独立于经验条是否显示，位置/缩放复用。
Vanilla溢出HUD不再显示饱食度/饱和度数字。超过20的饱食度用用户指定鸡腿叠在原栏，饱和度用橙色四档轮廓；每个位置2点，饱和余量(0,.5]/(.5,1]/(1,2)/2分别选四帧。超过40继续向上增加每20点一行并预留HUD高度。技能护盾原显示保留，不改实际营养/进食/回血。饱和度和手持预览受nutritionDisplay.enabled控制，原版鸡腿及额外饱食度属于基础状态，不因营养显示关闭而消失；装AppleSkin时再遵守其SHOW_SATURATION_OVERLAY/SHOW_FOOD_VALUES_OVERLAY及副手预览偏好，没有AppleSkin仍能显示实际溢出饱和度但无手持预览。Valheim不显示此HUD。同时安装暮光乐事时仅在百味纪接管的Vanilla显示路径避让其额外鸡腿及AppleSkin扩展层，不改宿主数值或复制其贴图。
notifications：发现/升星/升级/挑战/里程碑、经验弹出、通知音效与合并/频率。上述事件使用原版AdvancementToast（可由FancyToasts等进度通知模组接管），仅客户端临时展示，不注册/授予额外进度或奖励；经验仍使用合并后的动作栏提示。按钮音效另行管理。

图鉴与设置背景沿原版Screen.renderBackground，不创建独立模糊PostChain。1.20.1原版背景只变暗；模糊效果由已装界面模组及其配置决定，百味纪不另外处理光影渲染目标。
cozyMode/showNewIndicator/showStars及旧熟练度/制作次数等零散开关删除，正常数字/品质信息统一显示。client.tooltips.requireModifier默认true：制作记录和概率明细按Shift展开，关闭后常显；星级、食物属性和药水效果不折叠。设置页有对应本机入口，旧同名字段可继续读入。
营养统一使用server.nutritionDisplay.enabled，不另加旧showNutrition。管理员设置页显示“营养值显示”，通过SetNutritionDisplay请求，服务端检查multiplayer.adminPermissionLevel、保存server TOML并广播SyncRules；普通玩家不显示这个全服设置。保留既有Valheim/Vanilla默认组合与同模式手动覆盖，不改写第三方自有偏好。

## 命令

| 命令 | 当前作用 |
| --- | --- |
| /cj、/cj open、/cj help | 打开/帮助 |
| /cj stats、/cj recipe <item> | 自己统计/料理资料，只查询，不触发发现，不打开JEI |
| /cj challenges | 当前任务进度；全部明细GUI仍需补齐 |
| /cj badges、/cj badges wear <id>、/cj badges clear | 查看/佩戴/取消徽章 |
| /cj today | 查看今日料理与日任务；品质加成只用于新制作成品 |
| /cj leaderboard | 已开启的在线玩家榜，主动tag cuisinejournal_leaderboard（兼容旧tag） |
| /cj export | 聊天输出档案，不是方案导出 |
| /cj reload、/cj compat、/cj debug | 配置重载/识别报告/管理员调试 |
| /cj audit [ingredients/workstations/recipe_workstations] | 审查报告和TSV |
| /cj category ... | 分类编辑/创建/分配/还原，详见GLOBAL_CATEGORY_EDITOR.md并核对源码 |
| /cj admin inspect <player> | 在线目标资料；同时受admin和inspect权限限制 |
| /cj admin quality <stars> [perfect] | 改主手整堆基础0..5星，perfect省略保留，其他NBT/制作人保留，测试标记 |
| /cj admin sample <item> <stars> [count] [perfect] | 测试食物，count1..64默认1、perfect默认false，不造制作记录 |
| /cj admin reset <player> <scope> | CHALLENGES只清任务，徽章/里程碑保留；EVERYTHING明确全清例外 |

其他管理员addxp/setlevel/discover/purgeorphans沿源码命令树，不从历史文档猜参数。无/mealmastery或/mm新别名。
服务端multiplayer保留排行榜与权限；automation有不计入/全额/减额的可信自动制作成长策略，不自动识别任意机器主人。数值调参保留文件，普通设置GUI不铺满数值。

## 挑战 GUI 对接

本节是基于 2026-10-14 源码核对的界面对接参考，不是页面布局定稿，也不授权修改 GUI 或补玩法。有效决策见 DECISIONS.md，周期和配置见上文「荣誉与挑战」「今日料理」，构建及实机验证范围见 CURRENT_STATUS.md。

### 内容分工

| 内容 | 玩家做什么 | 完成或收益 | 跨日行为 | 数据来源 |
| --- | --- | --- | --- | --- |
| 今日料理 | 制作今天指定的料理 | 新成品基础星级 +1、完美概率 +20 个百分点 | 换日换菜，旧物品不变 | SyncTodayDish |
| 每日挑战 | 自动统计当日任务 | 达标自动发放奖励 | 换日换任务、重新统计 | ChallengeView，scope=DAILY |
| 一次性挑战 | 自动统计较难或特殊目标，可有前置链 | 全部目标满足后自动奖励，一次完成 | 通常保留进度；含单日目标时，未完成尝试换日清零 | ChallengeView，scope=PERMANENT/CHAIN |
| 里程碑 | 累计制作、发现、等级等个人记录 | 永久完成，可解锁徽章，没有经济奖励 | 保留累计与完成 | SyncStatistics.achievements，badge=false |
| 徽章 | 收集并选择佩戴已获得的荣誉 | 纯展示图标和名称 | 永久保存 | SyncStatistics.achievements，badge=true |

这是四种玩法内容加一份荣誉收藏。徽章不是第五套目标，原称号不再另有解锁逻辑。今日料理也不是每日挑战；每日挑战可以引用今日料理。CHAIN 是有前置的一次性挑战，不是另一套周期任务。

里程碑/徽章入口2026-10-21用户确认已整理完毕，本文保数据契约，不据旧设计过程重开布局任务。界面不得恢复每周入口、接取、手动开始、重试、领取奖励或任务倒计时。

### 日期与统计边界

- 所有按日机制共享服务端 DailyCalendar/CulinaryClock。GUI 使用服务端 day，不以客户端 tick、现实日期或本地时区另算。
- 原版在主世界 dayTime 对应凌晨四点换日；睡醒跨过该点同样换日。安装 BetterTime 时跟随该模组日期边界，其当前实现是 dayTime/24000。没有额外的现实时间定时器。
- 改变真实 dayTime 的速度、暂停或跳时会影响换日。时间倒退不退回已使用的日序号，不重新发奖。首次升级的旧 gameTime 日期只用于一次迁移。
- 每日任务及今日料理的选择是全服共同内容；每人的挑战进度、完成记录、奖励与徽章分别保存。团队发现共享不等于任务进度共享。
- 一次性挑战只要包含 UNIQUE_RECIPES_IN_ONE_DAY，就会在换日时清掉该挑战全部未完成目标的进度，不只是单日那一行。已完成挑战保持完成且不重奖。
- 其余一次性挑战没有自动换日重置，不要求玩家在本次打开界面后才开始。新目标一般按启用后的事件推进，不能把所有历史统计都当成挑战进度回填。里程碑会读取累计历史。
- 不显示“剩余几分钟”。界面可区分“今日”与“累计”，但当前挑战包没有明确的单日周期标志，需通过 objectives 中是否有 UNIQUE_RECIPES_IN_ONE_DAY 判断这种现有规则。

### 每日挑战模板

默认每天随机抽1种模板，可配置更多且模板去重，可用模板不足时不重复补齐。每个任务基础40烹饪经验，无默认徽章，额外物品由challenges.dailyRewardItems配置，默认空；实际经验受挑战倍率、全局倍率与成长开关影响，当前客户端下发实发经验（显示字段最大为有符号32位整数）。

| 模板 | 当前目标范围 | 计数单位 | 出现条件与边界 |
| --- | --- | --- | --- |
| USE_METHOD | 指定灶具制作 3..7 | 产出份数 | 该存档有人成功使用过，且当前仍有相关制作配方 |
| COOK_CATEGORY | 指定类别制作 2..4 种 | 不同料理去重 | 星级开启且 SUBCATEGORY 模式；不足时目标下调到该类别可制作种类数 |
| COOK_RECIPE | 任意制作 3..6 种 | 不同料理去重 | 目标最多为当前可制作种类数 |
| UNIQUE_RECIPES_IN_ONE_DAY | 同日制作 3..5 种 | 不同料理去重 | 目标最多为当前可制作种类数 |
| COOK_PERFECT | 制作 1 次完美料理 | 成功制作结算事件 | 星级及成长奖励开启，本次制作印记确实为完美 |
| COOK_TODAY | 制作 5 次今日料理 | 成功制作结算事件 | 今天存在可制作的今日料理；按其制作归属目标匹配 |
| EAT_FOOD | 成功进食 25..40 次 | 成功食用事件 | 包括未收录为料理的真实食品；普通药水、非食品不计 |

注意：COOK_CATEGORY 目前匹配料理的 category 根类别，不是 subcategory。只有它的出现资格与“小类星级模式”关联，不应据此把目标文案改成指定小类。

例如一次取出 8 份今日料理，今日任务增加 1 次；同次 8 份全为完美，完美任务也增加 1 次；灶具制作目标增加 8 份；制作种类目标只新增 1 种。各特殊制作路径仍依赖真实生产结算事件，不根据背包数量倒推。旧成品切片或品质继承不自动视为一次新完美制作。

两种任意制作不同料理的每日模板目前内容相近，但属于不同模板类型，可以同时抽到。当天任务及用过的灶具保存在世界存档；重启、重载不会因新增制作历史重抽当天任务。新用过的灶具参与以后日期的候选池。服务器灶具候选取所有玩家成功使用记录的并集，包括已归档的离线历史；单人自然只有本人。

切换为 DISH 模式时，当天分类任务隐藏并停止推进，恢复模式后仍使用原目标；任务数可能暂时少于 3。生成时也会过滤关闭的完美机制。

每日模板保证当前有配方，不保证玩家已经获得材料，也没有套用今日料理的模组白名单、维度或阶段筛选。数据包 scope=DAILY 的定义是额外的固定日任务，每日重新统计，不是加入这 7 种随机模板池。

### 一次性挑战与状态

挑战包含 1..16 个目标，所有目标同时满足才完成，是 AND，不是 OR。可配置 prerequisite 指向另一挑战；前置未完成时不推进，解锁后自动统计。hidden=true 的定义在未解锁且未完成时不发送给客户端。无效、缺失或循环的前置链会被注册器剔除，不应由客户端自行补回。

界面状态解释顺序：

1. completed=true：已完成，奖励已自动处理。即使后来配置关闭或内容缺失，也优先显示其历史完成身份。
2. completed=false 且 locked=true：暂不可用。可能是前置未完成、缺配方或模组、必要子系统关闭；包中没有原因，不能一律写“先完成某任务”。
3. 其余：自动进行中，显示服务端给出的每个目标进度。started 不是要求玩家点击开始的信号。

进度条只做显示裁剪，比例限制在 0..1；实际数值可能超过目标。不在客户端以进度相等为条件发奖励或修改 completed。隐藏任务直接尊重服务端未下发。

当前内置一次性挑战：

| ID | 名称/条件 | 基础奖励 |
| --- | --- | --- |
| cuisinejournal:iron_chef | 铁人厨师，同一个日历日制作 10 种不同菜 | 200 烹饪经验，铁人厨师徽章 |
| cuisinejournal:variety_is_the_spice | 多样才是调味料，不重复地连续制作 8 种不同菜 | 100 烹饪经验，无徽章 |

PREPARE_VARIETY 记录连续序列达到过的最高种类数；重复序列中的任意料理会重新开始序列，但不会抹掉已经达到的最高进度。它没有单日限制。内置没有额外的限秒倒计时或接取步骤。

### 可配置挑战目标

下表是运行时行为。unique 是 JSON 中的去重开关，但并非所有类型都读取它，不能仅按枚举注释推断规则。

| type | value 含义 | 进度规则 |
| --- | --- | --- |
| COOK_RECIPE | 料理物品 ID，空为任意 | 非 unique 按产出份数；unique 按料理 ID 去重 |
| COOK_CATEGORY | 根类别 ID，必填 | 匹配类别后按份数或按料理 ID 去重 |
| COOK_FROM_MOD | 模组 ID，必填 | 匹配料理命名空间后按份数或按料理 ID 去重 |
| USE_METHOD | 烹饪方式 ID，必填 | 匹配方式后按份数或按料理 ID 去重 |
| USE_INGREDIENT | 食材 ID，必填 | 按被记录的食材使用事件及产出份数；unique 按食材 ID 去重 |
| USE_INGREDIENT_TAG | 标签 ID，必填，不带 # | 食材属于标签时同上 |
| DISCOVER_RECIPE | 当前忽略 value | 新发现事件；unique 按料理 ID 去重 |
| MASTER_RECIPE | 当前忽略 value | 达到精通的事件；unique 按料理 ID 去重 |
| EAT_RECIPE | 料理 ID，空为任意 | 已追踪料理每次食用 +1；unique 按料理 ID 去重 |
| EAT_FOOD | 当前忽略 value/unique | 每次成功食用真实食品 +1 |
| SERVE_PORTIONS | 当前忽略 value/unique | 分享/分餐事件的份数 |
| PREPARE_VARIETY | 当前忽略 value/unique | 连续不重复制作序列的最高长度 |
| UNIQUE_RECIPES_IN_ONE_DAY | 当前忽略 value/unique | 本日制作不同料理数量，并使整个未完成挑战按日重置 |
| REACH_LEVEL | 当前忽略 value/unique | amount 为目标烹饪等级，收到等级变更时更新 |
| COMPLETE_CHALLENGE | 另一挑战 ID，必填 | 完成指定挑战时达到 1；amount 建议固定为 1 |
| COOK_PERFECT | 当前忽略 value/unique | 本次制作完美则 +1，不按产出份数 |
| COOK_TODAY | 料理 ID，必填 | 制作匹配该 ID 的 preparationTarget 则 +1 |

USE_INGREDIENT 不代表实际投入了几颗食材，当前数量来自制作份数；固定原料还可能由配方关联得出。COOK_TODAY 的 value 是具体物品 ID，数据包中写它不会自动变成每天变化的“今日料理变量”；自动日任务生成器会把当日 ID 写进去。

当前需注意的底层边界：REACH_LEVEL 不在 prepare 中自动回填既有等级，新增此挑战后已达标者可能需下一次等级事件；COMPLETE_CHALLENGE 会在 prepare 中补齐已有完成，但直接完成结算主要沿 prerequisite 链推进，无前置或不同前置的独立引用可能要等后续事件。这些属于底层边界，不应通过 GUI 假造完成来补偿。

### 里程碑

每个里程碑只有一项累计条件、正数 threshold、可选 badge。没有多目标组合、挑战前置、接取、时限或经验/物品奖励。制作、发现、精通、等级、分餐等相关事件会评估，登录及重载也核对已有累计统计；已完成永不因日刷新撤回。

目前资源文件共有 19 个里程碑，默认环境中按条件可用性显示：

| 累计内容 | 门槛 | 其中解锁徽章的门槛 |
| --- | --- | --- |
| 制作总份数 MEALS_PREPARED | 1、10、100、1000 | 无 |
| 制作不同料理 UNIQUE_RECIPES_PREPARED | 5、10、25、50 | 无 |
| 已发现料理 RECIPES_DISCOVERED | 25、50 | 50：烹饪探索者 |
| 已精通料理 RECIPES_MASTERED | 10、25 | 25：master_chef |
| 已发现食材 INGREDIENTS_DISCOVERED | 50 | 50：食材探索者 |
| 烹饪等级 COOKING_LEVEL | 1、10、25 | 分别 home_cook、sous_chef、chef |
| 分餐份数 PORTIONS_SERVED | 8、100 | 100：宴席大师 |
| 指定方式制作份数 METHOD_PREPARATIONS | 农夫乐事砧板 50 | 无 |

METHOD_PREPARATIONS 的 JSON 必须另带 method。当前没有该烹饪方式时，相应里程碑不发送。这里“制作份数”不是取出鼠标点击次数，沿个人累计统计及 MethodRecord.preparations 的产出计数。

界面显示里程碑名称用 cuisinejournal.milestone.<namespace>.<path>，条件说明用condition翻译键及amount门槛；当前progress是实际累计值、reward是明确的奖励徽章ID。不能把amount当已完成数量，也不能按同名猜徽章，挑战页已使用这些字段。

### 徽章与佩戴

当前有 8 个内置徽章：home_cook、sous_chef、chef、master_chef、culinary_explorer、feast_master、ingredient_explorer、iron_chef。前 7 个来自里程碑，iron_chef 来自挑战。定义只包含物品图标，解锁条件由来源目标负责。没有等级或属性奖励，没有同名称号的第二份条件。

- 未获得：可显示图标、名称和已同步的来源条件，不能佩戴。
- 已获得：永久收藏，可佩戴一个；佩戴新徽章替换旧徽章。
- 当前佩戴：从 SyncStatistics.selectedBadge 读取，null 表示未佩戴。
- 佩戴请求：Network.toServer(new Packets.SetPreference(Packets.SetPreference.Kind.BADGE, badgeId))。取消时badgeId=null，服务器检查是否已拥有，返回新的统计数据。
- 徽章名称：cuisinejournal.badge.<namespace>.<path>；图标读 Achievement.icon，是物品 ID。定义或图标缺失时服务端回退 minecraft:gold_nugget。
- 徽章按完整资源ID比较和关联，不按显示名称或仅path合并其他命名空间的内容。
- 不自动佩戴“最高”徽章。未佩戴表现沿现界面，不再以旧待办宣称入口未完成。

SyncStatistics 的徽章集合包含所有定义、目标可发放的徽章及历史已拥有徽章；一枚徽章可由多处解锁，但当前只同步一个来源翻译键。来源为空可能是历史荣誉或独立定义，不能显示空白为“无法获得”。

### 奖励与展示边界

挑战完成后由服务端自动发奖并记录完成，不存在待领取状态。每日任务每个周期可奖励一次；一次性挑战只奖一次；重复获得徽章不会变成多枚收藏。里程碑只有可选荣誉，无经济奖励。

奖励 JSON 支持 cooking_xp、vanilla_xp、mastery_xp、mastery_dish、badge、advancement、items 和 commands。mastery_xp 必须写明确料理目标；items 为物品 ID 及数量字符串，不是任意 NBT 奖励；commands 受 challenges.allowCommandRewards 控制，默认关闭。

当前ChallengeView发cookingXp、badge和itemRewards。itemRewards为服务端规范化的“物品ID 数量”列表，ChallengesPage.items/rewards已绘制图标/数量并提供悬停资料；缺失物品发奖时跳过，不以本地默认物品替代承诺。ServerPacketHandler使用ChallengeRewards.cookingXp计算后发送cookingXp，已含实际倍率与progression.enabled开关，并限制为网络整数范围；不再把它当定义基础经验或列为有效经验未同步。原版经验、熟练度、进度和命令等奖励明细仍未同步，客户端不能读本地包冒充服务端结果。

### 网络与客户端入口

打开图鉴已有 RequestProfile 请求，服务端 sendEverything 会发送规则、目录、个人档案、今日料理、扩展列表和统计。GUI 复用 ClientJournalState，不另建立目标判定。

| 包/客户端入口 | 需要读取的内容 |
| --- | --- |
| JournalSyncPackets.Progress → ClientJournalState.challenges() | 每日、永久、链式挑战列表；原SyncJournalExtras仅留兼容构造/测试，正式发送已拆分 |
| SyncStatistics → ClientJournalState.statistics() | 里程碑、徽章、当前佩戴，以及原统计数据 |
| SyncTodayDish → ClientJournalState.todayDish() | day 和可空的料理 ID |
| SyncProfile → badges()/milestones() | 已拥有/已完成 ID 补充信息，不含完整定义 |
| ClientJournalState.revision() | 数据变化后重建页面缓存；不要每帧发 RequestProfile |

挑战视图实际字段：

```java
ChallengeView(
    ResourceLocation id, String titleKey, String descriptionKey,
    boolean completed, int progress, int goal, String scope,
    boolean manual, boolean started, boolean failed, boolean locked,
    long remainingTicks, List<ObjectiveView> objectives,
    int cookingXp, ResourceLocation badge, List<String> itemRewards
)
ObjectiveView(String type, String value, int progress, int goal, boolean unique)
```

- progress/goal 只代表第一个目标，是旧概要。多目标界面以 objectives 为准，不能把第一行达标当成整项完成。
- scope是DAILY/PERMANENT/CHAIN。使用它分组，不靠ID前缀判断所有任务；自动目标ID统一cuisinejournal:daily/<day>_<shape>。
- manual=false、failed=false、remainingTicks=-1 是当前固定兼容值；不渲染旧操作。StartChallenge 包仍保留但服务端返回无效，GUI 不发送。
- ObjectiveView已有unique，ServerPacketHandler传objective.uniqueOnly()，ChallengesPage据此区分“种”与重复份/次；不再列为缺失字段，具体重复单位仍按目标类型判定。
- titleKey/descriptionKey 是翻译键。生成任务共用“每日挑战”和“完成本期烹饪目标”，具体内容来自 objectives。物品、灶具、类别、标签等 value 应按类型解析成名称，保留 ID 作为缺失翻译时的兜底。
- 当前通用 objective 翻译不少没有参数占位，单纯传入 value 不一定显示目标名称；需要由界面明确组合目标名称与进度。

里程碑/徽章视图：

```java
Achievement(ResourceLocation id, boolean badge, boolean earned,
            String condition, long amount, ResourceLocation icon,
            long progress, ResourceLocation reward)
```

badge=false时condition为累计指标翻译键、amount为门槛、progress为当前值、reward为奖励徽章ID（可空），icon当前为空；badge=true时condition仅某一来源翻译键、amount/progress为0、reward为空、icon为物品图标。服务端已发送progress/reward，挑战页已读取进度与奖励徽章。earned分别指里程碑完成/徽章已得；SyncStatistics.selectedBadge为当前佩戴徽章ID，可空。

今日料理视图只有 day 和 dish，不含奖励数值或关闭原因。未收到包与收到 dish=null 要分开：前者是数据未同步；后者可能是功能关闭、候选池为空或当前无合法菜。现有数据不能准确区分后几种原因，空态不应擅自断言。

challenge 列表为空也不能仅凭它判定功能关闭：可能无有效任务、模板不可用或定义被筛掉。服务端逐类发送数据，短暂不齐时保持加载/空态，不用旧世界缓存拼接；退出世界沿 ClientJournalState 的清理流程。挑战推进与日刷新服务端会同步，客户端接收新快照后更新。

### 数据包示例

文件ID来自路径，不能在GUI里重写身份。路径为data/<namespace>/cuisinejournal/challenges|milestones|badges/<path>.json。以下是配置示例，不是新增内置内容：

data/mypack/cuisinejournal/challenges/daily_variety.json：

```json
{
  "scope": "DAILY",
  "objectives": [
    { "type": "COOK_RECIPE", "amount": 5, "unique": true }
  ],
  "rewards": { "cooking_xp": 40, "items": ["minecraft:emerald 2", "minecraft:bread 3"] }
}
```

data/mypack/cuisinejournal/challenges/perfect_followup.json：

```json
{
  "scope": "CHAIN",
  "prerequisite": "cuisinejournal:iron_chef",
  "hidden": false,
  "objectives": [
    { "type": "COOK_PERFECT", "amount": 5 }
  ],
  "rewards": { "cooking_xp": 100, "badge": "mypack:perfect_cook", "items": ["minecraft:diamond 1"] }
}
```

data/mypack/cuisinejournal/milestones/hundred_meals.json：

```json
{
  "type": "MEALS_PREPARED",
  "threshold": 100,
  "badge": "mypack:patient_cook"
}
```

data/mypack/cuisinejournal/badges/patient_cook.json：

```json
{ "icon": "minecraft:bread" }
```

挑战名称/说明分别使用 cuisinejournal.challenge.mypack.<path>.title/.description；里程碑和徽章名称分别使用 cuisinejournal.milestone.mypack.<path>、cuisinejournal.badge.mypack.<path>。多目标按 AND；PERMANENT 是一次完成，DAILY 是每个日历周期一次；不要再配置 WEEKLY、manual 或 duration_seconds。游戏数据包可提供目标 JSON，但显示名称的翻译仍需客户端可用的语言资源。

自动生成的每日挑战共用奖励可以这样配置在 config/cuisinejournal-server.toml：


    [challenges]
    dailyRewardItems = ["minecraft:emerald 2", "minecraft:bread 3"]

每个抽中的每日任务完成后都发上述两种物品，并同时发原有基础40经验；当天已生成任务保持原奖励。单个挑战的独立奖励仍通过其数据包JSON配置。

### GUI 数据已有能力与剩余缺口

已按当前Packets/ServerPacketHandler/ChallengesPage核对：里程碑progress/reward、ObjectiveView.unique、有效cookingXp已同步，物品奖励图标已绘制；这些移出缺口。下表只保剩余视图能力，不代表新增实现授权。

| 想要的界面内容 | 当前缺失 | 当前可用边界 |
| --- | --- | --- |
| 解锁链、前置跳转、锁定原因 | prerequisite ID、可用性原因 | 当前只有 locked，可给通用不可用状态 |
| 其他奖励元数据 | 原版经验/熟练度/进度/命令等完整明细 | 已有有效cookingXp、可选badge、itemRewards及物品图标，不再要求补同样字段 |
| 自定义挑战图标 | icon 字段 | 只能采用界面统一图标或从目标推导，不能宣称数据包已有配置 |
| 徽章的全部获取途径 | 来源 ID/类型列表 | 当前只有一个来源翻译键 |
| 今日料理关闭与无候选的不同空态 | enabled/selectionStatus | 当前 dish=null 不含原因 |
| 历史每日任务、完成日期、领取记录 | 对应历史视图字段 | 当前只有当前任务列表，奖励自动发放，无领取步骤 |

## 宿主与补充数据契约

### 魔法食事工作站契约

魔法食事B004工作站沿现有compat/workstations数据协议，未新增配置键/网络字段：奥术厨锅farmers_spell:alchemist_pot使用cooking_pot适配器，method=farmers_spell:alchemist_cooking，允许其专用配方及farmersdelight:cooking；原料0..5/锅内6/容器7/输出8，独立scrollHandler（菜单槽9）不计食材。炽魂炉灶farmers_spell:cinderous_stove沿stove/营火配方。移除模组时required_mods规则跳过，用户同块覆盖规则仍按既有优先级生效。

### 森罗专项数据契约

- 可选宿主读取：厨房quality仅存在时走品质路线，0/1/2/3对应5/4/2/0星；无键仍是普通百味纪成长。图鉴不保存厨房最高品质；酿造个人记录新增highestBrewedStars，旧档缺字段为-1。
- 真实来源键cuisinejournal:ingredient_sources为最多128项物品/标签ID快照；cuisinejournal:cocktail_stars保存鸡尾酒继承星级；cuisinejournal:kaleidoscope_serving保存放置料理每口营养。显式food定义flavor_sources仍优先于实际来源快照。没有组合条件字段，也没有本批新增的药水转风味配置。
- 方块实体cuisinejournal:kaleidoscope_production保存贡献账本、原液来源与输出批次；蒸笼/茶壶内部原料的cuisinejournal:kaleidoscope_input_owners随宿主搬运保归属。调酒器cuisinejournal:kaleidoscope_shaker随Storage/Result搬运。放置杯堆/料理的完整变体保存在维度SavedData cuisinejournal_placed_food_variants。
- 真实变体提示按需请求，仅匹配服务端背包、当前容器和已吃记录里的实际ItemStack；SNBT最多16384字符、客户端缓存有界。客户端/服务端必须使用匹配协议，协议号唯一查CURRENT_STATUS验证块。
- 加速复用现有成长数值，森罗等待设备取8格内玩家最高加速，20tick更新一次缓存；不修改调酒窗口或世界randomTickSpeed。无新增GUI配置。

### Let's Do专项数据契约

- 可选宿主按本批实际1.20.1 Forge版本，入口与证据见docs/compatibility/B005_ADDED.md；无新增硬前置、配置项或网络结构。协议与产物只查CURRENT_STATUS验证块。
- 年份酒读原生Year及WineYears配置：默认0/6/12/18/24/30/66年映射0/1/2/3/4/5/5星完美，物品动态显示；不从普通熟练度/技能上限或随机完美重写，不因显示星级再次加营养。今日可抽该菜但不额外改年份品质；原生药水按宿主喝下时属性执行、不受槽计时或库存酒瓶继续陈化影响；风味才进入槽内库存。
- 啤酒读brewery.beer_quality=1..3映射1/3/5星，单制作者成功生产整批定品质；完美概率用映射星基准及现有技能/招牌/今日20百分点/批量扣减/保底。今日与抽中完美各升一原生品质、封顶3；顶档完美保标记、不额外再＋1星或套普通营养奖励；盛杯不重抽。个人最高复用highestBrewedStars，不新增存档字段。
- 原生动态效果保留宿主执行及原时长，受foodEffects与旧Valheim preserve开关共同控制，不进入有限库存；风味独立进入库存。Brewery独立醉酒不当槽内库存；原生关闭阻累计、停醉/断片判断、清旧计数及效果并同步。混茶壶与Flask明确不当食品接管；普通茶进饮品槽。
- 真实原料来源复用cuisinejournal:ingredient_sources（最多128项），Let's Do每口原始营养用cuisinejournal:lets_do_serving；世界制胚/抹酱/火锅以既有维度cuisinejournal_placed_food_variants保存实际物品，cuisinejournal:lets_do_work记录过程贡献。葡萄汁转内部储量的贡献/来源保方块实体cuisinejournal:lets_do_juice；不等于跨机器通用供液。
- 厨具燃料/容器/加热槽按实际布局排除，搅拌/绞肉保手动动作，等待加速只改正向制作进度；精酿小游戏和世界年份不受烹饪加速。切片/摆放/取回/下一口继承，不重复随机或发成长；Camping烤架按营火，不读取GrilledNutrition/GrilledSaturation改百味纪属性，也不修改宿主倍率。

### 统一续批边界

此前续批104组/291候选为历史子集，番茄/Compat兜底后当前规则见候选盘点第11节；一对多甜椒和CrockPot烟熏茄子由UnificationIngredientAliases接，不把所有颜色或整/切烟熏物品合一。茶种植保留，鲜/干/成品茶分别转代表，6份袋不并9份；茶加工块8片环形、混叶仍4干绿+4干红，掉落保实际投入与成品损耗，见候选盘点10节。固定烤串用GrillingRecipes读取宿主grilling表，原料selector补丁独立；无新增网络包/GUI设置。

### 放置火鸡

秋原TurkeyBlock/CookedTurkeyBlock沿宿主protected getLeg解析实际生/熟turkey_piece，剩余5-chunks；PlacedServingRelations建立整只到五份关系，普通放置印记/切份继承沿既有账本。直接吃走普通准入，持farmersdelight:tools/knives在装农夫时只切不吃，缺农夫时沿宿主AxeItem规则；切制不因槽满受阻、不重记制作，不是蛋糕片回血成员。原生每口营养及负面药水保持，Vanilla原生数值/Valheim映射沿通用。

### 奶昔边界

用户接受最多三瓶不做批量完美率扣减，保逐瓶现有品质结算；G6实际交付前结算风险接受当前限制，未新增修复。回倒G4已获澄清并修：SavedData cuisinejournal_milkshake_cauldrons每位置改contents列表，最多三份，每份分别存batch或original；原聚合portions/batch/original仍可读取后按份迁移。回倒成品追加独立存储份，取走只减对应前份，保未领制作批次；牛奶少一份则仅余份贡献进入下一次加工，补奶不恢复已消耗贡献。液体本身同质，归属按投入顺序对账，不修改宿主混液算法或逐瓶品质。

### 蛋糕与蛋糕片

蛋糕常驻兼容CakeFoodCompat：无切片CakeBlock保自己的物品身份并供每口营养，真实切片优先；内部/已吃变体可带cuisinejournal:cake_serving compound（nutrition/saturation），只存每口原值，经既有变体提示与食物存档携带，不改物品注册或网络结构。原版Blocks.CAKE保旧特例，未知自定义蛋糕原生动作/数值及跨坐标搬移印记须专项，不把父类识别视为全机制支持。

蛋糕片仅Valheim模式固定额外瞬回2生命值，Vanilla无额外回血/加成提示，由成功进食回调结算一次，不走药水等级或星级倍率，无新增设置。物品标签cuisinejournal:instant_healing_cake_slices（data/cuisinejournal/tags/items/instant_healing_cake_slices.json）引用可选forge:cake_slices并补已查蛋糕片ID；数据包可追加成员或replace覆盖。只影响此加成，不扩forge原标签成员或合成接受范围；放置食用以解析出的真实切片ID判断，整块自身不自动视为切片。提示与DirectFoodHealing共用数值，加到原生直接回血/瞬间治疗合计中，无网络/存档变化。

### 食物槽纠正与灶具显示

物品标签cuisinejournal:food_slot_overrides只列本次六ID（两种大骨汤/九头蛇汤/两棒棒糖/儿童刨冰盖饭），默认判food优先于饮用动作，显式foods.slot_type仍优先；客户端随服务端标签同步，不修改原生动画/食物药水。

制作方式图标共用WorkstationDisplay和打包cuisinejournal/workstation_display.json候选列表，由JournalText.station/method供统计/详情/追踪；取已注册首选，名称优先CJ方法译名再取该图标物品原生名，未知方式同ID回退。不新增网络/存档；脚本tools/audit_workstation_display.py可按安装JAR重做模型/中英文核查和docs/compatibility/灶具图标与翻译统计.md；模型存在不当实机渲染通过。

### 统一追加字段

统一规则的追加字段keep_recipe_items为items子集，只保指定物品的获得配方，和keep_recipes整组保渠道不同；沿既有kept_recipe_outputs提交策略，无网络/玩家存档变更。首轮追加79组/215候选及作物/奶油入口边界查候选盘点第7节，续批总量与有效规则查第9节；缺ID跳过不生成虚构物品。

源码按宿主/职责拆分，入口与命名见docs/ARCHITECTURE.md。首发前已统一自有NBT为cuisinejournal:<snake_case>，不兼容旧测试档；原生宿主键保持不动。工作站资源已用宿主名，批次编号只在调查/测试中使用。

### 热食、辣条与续批厨具

- 未新增GUI设置/硬前置/网络字段。辣条同款成长仅在DISH查询时汇总真实家族成员熟练度；各ID存档不复制，不合并发现/制作次数，SUBCATEGORY照各自小类。家族取宿主升级链，注册声明等级相符的同款相邻档作宿主查找拼写缺陷的有限回退。
- 热食已吃记录快照用cuisinejournal:grilling_heat_multiplier，取吃下时宿主饱和倍率；不改背包物品冷却计时。原生持续效果沿已确认热食专项加长一次，调料不再翻倍；不再捕获进库存，CJ风味独立。Vanilla保宿主算法。
- 动态串/饮品与条件锅实际投入沿cuisinejournal:ingredient_sources，不新增组合风味或默认风味；真实NBT提示复用已有受校验变体请求。
- 冷甜品桶在方块实体cuisinejournal:cosmopolitan_tub保存液体、原物品、来源和贡献，转换才设production，原成品倒回/取出按存储继承；手动烤架cuisinejournal:manual_grill_owner记录真实投料者，熟成品沿cuisinejournal:pending_production结算，模拟取出不写批次，空手领取清pending避免后续拾取重奖。
- 便携摇杯cuisinejournal:kitchen_karrot_shaker记录实际完成操作者及五原料快照，生成时只在当前输入吻合时认领该操作者，来源按当次实际五项；喝啤酒调酒台cuisinejournal:drink_beer_mix_owner保存本次投料者，连续加香料合成一份pending，最终非模拟取出才结算。原成品仅放回/取出不算新调酒。
- 工作站声明位于compat/workstations/<宿主ID>.json，required_mods与覆盖机制不变；计时加速仅已接的宿主进度写入，不改翻面/烧焦窗口。丰饶缺失的milling/fermenting原生配方不由百味纪补造。

## 代码定位与验收

- 鱿鱼掉落冲突补丁为常驻，无新增配置/协议：只修已知多元/饺子原表且来自mod资源，补缺失掉落池；外部自定义表或上游改表跳过。原统一开关只决定已有去重/代表替换，关闭统一仍恢复原版墨囊和多元掉落；不启用饺子坏GLM或补造矿工熟品资源。范围与实机步骤见docs/compatibility/鱿鱼标签与配方整理.md末节，构建查CURRENT_STATUS。

- 挑战定义/目标/状态/结算：src/main/java/com/chi__you/cuisinejournal/challenge/。
- 每日生成与世界历史：DailyRotation、DailyChallengeHistory；统一日期：culinary/DailyCalendar、CulinaryClock。
- 今日料理：progression/TodayDishService、TodayDishSelection。
- 里程碑：milestone/Milestone、MilestoneTracker；徽章元数据：culinary/CulinaryBadge，佩戴记录：CulinaryProfile。
- 包结构：network/Packets；组装和佩戴处理：network/ServerPacketHandler；接收与缓存：client/ClientPacketHandler、ClientJournalState。
- 现有 GUI 参考：client/journal/page/ChallengesPage、StatisticsPage，client/journal/JournalScreen、StatsSidePanel。这些文件里的旧每周筛选、接取或倒计时表现不代表当前机制。
- 内置定义：src/main/resources/data/cuisinejournal/cuisinejournal/下的challenges、milestones、badges。图标/徽章名查当前语言文件，不拿历史称号表当解锁条件。

GUI定向验收至少覆盖：多目标只完成第一项；单日跨刷新、睡醒及重启；每日与一次性同名但独立；模式关闭使任务隐藏/锁定；无合法今日料理；徽章未拥有/佩戴/取消；里程碑未达标和已达标；一次批量产出与五次独立制作；无倒计时、接取、领取或每周残留。多人沿用户延期边界；灶具名/图标已配置，待实机，不再列为未实现。

## 首发数据契约

用户确认发布前无需兼容旧测试档；本次整理不是测试实例重置/部署。不要把整理前物品NBT、存档或外部覆盖包与本版混用并推定品质/归属必然无损。

| 数据 | 当前入口/名称 | 规则 |
| --- | --- | --- |
| 物品品质 | MealStamp.ROOT = cuisinejournal:quality | 品质与制作者独立复合标签；其他代码引用此常量，不各写一份根键 |
| 实际来源 | IngredientProvenance.KEY = cuisinejournal:ingredient_sources | 共享最多128项物品/标签快照，不用宿主名代表全模组 |
| 未领取生产 | ProductionTracker.PENDING = cuisinejournal:pending_production | 与品质/来源分开；批次数量辅助键cuisinejournal:production_batch_count |
| 通用设备账本 | ProductionTracker.LEDGER = cuisinejournal:production | 同包设备复用，不复制字面量；其他设备专用根键均有明确宿主/用途 |
| 食用状态 | CuisineFoodState.ROOT = cuisinejournal:food_state | 保食物格式版本与未来格式只读保护；风味库存不再有旧混合来源迁移 |
| 放置完整变体 | PlacedFoodVariants / cuisinejournal_placed_food_variants.dat | 共用位置/杯堆/原物品，不再命名为某一个宿主 |
| 佩戴荣誉 | profile.selectedBadge / SyncStatistics.selectedBadge / SetPreference.Kind.BADGE | 只认完整徽章ID及已获归属，不保存第二份title或补发旧称号 |
| 菜单上下文设置 | compatibility.workstationContextTicks | 默认20tick，范围1..200；只控制菜单/加速上下文，不决定生产归属；已移除旧automation.attributionWindowTicks |
| 食物模式 | common.foodSystem | 唯一模式入口，valheim/vanilla；sol_valheim冲突回退不变，无experimentalRuntime隐藏门槛 |
| 宿主工作站文件 | compat/workstations/<宿主ID>.json | 八个旧b006文件已改宿主名；required_mods/priority/能力不变，旧路径不提供别名 |

内置数据/翻译使用cuisinejournal身份，删除旧mealmastery资源别名和重复语言键；宿主自有quality/BrewLevel/Effects/HotUntil等不改。旧测试版技能节点退款迁移与旧称号补发迁移已移除，现行技能/荣誉规则不变。网络协议与最终构建只查CURRENT_STATUS验证块。

公开CuisineFoodAPI仍保留CYS正在调用的类名/签名；这是现用跨项目接口，不是旧测试档兼容。独立food-interaction-recipes运行源码、许可证、署名与真实调查历史保持独立。
