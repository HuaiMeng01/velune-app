import re, io

src = io.open('app/src/main/java/com/veluneagent/app/VeluneApp.kt', encoding='utf-8').read()
dic = io.open('core/design/src/main/java/com/veluneagent/core/design/l10n/EnStrings.kt', encoding='utf-8').read()

str_re = re.compile(r'"((?:[^"\\]|\\.)*)"')
misses = []
count = 0
for name in ['agreementRoute', 'privacyRoute', 'licensesRoute', 'aboutRoute']:
    m = re.search(name + r' = DetailRoute\(.*?\n\)', src, re.S)
    if not m:
        misses.append('SECTION NOT FOUND: ' + name)
        continue
    block = m.group(0)
    for s in str_re.findall(block):
        if not re.search(r'[\u4e00-\u9fff]', s):
            continue
        count += 1
        if ('"' + s + '"') not in dic:
            misses.append(name + ': ' + s[:70])
print('checked', count, 'strings')
print('\n'.join(misses) if misses else 'ALL LEGAL KEYS COVERED')
