# Карта проекта (где что лежит)

Корневой пакет: `com.semmyx.ledger`. Три Gradle-модуля: `app` (экраны и Android), `domain` (чистая логика), `data` (база, сеть, безопасность, платежи).
Папки названы по темам, чтобы нужное находилось сразу.

## 🏦 Банк (импорт транзакций)

| Модуль | Папка | Что внутри |
|---|---|---|
| app | `app/src/main/kotlin/com/semmyx/ledger/bank/` | `BankNotificationListener` (чтение уведомлений банков), `BankSmsReceiver` (SMS), `BankScreen` (экран), `BankViewModel` |
| domain | `domain/src/main/kotlin/com/semmyx/ledger/domain/bank/` | `BankParser` (разбор текста уведомлений), `BankRepository` (контракты) |
| data | `data/src/main/kotlin/com/semmyx/ledger/data/bank/` | `BankEntities` (таблицы и DAO), `BankImportRepository` (импорт, дедупликация) |

> `BankNotificationListener` и `BankSmsReceiver` намеренно остались в пакете `…ledger.bank`: Android хранит разрешение на доступ к уведомлениям по полному имени класса, и при переезде его пришлось бы выдавать заново.

## 🐱 Кот (внешний вид и анимация)

`app/src/main/kotlin/com/semmyx/ledger/cat/`

| Файл | Назначение |
|---|---|
| `CatFaceMesh.kt` | сетка морды кота |
| `CatMeshRenderer.kt` | отрисовка кота (серый / небесный скин) |
| `CatAnimation.kt` | поза, моргание, движения, реакция на касания |
| `CatAssistantCard.kt` | карточка кота-помощника на главном экране |

Картинки кота лежат в `app/src/main/res/drawable-nodpi/` (`cat_amber_base.webp`, `cat_amber_sky.webp`): Android не поддерживает подпапки в `res`.

## 🎙 Голос кота

`app/src/main/kotlin/com/semmyx/ledger/voice/`

| Файл | Назначение |
|---|---|
| `OptionalVoiceAssistant.kt` | распознавание речи и озвучка |
| `VoiceEntryParser.kt` | «потратил 100 грн на еду» → запись |
| `VoiceFinanceQuery.kt` | вопросы «какой баланс», «траты за месяц» |
| `VoiceSmallTalk.kt` | обычные фразы: привет, спасибо и т. д. |
| `SpokenMoneyFormatter.kt` | как произносить суммы («минус 2000 гривен») |

## Остальные экраны (`app/.../ledger/`)

| Папка | Что это |
|---|---|
| `navigation/` | `AppRoot` (навигация), `AppViewModel` |
| `dashboard/` | главный экран, баланс, график, детали категорий |
| `analytics/` | аналитика и графики (`AnalyticsCharts.kt`) |
| `history/` | история операций |
| `editor/` | добавление и редактирование записи |
| `settings/` | настройки и цвета |
| `auth/` | PIN и биометрия |
| `onboarding/` | первый запуск |
| `support/` | помощь и поддержка (`HelpScreen`, `SupportSheet`, `SupportWebActivity`) |
| `theme/` | тема и палитра |
| `common/` | общие компоненты и вспомогательные классы ViewModel |
| `platform/` | системные адаптеры (биометрия, частота кадров, обновление курсов) |
| `diagnostics/` | `CrashLogger` |

В корне лежат точки входа: `LedgerApplication`, `MainActivity`, `AppContainer`.

## Данные (`data/.../data/`)

`bank/` (банк), `local/` (база Room: `LedgerDatabase`, `LedgerEntities`, `LedgerDao`), `repository/` (записи, настройки, курсы), `security/` (PIN, ключи), `billing/` (подписка), `remote/` (курсы НБУ).
Не переименовывайте `LedgerDatabase` и не переносите её в другой пакет: от этого зависит имя папки со схемами Room в `data/schemas/`.
