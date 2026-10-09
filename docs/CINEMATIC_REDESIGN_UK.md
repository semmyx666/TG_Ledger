# TG Ledger Cinematic — детальний дизайн і план production-реалізації

## 0. Статус цього пакета — без маркетингових перебільшень

Це окрема гілка вихідного проєкту 2.1.0 зі **справжніми Compose-компонентами і Room-запитами**, а не лише описом. Готові в коді: екран аналітики, календарний місяць/весь час, стовпчики, підказки, кільце категорій, точні суми/відсотки/кількість транзакцій за типом, переходи SharedTransition, каскад, цифри, інтеграція навігації, OLED-палітра, запит високої частоти дисплея, controller/rig-контракт кота.

**Не готове:** гіперреалістичний котячий asset, конкретний Filament renderer, lip-sync, повноцінна LLM, pixel-perfect звірка рендерів Android зі скріншотами, перевірка 120 FPS на телефоні. Присутній ресурсний preview із прямою позначкою про відсутність 3D. Не слід називати його високополігональною моделлю. Android/Kotlin-збірка і JUnit у sandbox не виконані; потрібен нормальний Android toolchain.

Ця гілка замінює попередній low-power профіль на cinematic-профіль для візуального екрана. Навіть без ліміту батареї зупиняємо GPU/голос, коли застосунок не видимий: це правильна робота lifecycle, а не обмеження якості.

## 1. Що саме взято з чотирьох референсів

### Екран A — аналітика серпня/вересня

- Верхня сегментована плашка «Історія / Аналітика», зелений активний текст та іконка стовпчиків.
- Другий ряд: місяць з календарем, праворуч валюта. **Картки/банківські рахунки не вигадуємо:** у наявній моделі застосунку немає окремих рахунків. Правий контрол відкриває реальний вибір валюти в налаштуваннях, не фіктивний лічильник «1 картка».
- Картка витрат: назва періоду, точна від’ємна сума, зміна проти попереднього періоду, графік із фіолетовим градієнтом, датами та середнім значенням.
- Стовпчики — фрагменти календарних тижнів: для серпня 2026 це 1–2, 3–9, 10–16, 17–23, 24–30, 31–31. Нульові тижні не зникають.
- Натискання на стовпчик показує справжні від/до і суму. Для поточного неповного місяця порівнюємо з такими самими elapsed days попереднього місяця, не з повним минулим місяцем. У підписі це пояснено.
- Дві окремі картки: витрати, надходження. До чотирьох міні-іконок із сумами/частками; «Більше» відкриває повний список.

### Екрани B/C — витрати/надходження

- Back, назва типу, період та велика підсумкова сума.
- Велике кільце, діаметр за шириною вікна, товщина приблизно 22% ширини; центр вільний для іконки, категорії, суми та частки.
- Витрати — рожеві, блакитні, фіолетові й нейтральні сектори; надходження — зелені. Відтінок категорії стабільний між карткою, кільцем і списком.
- Tap сектору змінює центральний контекст та відкриває список відповідної категорії. Tap рядка має той самий ефект.
- Повний список рядків: значок, категорія, **кількість саме витрат або саме надходжень**, сума і відсоток. Нижче — фактичні транзакції, дати, нотатки, редактор і «Показати ще».

Референс має темний, але не абсолютно чорний фон. За твоїм новим побажанням зовнішнє тло змінено на **#000000 OLED**. Не копіюємо iOS status bar: Android малює власний системний рядок.

## 2. Дизайн-система

| Токен | Значення / принцип |
|---|---|
| Background | #000000 |
| Cards | #202121 |
| Secondary text | #9A9F9B |
| Accent / More | #99D64A |
| Income | #19B950 / #69CF78 |
| Chart highlight | #DDCAFF → #976FFF |
| Food | #E53D83 |
| Transport | #21AECE |
| Borders | #333633, переважно без виразної рамки |
| Corner radius | картки 18dp, внутрішні плашки 12dp, segmented 100dp |
| Page padding | 18dp; вертикальні проміжки 20–22dp |
| Touch target | не менше 48dp для кнопок і категорій |
| Typography | системний sans-serif; гроші monospace для стабільного одометра |

