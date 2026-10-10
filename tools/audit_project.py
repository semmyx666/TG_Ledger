#!/usr/bin/env python3
"""Offline structural/resource/SQLite audit. This is NOT an Android compiler or a device test."""
from pathlib import Path
import json
import re
import sqlite3
import sys
import unicodedata
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks = []
failures = []

def check(condition, label):
    (checks if condition else failures).append(label)


def load_strings(folder):
    values = {}
    items = [item for file in sorted((ROOT / f'app/src/main/res/{folder}').glob('*.xml')) for item in ET.parse(file).getroot()]
    for item in items:
        if item.tag != 'string':
            continue
        check(item.attrib['name'] not in values, f'No duplicate string: {folder}/{item.attrib["name"]}')
        values[item.attrib['name']] = ''.join(item.itertext())
    return values


def code_only(source):
    """Lex strings/comments for balanced delimiters, without claiming full Kotlin parsing."""
    # Kotlin string-template bodies are deliberately not type-checked by this audit.
    pattern = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*'
    return re.sub(pattern, lambda match: '\n' * match.group(0).count('\n'), source)


for file in ROOT.rglob('*.xml'):
    if 'build' in file.parts:
        continue
    try:
        ET.parse(file)
        checks.append(f'Well-formed XML: {file.relative_to(ROOT)}')
    except ET.ParseError as error:
        failures.append(f'Malformed XML: {file.relative_to(ROOT)}: {error}')

en = load_strings('values')
uk = load_strings('values-uk')
# Explicit non-translatable branding resources inherit from default values in Android.
for file in (ROOT/'app/src/main/res/values').glob('*.xml'):
    for item in ET.parse(file).getroot():
        if item.tag == 'string' and item.get('translatable') == 'false':
            uk.setdefault(item.get('name'), ''.join(item.itertext()))
check(en.keys() == uk.keys(), 'English/Ukrainian key parity')
for key in en.keys() & uk.keys():
    check(sorted(re.findall(r'%\d+\$[a-z]', en[key])) == sorted(re.findall(r'%\d+\$[a-z]', uk[key])), f'Format-argument parity: {key}')
    check(bool(en[key].strip()) and bool(uk[key].strip()), f'Nonempty translations: {key}')

kotlin = list(ROOT.rglob('*.kt'))
for file in kotlin:
    source = file.read_text()
    path = str(file.relative_to(ROOT))
    check('\0' not in source, f'No NUL bytes: {path}')
    check(not re.search(r'\b(TODO|FIXME|NotImplementedError)\b', source), f'No implementation markers: {path}')
    if '/src/main/' in path:
        for name in re.findall(r'R\.string\.(\w+)', source):
            check(name in en, f'Resolved app string: {name}')
    if path.startswith('domain/src/main/'):
        check(not re.search(r'import (android\.|androidx\.|com\.semmyx\.ledger\.(data|presentation))', source), f'Pure domain boundary: {path}')
    if path.startswith('app/src/main/') and path.endswith('ViewModel.kt'):
        check('com.semmyx.ledger.data.' not in source, f'Domain-only ViewModel dependencies: {path}')
    stack = []
    valid = True
    for character in code_only(source):
        if character in '({[':
            stack.append(character)
        elif character in ')}]':
            if not stack or stack.pop() != {')':'(', '}':'{', ']':'['}[character]:
                valid = False
                break
    check(valid and not stack, f'Lexically balanced Kotlin delimiters: {path}')

# Execute the exact @Query bodies in SQLite, not a manually rewritten query.
dao = (ROOT/'data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDao.kt').read_text()
query_map = {}
for match in re.finditer(r'@Query\(("""[\s\S]*?"""|"(?:\\.|[^"\\])*")\)\s*(?:suspend\s+)?fun\s+(\w+)', dao):
    quoted, name = match.groups()
    query_map[name] = quoted[3:-3] if quoted.startswith('"""') else json.loads(quoted)

entities = (ROOT/'data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerEntities.kt').read_text()
body = re.search(r'data class EntryEntity\(([\s\S]*?)\n\)', entities).group(1)
columns = re.findall(r'val (\w+): (\w+)(\?)?', body)
types = {'String':'TEXT','Long':'INTEGER','Int':'INTEGER','Double':'REAL'}
connection = sqlite3.connect(':memory:')
connection.row_factory = sqlite3.Row
connection.execute('CREATE TABLE entries (' + ','.join('"'+name+'" '+types[kind]+(' PRIMARY KEY' if name=='id' else '') for name, kind, nullable in columns) + ')')

