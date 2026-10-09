#!/usr/bin/env python3
"""Executes the exact Room @Query SQL in SQLite. Not an Android or Kotlin test runner."""
from pathlib import Path
import re, json, sqlite3, datetime
R=Path(__file__).resolve().parents[1]
source=(R/'data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDao.kt').read_text()
queries={}
for quoted,name in re.findall(r'@Query\(("""[\s\S]*?"""|"(?:\\.|[^"\\])*")\)\s*(?:suspend\s+)?fun\s+(\w+)',source):
    queries[name]=quoted[3:-3] if quoted.startswith('"""') else json.loads(quoted)
c=sqlite3.connect(':memory:'); c.row_factory=sqlite3.Row
c.execute('CREATE TABLE entries(id TEXT PRIMARY KEY,title TEXT,amountMinor INTEGER,currency TEXT,type TEXT,category TEXT,epochDay INTEGER,note TEXT,searchText TEXT,createdAtMillis INTEGER,deletedAtMillis INTEGER)')
def day(value): return (datetime.date.fromisoformat(value)-datetime.date(1970,1,1)).days
def insert(id,amount=100,category='FOOD',date='2026-10-04',currency='UAH',kind='EXPENSE',deleted=None):
    c.execute('INSERT INTO entries VALUES(?,?,?,?,?,?,?,?,?,?,?)',(id,id,amount,currency,kind,category,day(date),'detail',id,1,deleted))
def q(name,**params): return c.execute(queries[name],params)
checks=[]
def check(value,label):
    if not value: raise AssertionError(label)
    checks.append(label)
for i in range(125): insert(f'food-{i}')
insert('transport',9000,'TRANSPORT')
insert('last-month',7000,date='2026-09-20')
insert('year-old',3000,date='2025-01-01')
insert('other-currency',999999,currency='USD')
insert('deleted',999999,deleted=1)
insert('salary',1000000,'SALARY',kind='INCOME')
insert('future',999999,date='2026-10-10')
params=dict(currency='UAH',type='EXPENSE',category='FOOD',**{'from':day('2026-10-01'),'through':day('2026-10-04')})
check(q('statistics',**params).fetchone()[0]==12500,'Calendar month: exact food sum across more than 50 records')
check(q('statistics',**{**params,'category':None}).fetchone()[0]==21500,'Calendar month: all expense categories')
check(q('statistics',**{**params,'type':'INCOME','category':None}).fetchone()[0]==1000000,'Income excludes expenses')
check(q('statistics',**{**params,'from':day('2000-01-01')}).fetchone()[0]==22500,'All-time includes earlier years and excludes deleted/future/currency')
check(q('statistics',**{**params,'category':'HEALTH'}).fetchone()[0]==0,'Empty category returns zero')
filter=dict(currency='UAH',type=None,pattern='%',category='FOOD',limit=51,**{'from':day('2026-10-01'),'through':day('2026-10-04')})
check(len(q('observeEntries',**filter).fetchall())==51,'Category pagination initial window plus sentinel')
rows=q('observeEntries',**{**filter,'limit':150}).fetchall()
check(len(rows)==125 and all(row['category']=='FOOD' for row in rows),'Load more obtains every category transaction')
check(all(row['note']=='detail' for row in rows),'Transaction details retained')
weekly=q('observeEntries',**{**filter,'limit':200,'from':day('2026-09-28')}).fetchall()
check(len(weekly)==125,'Seven-day details share exact seven-day bounds')
totals=q('observeCategoryTotals',currency='UAH',**{'from':day('2026-10-01'),'through':day('2026-10-04')}).fetchall()
food=next(row for row in totals if row['category']=='FOOD')
check(food['count']==125 and food['expenseMinor']==12500,'Category header uses full dataset rather than current page')
q('delete',id='food-0',now=1234)
check(q('statistics',**params).fetchone()[0]==12400,'Assistant statistics exclude newly deleted entries')
q('undo',id='food-0',stamp=1234)
check(q('statistics',**params).fetchone()[0]==12500,'Undo restores aggregate')
insert('food-income',100,'FOOD',kind='INCOME')
typed=q('observeCategoryTotals',currency='UAH',**{'from':day('2026-10-01'),'through':day('2026-10-04')}).fetchall()
food=next(row for row in typed if row['category']=='FOOD')
check(food['incomeCount']==1 and food['expenseCount']==125,'Income/expense counts remain separate inside one category')
check(food['count']==126,'Combined category detail count remains backwards compatible')
result={'kind':'exact-Room-SQL-in-SQLite','passed':len(checks),'checks':checks,'compiled_android':False,'executed_junit':False}
(R/'verification/upgrade-sql.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2))