Production-полірування: вбудувати ліцензований variable-font із tabular numerals, перевірити кирилицю, system font scale 1.0–2.0, вузькі екрани 320dp, foldables. Збільшення тексту не має обрізати гроші чи вимикати тапи.

Дані на скріншотах — приклади, не база користувача. Код НЕ додає вигаданих «−2186»/«+1043» у Room і не створює фальшиві транзакції.

## 3. Карта коду

```
domain/.../CinematicAnalytics.kt
  CategorySlice, CashBucket, AnalyticsMath.slices/buckets
app/.../presentation/analytics/AnalyticsViewModel.kt
  month/focus/limit → Room flows → AnalyticsState
app/.../presentation/analytics/AnalyticsScreen.kt
  overview, month picker, category hero, full transaction list
app/.../presentation/analytics/CinematicCharts.kt
  CascadeCard, Odometer, PremiumBars, PremiumDonut, design tokens
app/.../presentation/premium/LiveCatMotion.kt
  CatPose, CatMotionController, CatRigDriver, CatAssetRenderer, lifecycle host
app/.../platform/HighRefreshRate.kt
  supported display mode request; no pretend FPS guarantee
```

Навігація: нижня кнопка «Аналітика» → `analytics`, «Історія» → наявний dashboard, tap транзакції → наявний редактор. PIN/Keystore/вихідна база/applicationId не змінюються. Грошові та голосові виправлення 2.0 залишені.

## 4. Точність цифр — окремо від ефектів

- Гроші — `Long` у мінорних одиницях. Не зберігати кошти у Float/Double.
- SQL відсікає іншу валюту, видалені записи, записи поза датами та типом операції.
- Частки рахуються через BigDecimal. Для UI використано 10 000 basis points і largest-remainder rounding: сума відсотків дорівнює **100.00%**. Кут кільця використовує справжнє відношення сум, не округлений рядок.
- Немає ділення на нуль; якщо немає даних, показується empty state, а не «NaN%» або фальшиве повне кільце.
- Під час одометра змінюється лише проміжне відображення. Фінальний текст, доступний опис і голос — точні.
- Доходи та витрати мають різні SQL-лічильники `incomeCount`/`expenseCount`: змішана категорія не дає хибного числа транзакцій.
- Весь час може мати десятки років: стовпчики обмежені 48 агрегованими групами. Загальний підсумок/список не обрізаються. Це обмеження читабельності, не прихований ліміт батареї.
- У деталях усе дозавантажується сторінками. Не потрібно вантажити мільйон рядків лише для відкриття анімованої картки.

## 5. Motion specification — що відбувається при кожній дії

| Подія | Специфікація |
|---|---|
| Вхід на аналітику | alpha 0→1 за 420ms, translateY 24→0dp зі spring; stagger 55ms |
| Поява суми | 720ms count-up; кожен символ має вертикальний roll + fade 85ms |
| Стовпчики | 850ms ease-in-out, невеликий послідовний зсув старту |
| Кільце | 950ms заповнення секторів з нуля |
| Category → detail | SharedTransitionLayout/sharedBounds, спільний ключ type/category, 380ms enter / 240ms exit |
| Back | зворотний bounds transition; звичайний back і системний back узгоджені |
| Період | нові дані з Flow; графік і суми перевідтворюють анімацію |
| Cat gaze | експоненційне згладжування, незалежне від FPS: 1−exp(−9·dt) |
| Cat breath | синус, цикл 3.6s |
| Cat blink | procedural 220ms blink, приблизно кожні 4.7s |
| Mood | плавний перехід pleased ↔ serious залежно від реального netMinor |

Фінансова точність важливіша за анімацію: при швидких оновленнях зберігається останній стан, а не черга старих «красивих» чисел. Production-полірування: зберігати окремий outgoing snapshot в AnimatedContent, заборонити повторний tap під час критичної частини hero, перевірити швидке back/reopen і predictive back на пристрої.

