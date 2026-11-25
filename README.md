# SableLimits

Серверный мод для NeoForge 1.21.1, ограничивающий количество блоков на физических структурах
**Sable / Create: Aeronautics** и на обычных **Create Contraptions**.

Мод ставится только на сервер. Клиенту он не нужен: `displayTest = IGNORE_ALL_VERSION`,
вся логика и сообщения формируются на сервере.

## Что умеет

- Лимиты по точному ID блока: `create:deployer = 16`.
- Лимиты по тегу: `#create:seats = 16`.
- Групповые лимиты: несколько ID/тегов в одну категорию с общим лимитом.
- Список запрещённых блоков: если такой блок есть в конструкции, сборка отменяется,
  а на готовый Sable-корабль его нельзя поставить. Всё в конфиге, без датапаков.
- Раздельные области действия:
  - без префикса — только Sable/Aeronautics;
  - `C ` — только обычные Create Contraptions;
  - `* ` — и Sable, и Create.
- Запрет установки блока на уже собранный корабль рукой и через `create:deployer`.
- Отмена сборки Sable-структуры, если превышен лимит блока или общий размер.
- Отмена сборки обычного Create-контрапшена при превышении лимитов `C`/`*`.
- Общий лимит размера Sable-структуры (`defaultMaxBlocks`).
- Права LuckPerms/NeoForge PermissionAPI:
  - `sablelimits.bypass.blocks` — обход поштучных и групповых лимитов;
  - `sablelimits.bypass.size` — обход общего лимита размера;
  - `sablelimits.limit.size` (мета LuckPerms) — персональный лимит размера.
- Персистентный кэш счётчиков в `world/data/sablelimits.dat`.
- TTL-очистка неактивных записей (`cacheExpirationDays`, по умолчанию 14 дней).
- Команда `/sablelimits reload` и `/sablelimits status`.

## Требования

- Minecraft 1.21.1, NeoForge 21.1.228+ (проверено на 21.1.267).
- Sable 2.0.6+.
- Create 6.0.10+.
- Create: Aeronautics 1.3.2+ (внутри — Simulated 1.3.2). Для отмены сборки Sable-структур
  нужен именно Simulated: перехватывается его вызов `SubLevelAssemblyHelper.assembleBlocks`.
  Без Simulated проверки установки блоков работают, отмена сборки Sable — нет.
- Java 21.
- LuckPerms — опционально; без него работают дефолтные значения прав (все запреты включены).

## Установка

1. Положить `sablelimits-1.0.0.jar` в папку `mods` сервера.
2. Запустить сервер. При первом старте создастся `config/sablelimits.json`.
3. Настроить лимиты и выполнить `/sablelimits reload` (или перезапустить сервер).

Клиентские моды менять не нужно. Если jar случайно окажется на клиенте, мод не будет
применять свои миксины на клиенте (они объявлены в секции `server`).

## Конфиг

Файл: `config/sablelimits.json`. Пример — `docs/sablelimits.example.json`.

| Поле | По умолчанию | Описание |
|---|---:|---|
| `enabled` | `true` | Включает проверки. Счётчики при этом всё равно обновляются. |
| `cacheExpirationDays` | `14` | Через сколько дней удалять неактивную запись кэша. `0` — не удалять. |
| `cleanupIntervalHours` | `24` | Как часто проверять TTL. `0` — только при старте. |
| `defaultMaxBlocks` | `3500` | Общий лимит блоков Sable-структуры. `0` — без лимита. |
| `assemblyActorRadius` | `32` | Радиус поиска игрока для bypass-прав и уведомлений при сборке. |
| `messageCooldownTicks` | `20` | Антиспам ActionBar при отказах установки. |
| `assemblyUsesPlayerLimit` | `false` | Если `true`, при сборке берётся `limit.size` ближайшего игрока, иначе только `defaultMaxBlocks`. |
| `limits` | примеры | Правила лимитов. |
| `groups` | `engines` | Группы блоков для `group:<имя>`. |
| `forbiddenBlocks` | `["* minecraft:bedrock"]` | Блоки, с которыми сборка отменяется. Поддерживаются ID, теги и группы; префиксы `C`/`*` работают так же, как в `limits`. |
| `messages` | русские | Тексты сообщений с `&`-цветами. |

Перезагрузка без рестарта:

```
/sablelimits reload
/sablelimits status
```

Команды требуют permission level 2 (op).

### Синтаксис правил

Ключ правила — строка в `limits`. Префикс области действия опционален:

| Ключ | Где действует |
|---|---|
| `create:deployer` | Sable / Aeronautics |
| `C create:deployer` | обычные Create Contraptions |
| `* create:mechanical_drill` | и Sable, и Create |

После префикса:

| Форма | Пример |
|---|---|
| ID блока | `create:deployer` |
| Тег блока | `#create:seats` |
| Группа | `group:engines` |

Значение — целое число. `0` или меньше отключает правило (с предупреждением в логе).

### Группы

```json
"groups": {
  "engines": [
    "simulated:red_portable_engine",
    "simulated:blue_portable_engine",
    "#create:engines"
  ]
},
"limits": {
  "group:engines": 16,
  "C group:engines": 32,
  "* group:engines": 64
}
```

В группе можно указывать ID, теги и другие группы. Циклы обнаруживаются и пропускаются
с предупреждением.

### Запрещённые блоки

`forbiddenBlocks` — список блоков, при наличии которых **сборка отменяется**.
Формат тот же, что у ключей `limits`: ID, `#тег`, `group:имя`; префикс области действия
опционален.

```json
"forbiddenBlocks": [
  "minecraft:bedrock",
  "* create:mechanical_drill",
  "C #create:some_tag",
  "group:forbidden_mods"
]
```

