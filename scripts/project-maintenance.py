"""清理可再生成的产物，或导出不含本机配置的毕业设计源代码包。"""
import argparse
import json
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GENERATED = ['backend/target', 'frontend/dist', 'frontend/test-results', 'frontend/playwright-report',
             'frontend/vite.log', 'frontend/vite-error.log', '.github/modernize/java-upgrade']
SOURCE_DIRS = ['backend/src', 'frontend/src', 'frontend/public', 'frontend/tests', 'scripts', 'docs', 'school-data']
SOURCE_FILES = ['README.md', '.gitignore', '.prettierrc.json', '.prettierignore', 'backend/pom.xml',
                'backend/.env.example', 'frontend/package.json', 'frontend/package-lock.json',
                'frontend/index.html', 'frontend/vite.config.js', 'frontend/playwright.config.js',
                'start-backend.cmd', 'start-frontend.cmd']


def clean():
    removed = []
    for relative in GENERATED:
        target = ROOT / relative
        if target.is_symlink():
            raise ValueError(f'不处理符号链接：{relative}')
        if target.is_dir():
            count = sum(1 for path in target.rglob('*') if path.is_file())
            shutil.rmtree(target)
        elif target.is_file():
            count = 1
            target.unlink()
        else:
            continue
        removed.append({'path': relative, 'files': count})
        print('已清理', relative, count, '个文件')
    if removed:
        record = ROOT / 'docs/cleanup-record.json'
        previous = json.loads(record.read_text(encoding='utf-8')) if record.exists() else {}
        history = previous.get('runs', [])
        if 'removed' in previous:
            history.append({'removed': previous['removed']})
        history.append({'removed': removed})
        record.write_text(
            json.dumps({'runs': history, 'kept': ['源码', '学校原始资料及校验记录', '.env', '本地数据库', '依赖与开发工具配置']},
                       ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def export():
    output = ROOT / 'deliverables'
    output.mkdir(exist_ok=True)
    archive = output / 'campus-knowledge-base-source.zip'
    selected = [ROOT / name for name in SOURCE_FILES]
    for name in SOURCE_DIRS:
        selected.extend(path for path in (ROOT / name).rglob('*') if path.is_file())
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as bundle:
        for path in sorted(set(selected)):
            if path.is_symlink() or '__pycache__' in path.parts or path.suffix == '.pyc':
                continue
            bundle.write(path, Path(ROOT.name) / path.relative_to(ROOT))
    print('源代码包：', archive)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['clean', 'export'])
    args = parser.parse_args()
    if args.action == 'clean':
        clean()
    else:
        export()
