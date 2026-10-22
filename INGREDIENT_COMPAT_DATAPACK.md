# 百味纪食材兼容数据包

更新：2026-10-10。本文件描述已实现的食材索引规则；料理排除、料理分类成员迁移和正式效果传播另行实现。

merge_tags 现作为「一个条目包含多个真实标签」的特殊标签使用。新增保留原始标签成员以及料理/配方/原料槽/标签分支对应关系，网络同步给 GUI；成员和料理并集去重，不修改配方限制。协议 14。明确分组不受单成员自动溶解影响，空组仍隐藏。IngredientView.tagSources 保存真实标签成员，tagUses 保存料理/配方/原料槽/真实标签/接受物品。风味仅共用元数据，尚无运行时传播。

## 整合包禁用物品

物品标签 `cuisinejournal:disabled_items` 用于明确声明整合包已停用的内容。与是否开启统一模式、是否安装JEI或SOL无关；默认空，不自动读取SOL加血黑名单、JEI隐藏列表或创造栏缺席情况。

加入后从百味纪料理/食材图鉴、标签成员和配方原料显示候选中过滤，显式料理收录也不能绕过。自动交互展示同时检查来源方块物品、实际来源物品和产物，任一被禁用就不生成；不删除作者的普通配方，也不修改真正的方块互动或食用属性。历史记录保留，移出标签后 `/reload` 恢复可见。这个标签不会自动把原物品从JEI普通列表隐藏，整合包已有隐藏脚本仍需保留。

数据包写法：`data/cuisinejournal/tags/items/disabled_items.json`，可选模组物品使用 `required:false`：

```json
{
  "replace": false,
  "values": [
    {"id": "collectorsreap:portobello_quiche", "required": false},
    {"id": "collectorsreap:portobello_quiche_slice", "required": false}
  ]
}
```

已有KubeJS清理脚本可直接复用同一份 `removedFoods` 数组。在该数组可访问的作用域内，将下面这行加进现有的 `ServerEvents.tags('item', event => { ... })` 回调末尾：

```javascript
event.add('cuisinejournal:disabled_items', removedFoods);
```

务必放在 `removedFoods.forEach(id => event.removeAllTagsFrom(id))` 等清空标签操作之后，且没有后续脚本再移除该标签。数组条目应为有效物品ID；通配清单应先通过KubeJS展开成实际物品，不能把SOL通配字符串原样当标签成员。可选模组成员也可采用上面的 `required:false` 数据包写法。

农场物语的Collectors Reap清理脚本已有上述清空标签回调，只需在其中最后追加 `event.add`，无需复制或由百味纪硬编码名单。本轮未修改实例脚本。

独立 `food-interaction-recipes` 也识别同一标签，不需要安装百味纪；双装时由独立版负责生成，所以也须更新独立版。检查 `food-interaction-recipes-audit.tsv` 中的 `skipped_disabled_item` 可确认自动展示被跳过。

## 文件与覆盖

