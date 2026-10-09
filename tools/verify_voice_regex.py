#!/usr/bin/env python3
"""Run the exact source monetary regex patterns on the JVM. This does NOT compile Kotlin."""
from pathlib import Path
import re, json, tempfile, subprocess
R=Path(__file__).resolve().parents[1]
s=(R/'app/src/main/kotlin/com/semmyx/ledger/voice/VoiceEntryParser.kt').read_text()
quoted=r'"(?:\\.|[^"\\])*"'
stem_block=s.split('private val CURRENCY_STEMS = listOf(',1)[1].split('\n    )',1)[0]
stems=[json.loads(x) for x in re.findall(quoted,stem_block)]
amount_block=s.split('private val AMOUNT_WITH_CURRENCY = Regex(',1)[1].split('RegexOption',1)[0]
parts=[json.loads(x) for x in re.findall(quoted,amount_block)]
amount=parts[0]+'|'.join(stems)+parts[-1]
bare=json.loads(re.search(r'private val BARE_NUMBER = Regex\(('+quoted+r')\)',s).group(1))
java='''import java.util.regex.*;
public class RegexSmoke {
    static int checks=0;
    static final Pattern money=Pattern.compile(MONEY,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    static final Pattern bare=Pattern.compile(BARE);
    static void test(String text,String expected) {
        Matcher m=money.matcher(text);
        String found=null;
        if(m.find()) found=m.groupCount()>=1?m.group(1):m.group();
        else { m=bare.matcher(text); while(m.find()) found=m.groupCount()>=1?m.group(1):m.group(); }
        if(found!=null) found=found.replace(" ","").replace("\\u00A0","");
        if(!java.util.Objects.equals(found,expected)) throw new AssertionError(text+" -> "+found);
        checks++;
    }
    public static void main(String[] args) {
        test("привет я купил 1 кг томатов за 100 грн","100");
        test("я получил зарплату 1 000 000 гривен","1000000");
        test("купив томати за 100","100");
        test("отримав зарплату 2000,50","2000,50");
        test("купив за 1\\u00A0000,25 гривень","1000,25");
        test("привіт",null);
        test("bought food for 12.50 USD","12.50");
        System.out.println("JVM monetary-regex checks passed: "+checks);
    }
}'''.replace('MONEY',json.dumps(amount,ensure_ascii=True)).replace('BARE',json.dumps(bare,ensure_ascii=True))
with tempfile.TemporaryDirectory() as folder:
    path=Path(folder)/'RegexSmoke.java';path.write_text(java)
    result=subprocess.run(['java','--source','17',str(path)],text=True,capture_output=True,timeout=30)
    if result.returncode: raise SystemExit(result.stdout+result.stderr)
    print(result.stdout.strip())
    (R/'verification/voice-regex-jvm.json').write_text(json.dumps({'kind':'exact-source-regex-on-JVM','passed':7,'compiled_kotlin':False,'compiled_android':False,'output':result.stdout.strip()},indent=2)+'\n')
