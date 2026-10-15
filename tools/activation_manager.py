#!/usr/bin/env python3
"""Local Windows-friendly GUI. Does not send keys/codes to any server."""
from pathlib import Path
import os
import queue
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

PROJECT = Path(__file__).resolve().parents[1]


def main():
    root = tk.Tk()
    root.withdraw()
    try:
        import issue_activation_code as issuer
    except SystemExit as error:
        if not messagebox.askyesno('Нужна библиотека cryptography',
                'Для локальной подписи нужна cryptography. Установить её из PyPI сейчас? Приватные ключи никуда не передаются.'):
            return
        try:
            subprocess.run([sys.executable, '-m', 'pip', 'install', '-r', str(PROJECT / 'tools/requirements-activation.txt')], check=True)
        except Exception as exc:
            messagebox.showerror('Установка не завершена', str(exc))
            return
        os.execv(sys.executable, [sys.executable, str(Path(__file__).resolve())])
    root.deiconify()
    root.title('TG Ledger 4.4.0 — менеджер активаций')
    root.geometry('820x700')
    root.minsize(580, 560)
    root.columnconfigure(0, weight=1)
    frame = ttk.Frame(root, padding=16)
    frame.grid(sticky='nsew')
    frame.columnconfigure(0, weight=1)
    root.rowconfigure(0, weight=1)
    frame.rowconfigure(10, weight=1)
    status = tk.StringVar(value='Импортируйте прежний ключ или явно создайте новый. Ключ не хранится в папке версии.')
    ttk.Label(frame, textvariable=status, wraplength=740).grid(row=0, column=0, sticky='ew', pady=(0,12))
    actions = ttk.Frame(frame)
    actions.grid(row=1, column=0, sticky='ew')
    output = tk.Text(frame, wrap='word', height=11)
    jobs = queue.Queue()
    busy = False
    buttons = []

    def log(value):
        output.insert('end', str(value) + '\n')
        output.see('end')

    def doctor():
        s = issuer.inspect_project(PROJECT)
        if not s['matches']:
            raise ValueError('Ключ и проект не совпадают. Импортируйте нужный ключ или нажмите «Настроить проект».')
        status.set('Ключ издателя и проекта: ' + s['key_id'] + '\nПриватный ключ: ' + s['private_path'])
        return s

    def run_action(action):
        try:
            action()
        except Exception as exc:
            messagebox.showerror('Действие не выполнено', str(exc))
            log('Ошибка: ' + str(exc))

    def configure(source=None, create=False):
        s = issuer.setup(PROJECT, source, create)
        status.set('Настроено. Key ID: ' + s['key_id'] + '\nКлюч: ' + s['private_path'])
        log('Публичная настройка записана автоматически. Пересоберите/установите приложение один раз и сравните Key ID в нём.')
        log('Резервную копию приватного ключа храните отдельно от проекта. Не отправляйте её в чат/GitHub.')

    def import_key():
        filename = filedialog.askopenfilename(title='Выберите ПРЕЖНИЙ приватный ключ', filetypes=[('PEM private key','*.pem'),('All files','*')])
        if filename:
            configure(Path(filename))

    def create_key():
        if messagebox.askyesno('Новый ключ — только при необходимости',
                'Импорт прежнего ключа предпочтительнее. Новый ключ не подходит к уже установленному APK со старым ключом: потребуется пересборка/установка, старые коды без прежнего ключа не пройдут. Создать новый ключ?'):
            configure(create=True)

    for index, (title, action) in enumerate([('Импорт прежнего .pem', import_key), ('Создать новый ключ', create_key),
                          ('Настроить проект', configure), ('Проверить', doctor)]):
        button = ttk.Button(actions, text=title, command=lambda f=action: run_action(f))
        actions.columnconfigure(index % 2, weight=1)
        button.grid(row=index // 2, column=index % 2, sticky='ew', padx=(0,6), pady=4)
        buttons.append(button)
    ttk.Label(frame, text='1. Из приложения: Настройки → активация → «Скопировать запрос». Вставьте запрос ниже.\nМожно использовать старый Installation ID, но тогда отдельно сверяйте Key ID.').grid(row=2,column=0,sticky='w',pady=(14,4))
    request = tk.StringVar()
    ttk.Entry(frame, textvariable=request).grid(row=3,column=0,sticky='ew')
    row = ttk.Frame(frame);row.grid(row=4,column=0,sticky='ew',pady=8)
    ttk.Label(row,text='Дней (0 = бессрочно):').pack(side='left')
    days = tk.StringVar(value='30')
    ttk.Entry(row,textvariable=days,width=8).pack(side='left',padx=8)
    code = tk.Text(frame,height=5,wrap='char')
    code.grid(row=6,column=0,sticky='ew')

    def issue():
        value = request.get().strip()
        if value.startswith('TGREQ1|'):
            installation, expected, product = issuer.parse_request(value)
        else:
            installation, expected, product = issuer.normalize_installation(value), None, issuer.DEFAULT_PRODUCT_ID
            if not messagebox.askyesno('Сверьте Key ID', 'Запрос не содержит Key ID приложения. Вы сверили его с Key ID вверху окна?'):
                return
        token, _ = issuer.issue_checked(installation,float(days.get()),PROJECT,expected_key=expected,product=product)
        code.delete('1.0','end');code.insert('1.0',token)
        log('Код выдан. Подпись проверяется офлайн; выдавайте только после подтверждения оплаты.')

    button=ttk.Button(row,text='Выдать код',command=lambda:run_action(issue));button.pack(side='left',padx=4);buttons.append(button)

    def copy_code():
        token=code.get('1.0','end').strip()
        if not token:raise ValueError('Сначала выдайте код.')
        root.clipboard_clear();root.clipboard_append(token);root.update()
        log('Код скопирован без поясняющего текста.')
    ttk.Button(frame,text='Скопировать только код',command=lambda:run_action(copy_code)).grid(row=7,column=0,sticky='w',pady=8)
    buildrow=ttk.Frame(frame);buildrow.grid(row=8,column=0,sticky='ew',pady=8)

    def build(install=False):
        nonlocal busy
        if busy:return
        doctor()
        busy=True
        for b in buttons:b.configure(state='disabled')
        log('Сборка началась. Нужны JDK 17/21/поддерживаемая Gradle JDK, Android SDK и доступ к зависимостям. Для установки подключите телефон с USB debugging.')
        def worker():
            try:
                wrapper='gradlew.bat' if os.name=='nt' else 'gradlew'
                # Fixed tasks; no user-entered text is executed as shell code.
                cmd=(['cmd.exe','/d','/c',wrapper] if os.name=='nt' else ['sh',wrapper])+[
                    '-PREQUIRE_DEVELOPER_ACTIVATION=true',':app:installPlayDebug' if install else ':app:assemblePlayDebug']
                with subprocess.Popen(cmd,cwd=PROJECT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace') as process:
                    for line in process.stdout:jobs.put(('log',line.rstrip()))
                    result=process.wait()
                jobs.put(('log','Готово: app/build/outputs/apk/play/debug/app-play-debug.apk' if result==0 else 'Сборка/установка не завершена. Исправьте ошибку выше; установленное приложение не удаляйте.'))
            except Exception as exc:jobs.put(('log','Ошибка запуска сборки: '+str(exc)))
            finally:jobs.put(('done',None))
        threading.Thread(target=worker,daemon=True).start()

    for title, install in [('Собрать APK с этим ключом',False),('Собрать и установить по USB',True)]:
        b=ttk.Button(buildrow,text=title,command=lambda v=install:run_action(lambda:build(v)));b.pack(side='left',padx=(0,8));buttons.append(b)
    ttk.Label(frame,text='Обновление поверх приложения требует прежней APK-подписи. Это другой ключ, не activation .pem.\nМенеджер не удаляет приложение, не меняет его данные и не проверяет банковские счета.').grid(row=9,column=0,sticky='w')
    output.grid(row=10,column=0,sticky='nsew',pady=(10,0))
    def wrap_labels(event):
        for child in frame.winfo_children():
            if isinstance(child, ttk.Label): child.configure(wraplength=max(200,event.width-40))
    frame.bind('<Configure>',wrap_labels)
    def drain():
        nonlocal busy
        while not jobs.empty():
            kind,value=jobs.get()
            if kind=='log':log(value)
            else:
                busy=False
                for b in buttons:b.configure(state='normal')
        root.after(100,drain)
    def close():
        if busy and not messagebox.askyesno('Сборка выполняется','Закрыть менеджер? Сборка может продолжиться отдельно.'):return
        root.destroy()
    root.protocol('WM_DELETE_WINDOW',close)
    drain()
    try:doctor()
    except Exception as exc:log(str(exc))
    smoke_failure = []
    if '--smoke-test' in sys.argv:
        def smoke():
            try:
                s = doctor()
                request.set('TGREQ1|AB12-CD34-EF56|' + s['key_id'] + '|ledger_plus')
                days.set('5')
                issue()
                copy_code()
                assert root.clipboard_get().strip().startswith('TG2.' + s['key_id'] + '.')
                print('GUI_SMOKE_OK: window, project diagnosis, issuance and clipboard')
            except Exception as exc:
                print('GUI_SMOKE_FAILED:', str(exc), file=sys.stderr)
                smoke_failure.append(str(exc))
                root.destroy()
                return
            root.destroy()
        root.after(300, smoke)
    root.mainloop()
    if smoke_failure:
        raise SystemExit(1)

if __name__=='__main__':
    if '--smoke-test' in sys.argv:
        import tempfile
        with tempfile.TemporaryDirectory(prefix='tg-ledger-gui-') as temporary:
            PROJECT = Path(temporary) / 'project with spaces'
            PROJECT.mkdir()
            os.environ['TG_LEDGER_KEY_HOME'] = str(Path(temporary) / 'stable key')
            import issue_activation_code as issuer
            issuer.setup(PROJECT,create_new=True)
            main()
    else:
        main()
