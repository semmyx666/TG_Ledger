#!/usr/bin/env python3
"""Executes the actual Kotlin string-literal regexes on Java's engine; not Kotlin/Android compilation."""
from pathlib import Path
import re,json,subprocess,tempfile
R=Path(__file__).resolve().parents[1]
s=(R/'domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankParser.kt').read_text()
q=r'"(?:\\.|[^"\\])*"'
patterns={name:json.loads(literal) for name,literal in re.findall(r'private val (\w+) = Regex\(('+q+')',s)}
java='''import java.util.regex.*;
public class BankRegexSmoke {
 static int checks=0;
 static Pattern p(String source) { return Pattern.compile(source,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE); }
 static Pattern amount=p(AMOUNT),prefix=p(PREFIX),card=p(CARD),merchant=p(MERCHANT),otp=p(OTP),declined=p(DECLINED),transfer=p(TRANSFER),income=p(INCOME);
 static void check(boolean test,String label) { if(!test) throw new AssertionError(label); checks++; }
 static void money(String text,String expected,String currency) {
  Matcher m=amount.matcher(text); String value=null,cur=null;
  if(m.find()) { value=m.group(1); cur=m.group(2); }
  else { m=prefix.matcher(text); if(m.find()) { value=m.group(2); cur=m.group(1); } }
  if(value!=null)value=value.replaceAll("[ \\u00A0\\u202F]","").replace(',','.');
  check(java.util.Objects.equals(expected,value) && java.util.Objects.equals(currency,cur),"money fixture: "+text);
 }
 public static void main(String[] args) {
  money("Оплата 1 200,50 грн у АТБ; картка *1234","1200.50","грн");
  money("Purchase 12.50 USD at Steam","12.50","USD");
  money("Payment $12.50 at Steam","12.50","$");
  money("Зараховано 2000 UAH","2000","UAH");
  money("Оплата 1\\u00A0000,25 грн","1000.25","грн");
  money("Payment €12.50 at Megogo","12.50","€");
  Matcher c=card.matcher("картка *1234"); check(c.find()&&c.group(1).equals("1234"),"card suffix");
  Matcher shop=merchant.matcher("Оплата 100 грн у АТБ; баланс 900 грн");check(shop.find()&&shop.group(1).equals("АТБ"),"merchant boundary");
  check(otp.matcher("Код підтвердження оплати 100 грн").find(),"OTP ignored");
  check(declined.matcher("Payment declined 10 EUR").find(),"declined ignored");
  check(transfer.matcher("Переказ 100 грн").find(),"transfer marked for review");
  check(income.matcher("Зараховано 2000 грн").find(),"income detection");
  System.out.println("Bank source-regex JVM fixtures passed: "+checks);
 }
}'''
for javaName,kotlinName in [('AMOUNT','amount'),('PREFIX','amountPrefix'),('CARD','card'),('MERCHANT','merchant'),('OTP','otp'),('DECLINED','declined'),('TRANSFER','transfer'),('INCOME','income')]:
 java=java.replace('p('+javaName+')','p('+json.dumps(patterns[kotlinName],ensure_ascii=False)+')')
with tempfile.TemporaryDirectory() as folder:
 p=Path(folder)/'BankRegexSmoke.java';p.write_text(java)
 run=subprocess.run(['java',str(p)],capture_output=True,text=True,timeout=60)
 if run.returncode: raise RuntimeError(run.stdout+run.stderr)
 print(run.stdout.strip())
(R/'verification/bank-regex-jvm.json').write_text(json.dumps({'kind':'actual-source-regex-on-JVM','passed_fixtures':12,'compiled_kotlin':False,'executed_android':False},indent=2))
