#!/usr/bin/env python3
"""Standalone owner-only issuer. Ready Windows EXE requires neither Python nor the source project.
Private key is loaded from a separate owner bundle, never embedded into the public EXE/APK.
"""
from __future__ import annotations
import argparse
import base64
import json
from pathlib import Path
import sys
import tempfile
import issue_activation_code as issuer


def bundle_root() -> Path:
    return Path(sys.executable).resolve().parent if getattr(sys,'frozen',False) else Path(__file__).resolve().parent


def load_bundle(root: Path):
    config=json.loads((root/'issuer-config.json').read_text(encoding='utf-8-sig'))
    path=Path(config.get('private_key_file','private/developer_private_key.pem'))
    if path.is_absolute() or '..' in path.parts:
        raise ValueError('Invalid private-key path in owner configuration.')
    key=issuer.load_private(root/path)
    public=issuer.public_bytes(key)
    if issuer.fingerprint(public)!=config['key_id'] or base64.b64encode(public).decode()!=config['public_key']:
        raise ValueError('Key files do not match. Extract the complete PRIVATE owner archive into a new folder; do not replace individual keys.')
    return config,key


def create_code(root: Path, installation_or_request: str, days: float) -> str:
    config,key=load_bundle(root)
    value=installation_or_request.strip()
    if value.startswith('TGREQ1|'):
        installation,kid,product=issuer.parse_request(value)
        if kid!=config['key_id']:
            raise ValueError('Запрос из другой версии APK с другим ключом. Возьмите запрос именно из приложенного APK 4.4.1.')
        if product!=config['product_id']:
            raise ValueError('Запрос предназначен для другого продукта.')
    else:
        installation=issuer.normalize_installation(value)
    return issuer.make_token(key,installation,config['product_id'],days)