def insert(id, title, minor, currency='UAH', kind='EXPENSE', day=20704, created=1):
    values = dict(id=id, title=title, amountMinor=minor, currency=currency, type=kind, category='FOOD', epochDay=day,
                  note='', searchText=unicodedata.normalize('NFKC', title).lower(), createdAtMillis=created, deletedAtMillis=None, sourceType='UNKNOWN', bankId=None, cardLast4=None, eventAtMillis=None, customCategoryId=None, origin='MANUAL')
    connection.execute('INSERT INTO entries ('+','.join('"'+name+'"' for name,_,_ in columns)+') VALUES ('+','.join('?' for _ in columns)+')', [values[name] for name,_,_ in columns])


def query(name, **params):
    if name == 'observeEntries':
        params = {'category': None, 'from': None, 'through': None, **params}
    return connection.execute(query_map[name], params)

insert('coffee', 'КАВА 50%', 1250, created=3)
insert('income', 'Salary', 100000, kind='INCOME', created=2)
insert('usd', 'Other currency', 999999999, currency='USD')
balance = dict(query('observeBalance', currency='UAH').fetchone())
check(balance == {'incomeMinor':100000,'expenseMinor':1250,'entryCount':2}, 'Actual SQL: sums keep currencies separate')
check(len(query('observeEntries', currency='UAH', type=None, pattern='%кава%', limit=50).fetchall()) == 1, 'Actual SQL: normalized Ukrainian search')
check(len(query('observeEntries', currency='UAH', type=None, pattern='%50\\%%', limit=50).fetchall()) == 1, 'Actual SQL: literal percent search')
check(len(query('observeEntries', currency='UAH', type=None, pattern="%' OR 1=1 --%", limit=50).fetchall()) == 0, 'Actual SQL: bound input cannot change query structure')
check(len(query('observeEntries', currency='UAH', type='INCOME', pattern='%', limit=50).fetchall()) == 1, 'Actual SQL: type filter')
check(query('observeEntries', currency='UAH', type=None, pattern='%', limit=1).fetchone()['id'] == 'coffee', 'Actual SQL: stable creation ordering and limit')
check(query('delete', id='coffee', now=1234).rowcount == 1, 'Actual SQL: first delete succeeds')
check(query('delete', id='coffee', now=5678).rowcount == 0, 'Actual SQL: repeated delete is idempotent')
check(query('find', id='coffee').fetchone() is None, 'Actual SQL: deleted entries are hidden')
check(query('undo', id='coffee', stamp=9999).rowcount == 0, 'Actual SQL: stale undo token is rejected')
check(query('undo', id='coffee', stamp=1234).rowcount == 1, 'Actual SQL: exact undo token restores entry')
query('delete', id='coffee', now=1234)
query('purge', before=1235)
check(query('findIncludingDeleted', id='coffee').fetchone() is None, 'Actual SQL: old tombstone is purged')
activity = query('observeActivity', currency='UAH', **{'from':20703,'through':20705}).fetchall()
check(len(activity)==1 and activity[0]['incomeMinor']==100000 and activity[0]['expenseMinor']==0, 'Actual SQL: date-window aggregation excludes deletions')
connection.close()

app_manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
android = '{http://schemas.android.com/apk/res/android}'
application = app_manifest.find('application')
check(application.get(android+'allowBackup') == 'false', 'Android cloud backup disabled')
check(application.get(android+'usesCleartextTraffic') == 'false', 'Cleartext traffic disabled')
support_activity = next(a for a in application.findall('activity') if 'SupportWebActivity' in a.get(android+'name',''))
check(support_activity.get(android+'exported') == 'false', 'Support WebView is non-exported')
check('private fun normalizeSearch' in (ROOT/'data/src/main/kotlin/com/semmyx/ledger/data/repository/RoomEntryRepository.kt').read_text(), 'Unicode normalization implemented in repository')
check('com.android.billingclient:billing-ktx:7.1.1' in (ROOT/'app/build.gradle.kts').read_text(), 'Requested v7 flavor included')
check('com.android.billingclient:billing-ktx:8.0.0' in (ROOT/'app/build.gradle.kts').read_text(), 'Current-submission v8 flavor included')
check('targetSdk = 36' in (ROOT/'app/build.gradle.kts').read_text(), 'Android 16 target configured')
check('fallbackToDestructiveMigration(' not in (ROOT/'data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDatabase.kt').read_text(), 'No destructive migration fallback')

result = {
    'kind':'offline-structural-resource-and-sql-audit',
    'passed_assertions':len(checks),
    'failures':failures,
    'kotlin_source_files':len(kotlin),
    'localized_string_pairs':len(en),
    'compiled_android':False,
    'executed_android_or_junit_tests':False,
    'checks':checks,
}
output = ROOT/'verification/source-audit.json'
output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
print(json.dumps({key:value for key,value in result.items() if key!='checks'}, ensure_ascii=False, indent=2))
sys.exit(1 if failures else 0)
