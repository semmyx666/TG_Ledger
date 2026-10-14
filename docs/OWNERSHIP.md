# Авторство та захист TG Ledger

Власник: **Semmyx** (GitHub `semmyx666`, semmyxofficial666@gmail.com). Усі права захищено — див. `LICENSE`.

## Що вже зроблено
1. **LICENSE (All rights reserved)** — без письмового дозволу копіювати, публікувати, збирати й розповсюджувати код заборонено.
2. **Печатка авторства** `OwnershipSeal.ID` = перші 32 hex-символи SHA-256 від рядка
   `TG Ledger|semmyx666|semmyxofficial666@gmail.com|2026` → `11b3e1bb7fc072422cf2d25d258c65c8`.
   Вона є в коді та в кожній збірці (APK/AAB) і не вирізається обфускацією (`System.setProperty` у `LedgerApplication`).
   Перевірити: `printf 'TG Ledger|semmyx666|semmyxofficial666@gmail.com|2026' | sha256sum`; в APK — `strings classes*.dex | grep 11b3e1bb7fc0`.
3. **Часова мітка в блокчейні Bitcoin (OpenTimestamps)** — каталог `proof/`: `MANIFEST-<версія>.sha256` (SHA-256 усіх файлів проекту) і `.ots`-доказ.
   Він підтверджує, що саме така версія коду існувала не пізніше дати блоку. Перевірка: `pip install opentimestamps-client`, потім `ots verify proof/MANIFEST-3.3.1.sha256.ots`.
   Одразу після створення доказ «pending»; через кілька годин виконайте `ots upgrade proof/MANIFEST-3.3.1.sha256.ots` і закомітьте оновлений файл.
4. **Історія git** на GitHub (автор `semmyx666`, дата першого коміту) — додатковий незалежний доказ.
5. **R8/ProGuard** увімкнено для release — імена класів і методів у встановленому APK обфусціровані.

## Що потрібно зробити вам (код на GitHub зараз публічний!)
- **Зробіть репозиторій приватним**: GitHub → репозиторій → Settings → General → Danger Zone → *Change visibility* → *Make private*.
  Це єдиний справжній захист від копіювання — публічний код неможливо «зашифрувати» й водночас залишити читабельним.
  CI (Actions) продовжить працювати.
- Release-підписувати збірки **своїм** keystore (файл і паролі не комітити; `*.jks` вже в `.gitignore`).
- Для Google Play: підписання Play App Signing + обліковий запис розробника на ваше ім'я — це ще один доказ.

## Як довести авторство, якщо код скопіювали
1. Покажіть git-історію й дату першого коміту в акаунті `semmyx666`.
2. Покажіть `.ots`-доказ (дата в блокчейні) + manifest, що збігається з копією.
3. Знайдіть у чужому APK/коді печатку `11b3e1bb7fc072422cf2d25d258c65c8` (її не знає ніхто, окрім вас — вона виведена з вашого імені/пошти) та файл LICENSE.
4. Подайте скаргу: GitHub DMCA (https://github.com/contact/dmca), Google Play (форма Copyright/DMCA).
