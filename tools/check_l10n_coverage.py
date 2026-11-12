import io
import os
import re

dic = io.open('core/design/src/main/java/com/veluneagent/core/design/l10n/EnStrings.kt', encoding='utf-8').read()
str_re = re.compile(r'"((?:[^"\\]|\\.)*)"')
han = re.compile(r'[\u4e00-\u9fff]')

EXEMPT = [
    '【引用】', '[附件：', '（内容过长，已截断）', '待办内容', '待办 id',
    '把 PDF 表格转成 CSV', '小米设置页的设计要点', '层级、留白、圆角三个维度',
    '已读取 3 张表', '帮我把这张截图里的功能点整理成清单',
    '对话页 · 浅色', '对话页 · 深色', '输入栏 · 浅色', 'Markdown 渲染', '历史渲染',
]

targets = []
for base in ['app/src/main/java', 'feature/chat/src/main/java', 'feature/messages/src/main/java', 'feature/settings/src/main/java']:
    for dirpath, _, files in os.walk(base):
        for f in files:
            if f.endswith('.kt'):
                targets.append(os.path.join(dirpath, f))

seen = set()
for path in targets:
    for i, line in enumerate(io.open(path, encoding='utf-8'), 1):
        for s in str_re.findall(line):
            if not han.search(s) or any(e in s for e in EXEMPT):
                continue
            if ('"' + s + '"') in dic:
                continue
            key = s[:40]
            if key in seen:
                continue
            seen.add(key)
            print(f'{path.replace(chr(92), "/")}:{i}| {line.strip()[:150]}')