- без префикса — только Sable/Aeronautics;
- `C ` — только обычные Create Contraptions;
- `* ` — и Sable, и Create.

На готовом Sable-корабле такой блок нельзя поставить (проверка идёт через
`BlockItem.place`, как и лимиты). Право `sablelimits.bypass.blocks` обходит и этот запрет.
Сообщение — `messages.forbiddenBlock` с `%block%`.

По умолчанию в списке уже стоит `* minecraft:bedrock`. Если конфиг от старой версии мода,
раздел добавится автоматически при старте сервера или `/sablelimits reload`; текущие
значения при этом не меняются. Если раздела всё равно нет — проверьте, что в `mods`
лежит новый jar, и посмотрите `/sablelimits status`: там выводится
`запрещено блоков S=…, C=…`.

Это работает из конфига, без датапаков и правки тегов.

### Сообщения

Поддерживаются `&`-цвета и плейсхолдеры:

- `%block%` — ключ правила (для `forbiddenBlock` — ID найденного блока);
- `%current%` — сколько было бы после установки/сборки;
- `%limit%` — лимит;
- `%total%` — всего блоков в структуре;
- `%rules%`, `%sable%`, `%create%`, `%cached%` — для сообщения о reload.

## Права

Регистрируются через `net.neoforged.neoforge.server.permission.PermissionAPI`.
LuckPerms автоматически становится обработчиком прав.

```
/lp group staff permission set sablelimits.bypass.blocks true
/lp group staff permission set sablelimits.bypass.size true
/lp group sponsor meta set sablelimits.limit.size 6000
```

`limit.size` — строковая мета (NeoForge `PermissionTypes.STRING`), LuckPerms отдаёт её
как meta-значение. Если мета не задана или не число — используется `defaultMaxBlocks`.

`bypass.blocks` освобождает от поштучных и групповых лимитов, но не от общего размера.
`bypass.size` освобождает от общего размера, но не от поштучных лимитов.

При сборке Sable-структуры права ищутся у ближайшего игрока в радиусе
`assemblyActorRadius`. Если игрока рядом нет (автоматика), действует `defaultMaxBlocks`.

## Как считаются блоки

- Для каждой Sable-структуры (`SubLevel`) хранится запись: `dimension + UUID`, карта
  «блок → количество», общее число блоков, `lastUpdated`.
- Первичный подсчёт — один раз, при первом обращении после загрузки/сборки или после
  истечения TTL. Используются палитры секций чанков (`PalettedContainer.count`), а не
  перебор каждого блока AABB.
- Дальнейшие изменения блоков в плоте обновляют счётчики инкрементально через
  `LevelChunk.setBlockState` (+1/−1).
- Проверка установки — O(1) обращение к кэшу плюс проход по небольшому числу правил.
- Выгрузка структуры (`SubLevelRemovalReason.UNLOADED`) **не** удаляет кэш. Удаление
  происходит только при реальном удалении структуры, а также по TTL.
- TTL-очистка не снимает лимиты: при следующем взаимодействии запись будет пересоздана
  полным сканированием.

Файл кэша: `world/data/sablelimits.dat` (SavedData в оверворлде).

## Сборка Sable

Проверка встроена перед вызовом `SubLevelAssemblyHelper.assembleBlocks` в
`SimAssemblyHelper.assembleFromSingleBlock` (Physics Assembler, Swivel Bearing и т.п.).
При превышении лимита бросается `AssemblyException` — его уже обрабатывают ассемблер и
поворотный подшипник, поэтому блоки не перемещаются, а игроку уходит сообщение в чат.

После успешной сборки кэш новой структуры заполняется сканированием.

## Сборка Create

Проверка выполняется один раз в конце `Contraption.searchMovedStructure`, до удаления
блоков из мира. Правила с префиксом `C` и `*`; ограничение размера к Create не применяется.
При превышении бросается `AssemblyException` с сообщением.

## Ограничения

- Обычные Create-контрапшены нельзя менять после сборки, поэтому там только проверка
  при сборке.
- На Sable-корабле перехватывается `BlockItem.place`: рука, `create:deployer` и другие
  пути, идущие через `ItemStack#useOn`. Машины, которые ставят блоки напрямую через
  `Level#setBlock` в обход предмета, не отклоняются поштучным лимитом и `forbiddenBlocks`
  (но счётчик таких изменений всё равно обновляется).
- Блоки, занимающие несколько позиций (двери и т.п.), при клике проверяются по основной
  позиции; вторая половина учитывается инкрементально.
- Для отмены сборки Sable нужен Simulated (входит в Create: Aeronautics).
- `limit.size` при сборке используется только при `assemblyUsesPlayerLimit: true`.
  По умолчанию сборка ограничена `defaultMaxBlocks`; это сознательный выбор,
  чтобы не привязывать корабль к владельцу.

## Тестирование

Скрипт и результаты headless-проверки — в `docs/TESTING.md`.
In-game приёмка (клиент) остаётся за вами: сборка корабля, Deployer, LuckPerms-мета.

## Структура

```
src/main/java/com/middragonfly/sablelimits/
  SableLimits.java              — точка входа, события
  SableLimitsConfig.java        — JSON-конфиг и сообщения
  config/ConfigManager.java     — загрузка/перезагрузка
  limits/RuleSet.java           — разбор правил, теги, группы
  limits/StructureCounts.java   — счётчики структуры
  limits/Counting.java          — первичное сканирование
  limits/LimitManager.java      — кэш, проверки, TTL, сборка
  storage/LimitsSavedData.java  — персистентность
  perms/Permissions.java        — PermissionAPI/LuckPerms
  notify/Notifier.java          — сообщения
  command/SableLimitsCommand.java
  mixin/                        — точка входа в ванильные/Create/Sable методы
```

## Лицензия

MIT.
