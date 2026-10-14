#!/usr/bin/env python3
"""Regenerate the source book, project tree and SHA-256 manifest from tracked files.
Never includes .git, ignored local credentials, build outputs or developer private keys.
Run after staging new files; repeat after changing the release verification report.
"""
from pathlib import Path
import hashlib
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
tracked = sorted(set(subprocess.check_output(['git', 'ls-files'], cwd=ROOT, text=True).splitlines()))
version = re.search(r'versionName = "([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text())[1]
langs = {'.kt':'kotlin', '.kts':'kotlin', '.java':'java', '.py':'python', '.xml':'xml',
         '.yml':'yaml', '.yaml':'yaml', '.properties':'properties', '.ps1':'powershell',
         '.bat':'batch', '.toml':'toml', '.json':'json'}
source = [p for p in tracked if Path(p).suffix in langs or p == 'gradlew']
book = [f'# TG Ledger {version} — complete file-by-file source', '',
        f'{len(source)} implementation, configuration and test files, without truncation.', '',
        'Generated from tracked files. Local keys and build outputs are excluded.', '']
for name in source:
    text = (ROOT / name).read_text().rstrip('\n')
    # Fences must be longer than any backtick run inside the original file.
    fence = '`' * max(3, max((len(m[0]) + 1 for m in re.finditer(r'`+', text)), default=3))
    book.extend([f'## {name}', '', fence + langs.get(Path(name).suffix, 'bash'), text, fence, ''])
(ROOT / 'SOURCE_CODE.md').write_text('\n'.join(book))
files = sorted(set(tracked) | {'SOURCE_CODE.md', 'PROJECT_TREE.txt', 'MANIFEST.sha256'})
(ROOT / 'PROJECT_TREE.txt').write_text(f'TG_Ledger-{version}/\n' + '\n'.join('  '+p for p in files)+'\n')
(ROOT / 'MANIFEST.sha256').write_text(''.join(
    f'{hashlib.sha256((ROOT/name).read_bytes()).hexdigest()}  {name}\n'
    for name in files if name != 'MANIFEST.sha256'))
print(f'Source book: {len(source)} files; manifest: {len(files)-1} files')