服务端读取 data/<namespace>/cuisinejournal/compat/*.json，每次数据包重载重建索引。一个模组可以使用一个兼容包，包内按功能分文件。同资源路径遵循 Minecraft 的高优先级数据包整文件覆盖；不同路径按 priority 从小到大、再按资源 ID 排序。priority 越大越后应用。enabled=false 可禁用被覆盖文件；required_mods 缺任一模组则跳过整文件。

最终顺序是自动规则、数据包规则、本地配置。每次从空规则重建，删除兼容文件不会残留旧隐藏或旧别名。错误文件整份跳过并记录日志，不允许部分应用。

名称、类型和风味的显式 compatOverrides 优先于旧本地字段。null 撤销本对象的覆盖，随后仍可回退旧本地字段；风味 source=false 是明确禁用。旧 processingRecipeTypes 的本地完整列表继续保留，已有列表的作者若希望交给数据包管理，需移除该字段或设为 null。

## 已实现字段

| 字段 | 内容 |
| --- | --- |
| required_mods | 必需模组 ID 数组 |
| priority / enabled | 文件顺序与启用开关 |
| hidden_tags | 隐藏标签条目与所属标签链接，保留明确物品配方 |
| dissolve_tags | 不显示标签，把使用该标签的料理关联到该标签实际成员 |
| keep_tags | 撤销指定标签的隐藏、溶解、合并或并入物品；同时退出同成员自动合并 |
| tag_names | 标签 ID 到显示文字的映射；null 删除覆盖，恢复默认语言键 |
| merge_tags | id、tags、可选 name/types/flavor；多个真实标签合并成一个稳定图鉴条目 |
| merge_tag_additions | id/tags 对象数组，向已经定义的组追加真实标签，继承名称、类型和风味，不覆盖其它追加 |
| remove_merge_tags | 按合并条目 ID 撤销整个合并组 |
| tag_items | tag/into 对象数组；标签关联料理并入指定物品条目 |
| item_aliases | item/into 对象数组；物品形态合并到目标物品条目 |
| remove_item_aliases | 撤销指定物品别名 |
| excluded_items / included_items | 隐藏食材物品/撤销低优先级食材隐藏；不会改变料理资格 |
| item_types / tag_types | ID 到类型 ID 数组；null 删除覆盖 |
| item_flavor / tag_flavor | ID 到 source/effect 对象；source=false 禁用风味展示，null 删除覆盖 |
| processing_recipe_types | add/remove/reset 数组；添加加工类型、取消加工类型、撤销类型覆盖 |
| portion_origins | 分装成品 ID 到本体 ID 的映射；null 撤销低优先级显式关联，不改变食材条目身份 |

portion_origins 在目录重建时生效：成品必须有未被排除的加工配方关联到本体，本体必须有有效的非加工制作配方。已有独立烹饪配方的成品不会被覆盖；循环关系不生效。成品关联本体的制作记录、熟练度和配方，食用记录保持独立，切制不重复计制作次数。标准带 bites 属性的方块切片可自动识别，无须逐个配置；有真实本体配方时自动补收录未列入分类的切片。砧板多产物逐个读取，非食品副产物不会因此收录。

例如：{"portion_origins":{"aquamirae_delight:fishermans_delicacy_slice":"aquamirae_delight:fishermans_delicacy"}}。内置特殊关联位于 compat/portions/ 各模组文件，继续支持 required_mods、priority 和本地 compatOverrides 覆盖。

类型使用现有 cuisinejournal:grain、fruit、vegetable、meat、egg、dairy、other 等 ID。flavor 仅为确认过的展示数据，没有效果递归或运行时施加。

同一文件内标签操作顺序：hidden、dissolve、keep、remove_merge、tag_items、merge、merge_tag_additions。建议同一目标只写一种操作。加工操作顺序：add、remove、reset。

merge_tag_additions 的目标组需在该规则之前定义；建议公共 merge_tags 文件 priority=0、模组追加 priority=100，本地追加仍最后执行。未定义目标跳过并记录警告，不凭空创建组；required_mods 只影响当前追加文件。追加可重复，标签不重复显示；更高优先级 merge_tags 是完整重定义，会撤销较早追加。remove_merge_tags 撤销组及全部追加，keep_tags 可以单独恢复来源。现蛋类定义在 egg_group.json，aether_redux_eggs.json 只追加该模组的蓝莓派用蛋标签。

未手动分组的标签若真实成员全集完全相同（顺序无关，至少两个成员），目录重建时自动合成一个条目。只比较真实成员，不以排除/形态合并后的网格作为判断依据；空标签不合并，普通单成员仍溶解。明确 keep_tags 或手动组不会自动被并入其它组；名称、类型、风味显式配置冲突时保持独立。代表条目优先使用有配置名称的标签，然后 forge 命名空间，再按 ID 稳定排序。原始标签 ID、成员、各配方分支全部保留，料理与所属标签去重；audit 中被并入来源记录 merged_identical。实际 Minecraft 标签和配方不修改，标签成员变化后自动重新判断。

## 示例

    {
      "required_mods": ["farmersdelight"],
      "priority": 100,
      "hidden_tags": ["minecraft:flowers"],
      "dissolve_tags": ["forge:crops/wheat"],
      "tag_names": {"forge:eggs": "蛋类"},
      "merge_tags": [{
        "id": "my_pack:ingredient/meat",
        "name": "肉类",
        "tags": ["forge:raw_meat", "brewinandchewin:raw_meats"],
        "types": ["cuisinejournal:meat"],
        "flavor": {"source": true, "effect": "minecraft:strength"}
      }],
      "item_aliases": [{"item": "farmersdelight:bacon", "into": "minecraft:porkchop"}],
      "processing_recipe_types": {"add": ["farmersdelight:cutting"]}
    }

高优先级包可以单独写 keep_tags、remove_merge_tags、remove_item_aliases、included_items，或者覆盖原文件设置 enabled=false。名称沿用现有列表/详情/所属标签位置，不新增 GUI 控件。数据包文字是字面名称；客户端语言键仍为未配置名称的回退。

## 本地配置

config/cuisinejournal-ingredients.json 保留旧 excluded、types、flavor、tagFlavor 字段。新增 compatOverrides 对象，使用上述同一套字段，并在所有数据包之后应用。

    {
      "compatOverrides": {
        "keep_tags": ["minecraft:flowers"],
        "remove_item_aliases": ["farmersdelight:bacon"],
        "tag_names": {"forge:eggs": "我的蛋类"},
        "processing_recipe_types": {"remove": ["farmersdelight:cutting"]}
      }
    }

旧 processingRecipeTypes 若明确存在，则视为完整本地覆盖列表（空数组表示没有加工类型）。未设置/设 null 时使用自动默认与数据包增删。compatOverrides.processing_recipe_types 的 add/remove 优先于该旧列表。无需删除或重写现有用户配置。

## 索引和进度

规则只改食材图鉴身份，不改 Minecraft 标签、配方、料理类别、料理熟练点或星级。同一道菜多配方去重；溶解只展开该标签与该原料槽相交的成员。c/forge 同路径自动合并继续保留；单成员标签自动溶解。

物品别名支持链式解析；循环会警告并保留原物品身份。不存在/被排除的别名目标不会制造虚假条目。合并后的发现状态取任一源记录，次数求和、方法去重、首日取最早、末日取最晚，Long.MIN_VALUE 保留为未使用标记。标签并入物品也汇总其实际成员记录。存档一直保存原物品记录，撤销规则可重新显示原记录。

## 排查与验证

/mealmastery audit ingredients 导出 config/cuisinejournal-ingredients-audit.tsv。保留旧列，追加 result、final_entry、rules；包含最终条目及原始条目（包括 hidden、merged、dissolved 等），规则来源记录包 ID、JSON 路径和操作，也记录旧本地覆盖字段。

网络协议为 12，客户端和服务器需使用相同版本。新增名称字段已做读写与发现刷新回归。自动测试覆盖规则冲突、撤销、缺模组、错误文件、循环、混合槽、去重、合并记录和配置列表。游戏重载、名称显示及多人行为仍需实机验收。