Android animator scale/reduced motion слід поважати. «No Battery Limits» не означає ігнорувати accessibility чи крутити GPU в заблокованому застосунку.

## 6. Гіперреалістичний кіт: справжній production pipeline

### Рекомендований шлях — 3D, а не відео

Відео має фіксований ракурс і погано слідкує за дотиками. Тому для справжнього gaze/mood рекомендується **rigged GLB + Filament**. Відео з alpha — альтернативний кінематографічний idle, не еквівалент керованої моделі.

**Потрібні арт-ресурси:**
- ліцензований cat.glb з ригом голови, очей, вух і тіла;
- героїчна деталізація, орієнтир 80k–200k трикутників після ретопології, а не некеровані мільйони полігонів;
- 2K–4K albedo/normal/roughness/AO, окремі eye/cornea матеріали;
- fur cards або shell fur; густина й shader складність визначаються профілюванням;
- morph names: `breath`, `blinkLeft`, `blinkRight`, `pleased`, `serious`, `earsForward`;
- idle/listening/talking loops, якщо потрібні скелетні кліпи, і окремі phoneme/viseme morphs для lip-sync;
- власне HDRI/IBL, маленький key light, теплий rim light, м’яка contact shadow.

**Код у пакеті:** `CatPose` описує всі motion channels, `CatMotionController` формує їх зі справжнього балансу й touch target, `CatRigDriver` застосовує до `CatRig`, `CatAssetRenderer` приймає pose та малює asset. `LiveCatHost` запускає frame-clock лише на RESUMED-екрані. `trackCatTouch` спостерігає touches у головному екрані й НЕ споживає їх, тому не ламає прокрутку або кнопки.

**Кроки Filament adapter, які ще потрібно виконати:**
1. Обрати сумісну фіксовану версію Filament/gltfio та додати Gradle dependencies після перевірки потрібних ABI/minSdk. У цьому пакеті їх немає, бо без asset і реального рендерера така залежність створювала б лише хибне враження готового 3D.
2. Створити Engine/Renderer/Scene/View/Camera, gltf AssetLoader/ResourceLoader, завантажити asset і текстури поза UI thread, додати entities у Scene.
3. Поставити orthographic або perspective camera на потрібний крупний план; background alpha=0, правильно підібрати premultiplied alpha blending і композицію Android view.
4. TextureView з прозорістю простіше компонувати з Compose, але має додаткову композиційну вартість. SurfaceView потребує окремої перевірки z-order, прозорості й участі в transitions. Для hero кота використати snapshot-текстуру, а не припускати, що SharedTransition автоматично перенесе зовнішній Surface.
5. Реалізувати `CatRig.morph` через morph targets відповідних renderables, `CatRig.head` — через local bone transforms без накопичення Euler drift. Перевірити відсутні morph names, clamp weight 0..1, bind pose та координатні осі.
6. Реалізувати `CatAssetRenderer.Render`: AndroidView host + керування renderer, pose → CatRigDriver → render frame. Не створювати Engine на кожній recomposition.
7. Install реалізацію через `CatAssetRegistry.renderer` у composition root. Preview позначка автоматично зникне.
8. On pause — зупинити loop; on dispose — від’єднати surface, знищити asset/swapchain/resource loader/renderer/engine у правильному порядку на потрібному thread.
9. Розділити happy/serious від слова «сумний»: фінансовий мінус не повинен засуджувати користувача. У негативному балансі погляд уважний, вуха нейтральні, голос підтримувальний.
10. Для talking/lip-sync використати TTS utterance progress + часові маркери чи фонемний backend. Точний lip-sync НЕ можна отримати з одного `speak()` без додаткових даних.

**Альтернативний video-look:** high-quality transparent master (ProRes 4444 для виробництва), Android-friendly asset із підтвердженою підтримкою альфи/власним dual-plane shader. Не обіцяти, що звичайний MP4/H.264 має alpha. Для dual-plane: RGB video + alpha mask, синхронізований decoder і GPU composition. Gaze забезпечується окремими кліпами/ракурсами й blending, але не буде таким керованим, як справжня 3D-модель.

