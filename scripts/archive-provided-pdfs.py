"""Archive only the six school policy PDFs explicitly supplied by the user."""
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'school-data' / 'sdyu'
DESKTOP = Path(r'C:\Users\88451\Desktop')
FILES = {
    'financial-assessment-2025': '《山东青年政治学院家庭经济困难学生认定办法》.pdf',
    'student-management-2025': '《山东青年政治学院学生管理规定》.pdf',
    'student-awards-2025': '《山东青年政治学院学生奖励办法（修订）》.pdf',
    'work-study-2025': '《山东青年政治学院学生勤工助学管理办法》.pdf',
    'student-appeals-2025': '《山东青年政治学院学生申诉处理办法（修订）》.pdf',
    'financial-aid-2025': '《山东青年政治学院学生资助资金管理办法》.pdf',
}
SCOPES = {
    'financial-assessment-2025': '用户提供的PDF为山青院办字〔2021〕18号，落款2021-12-13；官网页面发表于2025-06-26，不代表2025年修订。具体执行以学校最新通知为准。',
    'student-management-2025': '用户提供的PDF为山青院办字〔2025〕12号，落款2025-05-19；官网页面发表于2025-07-11，施行及适用范围以正文为准。',
    'student-awards-2025': '用户提供的PDF为山青院办字〔2025〕18号，落款2025-07-02，适用于全日制普通本专科学生；官网页面发表于2025-07-11。',
    'work-study-2025': '用户提供的PDF为2022年修订版，山青院办字〔2022〕11号，落款2022-10-13；官网页面发表于2025-06-26，不代表2025年修订。',
    'student-appeals-2025': '用户提供的PDF为山青院办字〔2018〕15号，落款2018-09-21；官网页面发表于2025-06-26，不代表2025年修订，具体程序请核对最新规定。',
    'financial-aid-2025': '用户提供的PDF为山青院办字〔2021〕19号，落款2021-12-13；官网页面发表于2025-06-26。文中资助金额及标准属于该版本，不得直接视为当前学年标准。',
}


def main():
    manifest_path = ROOT / 'manifest.json'
    original = manifest_path.read_bytes()
    records = json.loads(original)
    prepared = []
    for record_id, filename in FILES.items():
        data = (DESKTOP / filename).read_bytes()
        if not data.startswith(b'%PDF-'):
            raise ValueError(f'Not a PDF: {filename}')
        if len(data) > 10 * 1024 * 1024:
            raise ValueError(f'PDF exceeds upload limit: {filename}')
        record = next(item for item in records if item['id'] == record_id)
        relative = f'attachments/{record_id}.pdf'
        target = ROOT / relative
        if target.exists() and target.read_bytes() != data:
            raise ValueError(f'Existing archive differs: {relative}')
        prepared.append((record, relative, data, filename))
    backup = ROOT / 'manifest-before-provided-pdfs.json'
    if not backup.exists():
        backup.write_bytes(original)
    for record, relative, data, filename in prepared:
        target = ROOT / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        archived = {
            'path': relative, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
            'kind': 'attachment', 'originalName': filename, 'acquisition': 'user-provided-local-file',
            'archivedAt': datetime.now(timezone.utc).isoformat(),
            'sourcePage': record['url'], 'contentType': 'application/pdf',
        }
        record['files'] = [item for item in record['files'] if item['path'] != relative] + [archived]
        record['previousWarnings'] = record.get('previousWarnings', record.get('warnings', []))
        record['warnings'] = []
        record['status'] = 'ready'
        record['importFile'] = relative
        record['scope'] = SCOPES[record['id']]
        record['acquisitionNote'] = '官网附件自动下载要求验证码；本 PDF 由用户提供，已核对格式并记录哈希。'
        print(filename, len(data), 'bytes', archived['sha256'])
    manifest_path.write_text(json.dumps(records, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
