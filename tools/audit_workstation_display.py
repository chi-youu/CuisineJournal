"""Inspect bundled method declarations and installed assets without launching Minecraft."""
import argparse
import json
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def scan(mods):
    items, zh, en = {}, {}, {}
    for jar in sorted(mods.glob("*.jar")):
        if jar.name.startswith("cuisinejournal-"):
            continue
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                parts = name.split("/")
                if len(parts) == 5 and parts[0] == "assets" and parts[2:4] == ["models", "item"] and name.endswith(".json"):
                    items[parts[1] + ":" + parts[4][:-5]] = jar.name
                if name.endswith(("/lang/zh_cn.json", "/lang/en_us.json")):
                    try:
                        (zh if name.endswith("zh_cn.json") else en).update(json.loads(archive.read(name)))
                    except (ValueError, UnicodeError):
                        pass
    return items, zh, en


def declarations():
    methods = defaultdict(list)
    for path in sorted((ROOT / "src/main/resources/data/cuisinejournal/cuisinejournal/compat/workstations").glob("*.json")):
        for row in json.loads(path.read_text(encoding="utf-8")).get("workstations", []):
            if row.get("method") and row["block"] not in methods[row["method"]]:
                methods[row["method"]].append(row["block"])
    return methods


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mods", type=Path, required=True)
    parser.add_argument("--candidates", action="store_true")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    items, zh, en = scan(args.mods)
    if args.candidates:
        for item in items:
            if item.split(":")[0] in {"kaleidoscope_tavern", "kaleidoscope_grilling", "kaleidoscope_chinesefood", "kaleidoscope_world_liquor", "braziliandelight", "cosmopolitan", "barbequesdelight", "drinkbeer", "kitchenkarrot"} and any(word in item for word in ("pot", "bar", "grill", "tub", "skew", "shak", "mix", "freez", "pickle")):
                key = item.replace(":", ".")
                print(item, zh.get("block." + key, zh.get("item." + key, "")))
        return
    mapping = json.loads((ROOT / "src/main/resources/cuisinejournal/workstation_display.json").read_text(encoding="utf-8"))
    own = json.loads((ROOT / "src/main/resources/assets/cuisinejournal/lang/zh_cn.json").read_text(encoding="utf-8"))
    methods = declarations()
    for method in mapping:
        methods.setdefault(method, [])
    rows = []
    for method in sorted(methods):
        choices = mapping.get(method, [method])
        icon = next((item for item in choices if item.startswith("minecraft:") or item in items), "")
        key = "cuisinejournal.method." + method.replace(":", ".")
        native_key = icon.replace(":", ".")
        label = own.get(key, zh.get("block." + native_key, zh.get("item." + native_key, "")))
        rows.append({"method": method, "blocks": methods[method], "icon": icon, "name": label, "explicit_name": key in own})
    if not args.report:
        print(json.dumps(rows, ensure_ascii=False, indent=2))
        return
    baseline = {
        "minecraft:crafting": "minecraft:crafting_table", "minecraft:smelting": "minecraft:furnace",
        "minecraft:blasting": "minecraft:blast_furnace", "minecraft:smoking": "minecraft:smoker",
        "minecraft:campfire_cooking": "minecraft:campfire", "farmersdelight:cooking": "farmersdelight:cooking_pot",
        "farmersdelight:cutting": "farmersdelight:cutting_board", "farmersdelight:skillet": "farmersdelight:skillet",
        "farmersdelight:stove": "farmersdelight:stove", "brewinandchewin:fermenting": "brewinandchewin:keg",
        "fruitsdelight:cauldron": "minecraft:cauldron", "youkaishomecoming:cuisine": "youkaishomecoming:cuisine_board",
    }
    original_names = json.loads((ROOT / "tools/workstation_display_baseline.json").read_text(encoding="utf-8"))
    counts = defaultdict(int)
    for row in rows:
        old_icon = baseline.get(row["method"], row["method"])
        row["before_icon"] = old_icon.startswith("minecraft:") or old_icon in items
        row["before_name"] = row["method"] in original_names or any(
            key in zh for key in ("item." + row["method"].replace(":", "."), "block." + row["method"].replace(":", ".")))
        counts[(row["before_icon"], row["before_name"])] += 1
    missing = [row["method"] for row in rows if not row["icon"] or not row["name"]]
    lines = ["# 灶具图标与中文名核对", "", "按当前安装JAR物品模型/原生语言与百味纪已声明方式核对，不是游戏实测或新生产兼容声明。", "", "## 统计", "",
             f"- 共核对{len(rows)}个制作方式，含厨具、盛取/组合及兜底；同一方式可对应多个设备。"]
    for flags, label in [((True, True), "修改前图标/中文均可用"), ((True, False), "修改前有图标但缺中文"), ((False, True), "修改前有中文但缺正确图标"), ((False, False), "修改前图标/中文均缺")]:
        lines.append(f"- {label}：{counts[flags]}。")
    lines += [f"- 补齐后图标/中文均可解析：{len(rows)-len(missing)}；待用户指定：{len(missing)}。",
              "- 下表的“之前缺”只描述修改前；当前是否补齐看“当前状态”，不是仍缺图标或翻译。", "", "## 明细", "",
              "| 制作方式ID | 中文显示 | 修改前图标 | 修改前中文 | 当前状态 | 当前图标物品 | 依据 |", "| --- | --- | --- | --- | --- | --- | --- |"]
    for row in rows:
        symbolic = row["icon"].startswith("minecraft:") and not row["method"].startswith("minecraft:")
        state = "均已配置（待实机）" if row["icon"] and row["name"] else "仍需补充"
        lines.append(f"| {row['method']} | {row['name'] or '待定'} | {'之前有' if row['before_icon'] else '之前缺'} | {'之前有' if row['before_name'] else '之前缺'} | {state} | {row['icon'] or '待定'} | {'动作/容器代表' if symbolic else '宿主物品模型'} |")
    lines += ["", "## 范围", "", "- 补前按原JournalText显式映射/同ID物品和原有译名判断；宿主物品已有中文也计为可用，不将缺CJ专用键一律算缺译。",
              "- 共用映射：cuisinejournal/workstation_display.json，取首个实际注册候选。中文优先CJ译名，其次图标物品原生名。",
              "- 穿串/盖饭/摆盘/奶昔/温泉无独立物品设备时，用木棍/碗/炼药锅/水桶代表动作，不冒称独立方块。",
              "- 只覆盖已声明方式，不从未安装联动配方推定设备可用；未知外部方式保留同ID物品和名称回退。",
              "- 最终构建/安装/部署见CURRENT_STATUS；游戏内图标外观需集中实机确认。", ""]
    args.report.write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({"methods": len(rows), "missing": missing, "report": str(args.report)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