## 7. 120 FPS — вимірюваний performance contract

120 Гц дає **8.33ms на кадр**. Бажані бюджети після warmup: UI/animation work до 2ms, RenderThread/Compose до 2ms, 3D GPU приблизно до 4ms. Це орієнтири для профілювання, не результати вимірювань цього пакета.

`HighRefreshRate.request` вибирає підтримуваний режим з тією самою фізичною роздільною здатністю, віддаючи перевагу ≥120 Гц, і робить запит Window. Android/дисплей/thermal governor можуть відмовити. На 60-Гц екрані код не створить 120 FPS; на 120-Гц панелі важкі fur/blur shader-и також можуть опустити FPS.

Production-перевірка:
- Release build, minify, baseline profile, хоча б 60s warmup.
- Perfetto, FrameTimeline, JankStats/Macrobenchmark; окремо UI/GPU/decoder frame pacing.
- Scenarios: cold start, scroll, month switch, ring tap, category hero, клавіатура, listening, 3D plus NLP, background/resume.
- 30 хв безперервного тесту для thermal throttling.
- Ціль: p95 frame time ≤8.33ms на погодженому 120-Гц пристрої; для інших пристроїв — явно визначений fallback 90/60.
- Heavy assets допустимі, але main thread не повинен декодувати текстури/модель чи виконувати NLP/SQL.
- Frame pacing через frame clock/Choreographer; не `delay(8)`, не Timer і не нескінченний loop без VSync.
- Окремо міряти пам’ять, startup asset decode, shader warmup. «Вага неважлива» не усуває OOM і shader compilation stutter.

## 8. Інтелект і голос

Збережено шлях 2.0: верхній помічник → голосовий результат або текст → FinanceQuery → структурований запит → SQL → точний текст → TTS.

Приклади працюють у локальній логіці:
- «скажи витрати за місяць»;
- «скажи витрати за місяць за продукти»;
- «Скай, який баланс?» → `SpokenMoney`, явне «мінус», а не лише символ −.

Модель не генерує фінансові суми. Якщо потрібна повноцінна LLM: окремий inference worker/backend повертає JSON intent, після schema/period/category validation виконується SQL. Відповідь з числами формується з результату SQL. Не передавати транзакції у сторонній сервіс без явної згоди користувача. Поточний пакет не містить нейромоделі.

## 9. QA та критерії приймання

1. Pixel comparison на цільових Android розмірах зі scaled референсом; відступи/текст/кольори/розташування звірити окремо. Скриншоти не задають готові dp або font metrics.
2. Нуль/одна категорія/сім категорій/дуже маленький сектор; відсотки разом 100.00%.
3. Точний negative balance у UI/TTS, нуль без «мінус», великі суми без overflow.
4. Серпень 2026 має 6 calendar-week buckets; лютий високосного року — коректні межі.
5. Змішані income/expense у категорії — коректні типові count і суми.
6. Понад 50 транзакцій — правильний summary та повне дозавантаження.
7. Tap bar date та Canvas target дають той самий exact amount.
8. Hero back/rapid repeated taps/rotate/lock/system back без пропадання private content.
9. TalkBack, reduced animations, large font, вузький екран.
10. Реальна котяча модель: видимі breath/blink/gaze/mood, немає asset reload на recomposition, немає витоків Engine.
11. 120 FPS підтверджувати трасами, не словами чи записом screen recorder.

## 10. Збірка

JDK 17, Android Studio/SDK 36, мережа до Gradle/Maven:

```bash
./gradlew :domain:test :data:testPlayDebugUnitTest :app:testPlayDebugUnitTest :app:assemblePlayDebug
```

Для тестів пристрою:

```bash
./gradlew :app:connectedPlayDebugAndroidTest
```

Архів містить file-by-file SOURCE_CODE.md, тести, ресурси, контрольні суми та лог попередньої невдалої спроби збірки. Не встановлюй шляхом видалення старої інсталяції, якщо потрібна її база; applicationId/підпис зберегти. Cinematic versionCode=3 — робочий, перед публікацією звірити з Play Console.
