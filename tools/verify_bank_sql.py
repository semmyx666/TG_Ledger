#!/usr/bin/env python3
from pathlib import Path
import json,re,sqlite3
R=Path(__file__).resolve().parents[1]
source=(R/'data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDatabase.kt').read_text()
sql=[json.loads(x) for x in re.findall(r'db.execSQL\(("(?:\\.|[^"\\])*")\)',source)]
c=sqlite3.connect(':memory:');c.row_factory=sqlite3.Row
c.execute('CREATE TABLE entries(id TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,amountMinor INTEGER NOT NULL,currency TEXT NOT NULL,type TEXT NOT NULL,category TEXT NOT NULL,epochDay INTEGER NOT NULL,note TEXT NOT NULL,searchText TEXT NOT NULL,createdAtMillis INTEGER NOT NULL,deletedAtMillis INTEGER)')
c.execute("INSERT INTO entries VALUES('old','Legacy expense',100,'UAH','EXPENSE','FOOD',20733,'','legacy',1,NULL)")
for statement in sql:c.execute(statement)
checks=[]
def check(test,label):
 assert test,label
 checks.append(label)
old=c.execute("SELECT * FROM entries WHERE id='old'").fetchone()
check(old['sourceType']=='UNKNOWN' and old['amountMinor']==100,'Migration preserves entries and does not invent cash/card attribution')
check(len(c.execute('PRAGMA table_info(entries)').fetchall())==17,'All six metadata columns migrated')
s=(R/'data/src/main/kotlin/com/semmyx/ledger/data/bank/BankEntities.kt').read_text()
queries={}
for quoted,name in re.findall(r'@Query\(("""[\s\S]*?"""|"(?:\\.|[^"\\])*")\)\s*(?:suspend\s+)?fun\s+(\w+)',s):queries[name]=quoted[3:-3] if quoted.startswith('"""') else json.loads(quoted)
def insert(id,amount,source,bank,currency='UAH',kind='EXPENSE',category='FOOD',custom=None,deleted=None):
 c.execute('INSERT INTO entries(id,title,amountMinor,currency,type,category,epochDay,note,searchText,createdAtMillis,deletedAtMillis,sourceType,bankId,customCategoryId) VALUES(?,?,?,?,?,?,20733,\'\',\'\',1,?,?,?,?)',(id,id,amount,currency,kind,category,deleted,source,bank,custom))
insert('cash',200,'CASH',None);insert('mono',300,'CARD','mono');insert('privat',400,'CARD','privat')
insert('dollars',99999,'CARD','mono',currency='USD');insert('income',99999,'CARD','mono',kind='INCOME');insert('deleted',99999,'CARD','mono',deleted=1)
p={'currency':'UAH','from':20730,'through':20734,'source':None,'bank':None}
rows=c.execute(queries['sources'],p).fetchall()
check(sum(r['totalMinor'] for r in rows)==1000,'Source totals exclude income, deleted and other currencies')
check(next(r['totalMinor'] for r in rows if r['bankId']=='mono')==300,'Mono is isolated from Privat and cash')
check(sum(r['totalMinor'] for r in c.execute(queries['detailCategories'],{**p,'source':'CARD','bank':'privat'}))==400,'Source category filter uses chosen bank')
c.execute("INSERT INTO custom_categories VALUES('c1','Pet care','#6FAEFF')")
insert('custom',500,'CASH',None,category='OTHER',custom='c1')
cat=c.execute(queries['detailCategories'],p).fetchall()
check(any(r['customName']=='Pet care' and r['totalMinor']==500 for r in cat),'Custom categories retain their own identity')
fields=['id','eventKey','bankId','amountMinor','currency','type','merchant','cardLast4','eventAtMillis','receivedAtMillis','timeFromMessage','transfer','category','origin','status']
vals=['a','hash','mono',300,'UAH','EXPENSE','ATB','1234',100000,100000,0,0,'FOOD','PUSH','REVIEW']
q='INSERT OR IGNORE INTO bank_inbox('+','.join(fields)+') VALUES('+','.join('?'*len(fields))+')'
c.execute(q,vals);c.execute(q,['b']+vals[1:])
check(c.execute('SELECT COUNT(*) FROM bank_inbox').fetchone()[0]==1,'Unique fingerprint prevents repeated delivery imports')
check(c.execute(queries['similar'],dict(bank='mono',amount=300,currency='UAH',type='EXPENSE',time=120000,card='1234',merchant='ATB')).fetchone()[0]==1,'Near duplicates are detected for review')
check(c.execute(queries['similar'],dict(bank='privat',amount=300,currency='UAH',type='EXPENSE',time=120000,card='1234',merchant='ATB')).fetchone()[0]==0,'Different banks do not deduplicate each other')
c.execute(queries['clearInbox']);check(c.execute('SELECT COUNT(*) FROM entries').fetchone()[0]==8,'Clearing review history preserves ledger entries')
result={'kind':'exact-migration-and-Room-bank-SQL-in-SQLite','passed':len(checks),'checks':checks,'compiled_android':False}
(R/'verification/bank-sql.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
print(json.dumps(result,ensure_ascii=False,indent=2))
