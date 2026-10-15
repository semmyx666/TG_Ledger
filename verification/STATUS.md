# Текущий статус — 4.4.1

**Готовы signed APK и автономный Windows Create Key Premium.** Все четыре CI job прошли: https://github.com/semmyx666/TG_Ledger/actions/runs/38804609923

- Android/JVM: 138 запусков; Windows Python: 28 тестов; ноль ошибок/пропусков.
- Windows GUI + clipboard и реальный packaged EXE — успешно.
- Android 8 / 16: по 27 инструментальных тестов — успешно.
- Тот же подписанный release APK установлен и запущен на обеих версиях; nondebuggable, без startup crash.
- Подпись v2/v3, 16K alignment и ключ внутри DEX проверены. Личный генератор согласован с APK; production private key не находится в GitHub/CI.
- Банковские API, push, Plus-периоды, данные и прежние функции сохранены.

Отчёт и ограничения: `RELEASE_4.4.1.md`, `release-4.4.1.json`. Инструкция: `../docs/READY_APK_4.4.1_RU.md`. Личный архив содержит секреты и передаётся отдельно владельцу. Не удаляйте старое приложение при конфликте подписи.

Исторические подтверждения: `RELEASE_4.4.0.md`, `release-4.4.0.json`, `history/4.3.3/`, `history/3.3.3/`.
