"""Download an explicit, reviewed selection of public SDYU documents (Python stdlib only).

Stores original HTML/attachments, extracted verbatim article text and a provenance manifest.
No login or crawling is performed. Empty/image-only pages are not invented into text.
"""
import hashlib
import json
import re
import time
import sys
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1] / 'school-data' / 'sdyu'


class ArticleParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.depth = 0
        self.capture = None
        self.skip = 0
        self.parts = []
        self.page_parts = []
        self.links = []
        self.link = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in ('script', 'style'):
            self.skip += 1
        if tag == 'div':
            self.depth += 1
            if 'v_news_content' in attrs.get('class', '').split():
                self.capture = self.depth
        if tag == 'a':
            self.link = [attrs.get('href', ''), '']
        if self.capture and tag in ('p', 'br', 'li', 'tr', 'h1', 'h2', 'h3', 'h4'):
            self.parts.append('\n')
        if self.capture and tag in ('td', 'th'):
            self.parts.append(' | ')

    def handle_endtag(self, tag):
        if tag in ('script', 'style') and self.skip:
            self.skip -= 1
        if tag == 'div':
            if self.capture == self.depth:
                self.capture = None
            self.depth -= 1
        if tag == 'a' and self.link:
            self.links.append(self.link)
            self.link = None
        if self.capture and tag in ('p', 'li', 'tr', 'h1', 'h2', 'h3', 'h4'):
            self.parts.append('\n')

    def handle_data(self, data):
        if self.skip:
            return
        self.page_parts.append(data)
        if self.capture:
            self.parts.append(data)
        if self.link:
            self.link[1] += data


def fetch(url, referer=None):
    host = urlparse(url).hostname or ''
    if not (host == 'sdyu.edu.cn' or host.endswith('.sdyu.edu.cn')):
        raise ValueError('Not an approved school domain: ' + host)
    headers = {'User-Agent': 'Mozilla/5.0 (compatible; CampusKnowledgeResearch/1.0)'}
    if referer:
        headers['Referer'] = referer
    with urlopen(Request(url, headers=headers), timeout=45) as response:
        data = response.read(12 * 1024 * 1024 + 1)
        if len(data) > 12 * 1024 * 1024:
            raise ValueError('File exceeds download limit')
        final_host = urlparse(response.url).hostname or ''
        if not final_host.endswith('.sdyu.edu.cn') and final_host != 'sdyu.edu.cn':
            raise ValueError('Unexpected redirect domain')
        return data, response.url, response.headers.get('Content-Type', '')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def save(path, data):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return {'path': path, 'bytes': len(data), 'sha256': digest(data)}


def main():
    catalog = json.loads((ROOT / 'catalog.json').read_text(encoding='utf-8'))
    manifest = []
    existing = {}
    if (ROOT / 'manifest.json').exists():
        existing = {item['id']: item for item in json.loads((ROOT / 'manifest.json').read_text(encoding='utf-8'))}
    for entry in catalog:
        if entry['id'] in existing and ('--new-only' in sys.argv or any(
                file.get('acquisition') == 'user-provided-local-file' for file in existing[entry['id']]['files'])):
            manifest.append(existing[entry['id']])
            continue
        record = dict(entry, retrievedAt=datetime.now(timezone.utc).isoformat(), files=[], warnings=[])
        try:
            raw, final_url, _ = fetch(entry['url'])
            record['files'].append(dict(save(f"originals/{entry['id']}.html", raw), kind='html', url=final_url))
            parser = ArticleParser()
            parser.feed(raw.decode('utf-8-sig'))
            page_text = ''.join(parser.page_parts)
            match = re.search(r'(?:日期|发布时间|发布时间：|发布日期|时间)\s*[：:]?\s*(20\d{2})[-年/.](\d{1,2})[-月/.](\d{1,2})', page_text)
            if match:
                record['publishedDate'] = f'{match[1]}-{int(match[2]):02d}-{int(match[3]):02d}'
            else:
                record['publishedDate'] = None
                record['warnings'].append('页面未提取到明确发布日期，请核对官网')
            text = ''.join(parser.parts).replace('\u200b', '').replace('\xa0', ' ')
            text = re.sub(r'[\t ]+', ' ', text)
            text = '\n\n'.join(line.strip() for line in text.splitlines() if line.strip())
            seen = set()
            for href, label in parser.links:
                label = label.strip()
                if not re.search(r'\.(pdf|docx|doc|xlsx?)$', label, re.I):
                    continue
                url = urljoin(entry['url'], href)
                if url in seen:
                    continue
                seen.add(url)
                extension = label.rsplit('.', 1)[1].lower()
                try:
                    data, resolved, content_type = fetch(url, entry['url'])
                    valid = data.startswith(b'%PDF') if extension == 'pdf' else data.startswith(b'PK') if extension in ('docx', 'xlsx') else data.startswith(bytes.fromhex('D0CF11E0'))
                    if not valid:
                        raise ValueError('下载返回的内容不是声明的文档格式，可能需要验证码')
                    filename = f"attachments/{entry['id']}-{len(seen)}.{extension}"
                    record['files'].append(dict(save(filename, data), kind='attachment', originalName=label,
                                                url=url, resolvedUrl=resolved, contentType=content_type))
                except Exception as error:
                    record['warnings'].append(f'{label}: {error}')
                time.sleep(0.25)
            if len(text) >= 80:
                header = f"# {entry['title']}\n\n学校：山东青年政治学院\n发布单位：{entry['publisher']}\n官网来源：{entry['url']}\n官网发布日期：{record['publishedDate'] or '未标明'}\n采集时间：{record['retrievedAt']}\n"
                header += f"适用说明：{entry.get('scope', '按原文适用范围执行；官网公开不等于已确认当前有效，请核对最新通知。')}\n\n## 官网正文（原文提取，非 AI 改写）\n\n"
                path = f"texts/{entry['id']}.md"
                record['files'].append(dict(save(path, (header + text + '\n').encode('utf-8')), kind='text'))
                record['importFile'] = path
                record['textCharacters'] = len(text)
            else:
                supported = [file for file in record['files'] if file['kind'] == 'attachment' and file['path'].endswith(('.docx', '.pdf'))]
                if supported:
                    record['importFile'] = supported[0]['path']
                else:
                    record['warnings'].append('无可检索正文或可解析附件，不导入知识库')
            record['status'] = 'ready' if record.get('importFile') else 'archive-only'
            print(entry['id'], record['status'], record.get('publishedDate'), record.get('textCharacters', 0), 'chars;', len(record['files']), 'files', flush=True)
            for warning in record['warnings']:
                print('  WARNING:', warning, flush=True)
        except Exception as error:
            record['status'] = 'failed'
            record['warnings'].append(str(error))
            print(entry['id'], 'FAILED:', error, flush=True)
        manifest.append(record)
        (ROOT / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        time.sleep(0.4)
    print('Ready:', sum(item['status'] == 'ready' for item in manifest), '/', len(catalog))


if __name__ == '__main__':
    main()