def run_gui(root_path: Path, smoke_report: Path | None = None):
    import tkinter as tk
    from tkinter import ttk, messagebox
    root=tk.Tk();root.withdraw()
    try:
        config,_=load_bundle(root_path)
    except Exception as error:
        messagebox.showerror('Create Key Premium — неполный комплект',
            'Распакуйте ПОЛНОСТЬЮ личный архив Create Key Premium. Рядом с EXE должны остаться issuer-config.json и папка private.\n\n'+str(error))
        root.destroy();raise SystemExit(1)
    root.title('Create Key Premium — TG Ledger '+config['version'])
    root.geometry('740x600');root.minsize(500,500)
    frame=ttk.Frame(root,padding=16);frame.pack(fill='both',expand=True)
    frame.columnconfigure(0,weight=1);frame.rowconfigure(7,weight=1)
    ttk.Label(frame,text='Создать ключ Premium',font=('Segoe UI',17,'bold')).grid(row=0,column=0,sticky='w')
    ttk.Label(frame,text='Вставьте ID установки ИМЕННО ИЗ НОВОГО APK (например AB12-CD34-EF56).\nЛучше скопировать полный запрос TGREQ1 из Настроек → Активация TG Plus.').grid(row=1,column=0,sticky='ew',pady=(10,5))
    installation=tk.StringVar();ttk.Entry(frame,textvariable=installation,font=('Segoe UI',12)).grid(row=2,column=0,sticky='ew')
    options=ttk.Frame(frame);options.grid(row=3,column=0,sticky='w',pady=12)
    ttk.Label(options,text='Дней:').pack(side='left');days=tk.StringVar(value='30')
    ttk.Spinbox(options,from_=0,to=36500,textvariable=days,width=8).pack(side='left',padx=8)
    ttk.Label(options,text='0 — бессрочно; 5 — на пять дней').pack(side='left')
    status=tk.StringVar(value='Подтвердите оплату самостоятельно. Срок начинается с выдачи кода.')
    ttk.Label(frame,textvariable=status).grid(row=4,column=0,sticky='ew')
    result=tk.Text(frame,height=6,wrap='char',font=('Consolas',10))
    def generate():
        try:
            token=create_code(root_path,installation.get(),float(days.get()))
            result.delete('1.0','end');result.insert('1.0',token)
            status.set('Готово: '+('бессрочно.' if float(days.get())==0 else days.get()+' дней с момента выдачи.'))
            return token
        except Exception as error:
            # Never leave an older successful token visible after a failed request.
            result.delete('1.0','end');status.set('Код НЕ выдан.')
            if smoke_report is not None:raise
            messagebox.showerror('Код не выдан',str(error))
    ttk.Button(frame,text='Создать ключ Premium',command=generate).grid(row=5,column=0,sticky='ew',pady=8)
    ttk.Label(frame,text='Готовый код — вставьте в приложение и нажмите «Активувати TG Plus»:').grid(row=6,column=0,sticky='ew')
    result.grid(row=7,column=0,sticky='nsew')
    def copy():
        token=result.get('1.0','end').strip()
        if not token:
            if smoke_report is not None:raise ValueError('No generated code')
            messagebox.showinfo('Нет кода','Сначала введите ID и нажмите «Создать ключ Premium».');return
        root.clipboard_clear();root.clipboard_append(token);root.update()
        status.set('Код скопирован. Отправляйте только этот код, НЕ генератор и НЕ папку private.')
    ttk.Button(frame,text='Скопировать код',command=copy).grid(row=8,column=0,sticky='ew',pady=8)
    ttk.Label(frame,text='Key ID: '+config['key_id']+'\nЛИЧНЫЙ ИНСТРУМЕНТ РАЗРАБОТЧИКА. Не передавайте EXE вместе с ключами покупателям.').grid(row=9,column=0,sticky='ew')
    def wrap(event):
        for widget in frame.winfo_children():
            if isinstance(widget,ttk.Label):widget.configure(wraplength=max(200,event.width-36))
    frame.bind('<Configure>',wrap)
    failures=[]
    if smoke_report is not None:
        def smoke():
            try:
                installation.set('TGREQ1|AB12-CD34-EF56|'+config['key_id']+'|ledger_plus');days.set('5')
                token=generate();copy();assert root.clipboard_get().strip()==token
                assert token.startswith('TG2.'+config['key_id']+'.')
                smoke_report.write_text(json.dumps({'passed':True,'window':True,'issue':True,'clipboard':True,'frozen_exe':bool(getattr(sys,'frozen',False))}))
            except Exception as error:
                failures.append(str(error));smoke_report.write_text(json.dumps({'passed':False,'error':str(error)}))
            root.destroy()
        root.after(500,smoke)
    root.deiconify();root.mainloop()
    if failures:raise SystemExit(1)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle',type=Path,default=None)
    parser.add_argument('--smoke-test',action='store_true')
    parser.add_argument('--smoke-report',type=Path,default=Path('premium-gui-smoke.json'))
    args=parser.parse_args()
    if args.smoke_test:
        report=args.smoke_report.resolve()
        with tempfile.TemporaryDirectory(prefix='premium-gui-') as temporary:
            home=Path(temporary);(home/'private').mkdir()
            key=issuer.rsa.generate_private_key(public_exponent=65537,key_size=2048)
            issuer.atomic_write(home/'private/developer_private_key.pem',key.private_bytes(issuer.serialization.Encoding.PEM,
                issuer.serialization.PrivateFormat.PKCS8,issuer.serialization.NoEncryption()),private=True,replace=False)
            (home/'issuer-config.json').write_text(json.dumps({'version':'4.4.1-test','key_id':issuer.fingerprint(issuer.public_bytes(key)),
                'public_key':base64.b64encode(issuer.public_bytes(key)).decode(),'product_id':'ledger_plus'}))
            run_gui(home,report)
    else:
        run_gui(args.bundle or bundle_root())

if __name__=='__main__':main()
