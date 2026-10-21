"""Read installed B005 resources without loading Minecraft or altering the instance."""
import argparse
import hashlib
import json
import zipfile
from collections import Counter
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
CONTENT = {
    'farm_and_charm', 'bakery', 'candlelight', 'brewery', 'vinery', 'meadow',
    'herbalbrews', 'beachparty', 'bloomingnature', 'wildernature', 'camping',
}


def is_recipe(name):
    parts = name.split('/')
    return len(parts) >= 4 and parts[0] == 'data' and parts[2] == 'recipes' and name.endswith('.json')


def inspect(snapshot):
    available = {mod['id'] for jar in snapshot['jars'] for mod in jar['mods']}
    available.update(mod['id'] for mod in snapshot['embedded'])
    available.update({'minecraft', 'forge', 'java'})
    result = []
    for jar in snapshot['jars']:
        hosts = CONTENT.intersection(mod['id'] for mod in jar['mods'])
        if not hosts:
            continue
        path = Path(snapshot['instance']) / 'mods' / jar['file']
        recipes, learned, invalid = [], [], []
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if not is_recipe(name):
                    continue
                try:
                    recipe = json.loads(archive.read(name))
                    recipe_type = recipe.get('type', 'conditional/wrapper')
                    if ':' not in recipe_type and recipe_type != 'conditional/wrapper':
                        recipe_type = 'minecraft:' + recipe_type
                    recipes.append({'resource': name, 'type': recipe_type, 'json': recipe})
                    if recipe.get('requiresLearning'):
                        learned.append(name)
                except (ValueError, AttributeError) as error:
                    invalid.append({'resource': name, 'error': str(error)})
            classes = [name for name in archive.namelist() if name.endswith('.class') and '$' not in name]
        missing = [dep['modId'] for mod in jar['mods'] for dep in mod.get('dependencies', [])
                   if dep.get('mandatory') and dep['modId'] not in available]
        result.append({
            'mods': [{'id': mod['id'], 'version': mod['version']} for mod in jar['mods']],
            'file': jar['file'], 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'recipe_counts': dict(sorted(Counter(row['type'] for row in recipes).items())),
            'requires_learning_true': learned, 'invalid_json': invalid,
            'missing_dependency_ids': missing,
            'host_classes': [name for name in classes if any(part in name.lower() for part in
                             ['/block/', '/blocks/', '/recipe/', '/recipes/', '/item/', '/items/'])],
            'recipes': recipes,
        })
    return {'scope': 'installed B005 only; static resource and metadata evidence, not gameplay verification',
            'hosts': result}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshot', type=Path, default=PROJECT / 'docs/compatibility/installed.json')
    parser.add_argument('--output', type=Path, default=PROJECT / 'build/b005-resource-audit.json')
    args = parser.parse_args()
    assert is_recipe('data/test/recipes/food.json')
    assert not is_recipe('data/test/advancements/recipes/food.json')
    audit = inspect(json.loads(args.snapshot.read_text(encoding='utf-8-sig')))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + chr(10), encoding='utf-8')
    for host in audit['hosts']:
        print(json.dumps({key: host[key] for key in ['mods', 'recipe_counts', 'requires_learning_true',
                                                   'invalid_json', 'missing_dependency_ids']}, ensure_ascii=False))
    print('Host count:', len(audit['hosts']), 'Output:', args.output)


if __name__ == '__main__':
    main()
