# Тестирование SableLimits

## Окружение

- NeoForge 21.1.267, Minecraft 1.21.1, Java 21 (Adoptium 21.0.3).
- Sable 2.0.6, Create 6.0.10, Create: Aeronautics 1.3.2 (Simulated 1.3.2).
- Отдельный тестовый сервер NeoForge 1.21.1 с плоским миром; клиент не использовался.
- Мод собран `gradlew.bat build --offline`, установлен в `mods` тестового сервера
  вместе с временным harness-модом, который вызывает реальные методы Sable/Create.

## Что проверено автоматически

| Проверка | Результат |
|---|---|
| Загрузка мода, отсутствие Mixin-ошибок | PASS |
| Генерация `config/sablelimits.json` при первом старте | PASS |
| Сборка правил после загрузки датапаков: Sable=4, Create=2, без предупреждений | PASS |
| `/sablelimits reload` и `/sablelimits status` | PASS |
| Первичный подсчёт после сборки: 3 блока, 3 хоппера | PASS |
| Установка блока сверх лимита (BlockItem.place) → FAIL | PASS |
| Разрушение блока: total 3 → 2 | PASS |
| Установка после освобождения лимита → SUCCESS, total 3 | PASS |
| Установка сверх общего размера (`defaultMaxBlocks=3`) → FAIL | PASS |
| Отмена сборки Sable по лимиту блока (2 slime при лимите 1) | PASS |
| Блоки после отменённой сборки остаются в мире | PASS |
| Отмена сборки Sable по размеру (2 блока при `defaultMaxBlocks=1`) | PASS |
| Отмена сборки Create Contraption по правилу `C` | PASS |
| Правило `C` не действует на Sable | PASS |
| Правило без префикса не действует на Create | PASS |
| Групповой лимит (hopper + slime, лимит 1) → FAIL | PASS |
| `forbiddenBlocks`: установка хоппера на Sable-корабль → FAIL | PASS |
| `forbiddenBlocks`: `C`-префикс не мешает Sable | PASS |
| `forbiddenBlocks`: сборка Sable с запрещённым slime отменяется | PASS |
| `forbiddenBlocks`: сборка Create с `C`-запретом отменяется | PASS |
| `forbiddenBlocks`: правило без префикса не мешает Create | PASS |
| `forbiddenBlocks`: бедрок (`* minecraft:bedrock`) не ставится на Sable-корабль | PASS |
| Автообновление старого конфига: добавлены `forbiddenBlocks` с бедроком, `configVersion`, `assemblyUsesPlayerLimit`, `messages.forbiddenBlock` | PASS |
| Кэш переживает рестарт: 3 записи загружены из `world/data/sablelimits.dat` | PASS |
| Выгрузка структуры (`UNLOADED`) не удаляет кэш | PASS |
| Конфиг и сообщения в UTF-8 | PASS |

Фактический вывод harness-команды (сокращённо):

```
assembled=true; seed total=3 hopper=3;
place over block limit=FAIL;
after break total=2;
place after break=CONSUME total=3;
place over size limit=FAIL;
assembly cancelled=true blocksStillThere=true;
assemblySize cancelled=true;
create cancelled=true still=true;
create unaffectedBySableRule=true;
sable unaffectedByCreateRule=CONSUME;
group denied=FAIL;
forbiddenPlace=FAIL;
sableUnaffectedByCreateForbidden=CONSUME;
forbiddenAssembly=true;
createForbidden=true;
createUnaffectedBySableForbidden=true;
bedrockPlace=FAIL;
cache entries=5
```

После `stop` / запуска сервера:

```
[Server thread/INFO] [SableLimits/]: Кэш SableLimits загружен: 3 структур
```

## Что нужно проверить в игре (клиент)

Эти сценарии требуют реального игрока, поэтому выполняются вручную:

1. **Сборка корабля**: собрать структуру с N+1 лимитируемым блоком — сборка
   отменяется, блоки остаются на месте, сообщение в чате.
2. **Установка рукой на готовый корабль**: N блоков ставятся, N+1 — блокируется,
   предмет остаётся в инвентаре, сообщение в ActionBar.
3. **Deployer**: N+1-й блок не ставится, предмет остаётся в активаторе, консоль не спамит.
4. **Освобождение лимита**: сломать один блок — снова можно поставить один.
5. **Групповой лимит**: 10 + 6 из группы проходят, 17-й блок блокируется.
6. **`forbiddenBlocks`**: блок из списка нельзя поставить на борт и нельзя собрать
   с ним корабль/контрапшен; `C`-правило не мешает Sable, правило без префикса — Create.
   По умолчанию проверяется `* minecraft:bedrock`; старый конфиг дополняется разделом
   автоматически при старте/`reload`.
7. **`C`-правила**: подшипник/вагонетка с превышением отменяются; корабль Sable
   этим правилом не затрагивается.
8. **`*`-правила**: действуют на оба типа.
9. **`bypass.blocks`**: игрок ставит сверх поштучного лимита, но упирается в размер.
10. **`bypass.size`**: игрок собирает больше общего капа, поштучные лимиты действуют.
11. **`limit.size`**: `/lp group sponsor meta set sablelimits.limit.size 6000`;
    спонсор достраивает до 6000, обычный игрок — до `defaultMaxBlocks`.
    Для сборки спонсором больше `defaultMaxBlocks` включить
    `"assemblyUsesPlayerLimit": true`.
12. **`/sablelimits reload`**: изменить число в конфиге, проверить применение без рестарта.
13. **Рестарт/выгрузка чанков**: после рестарта счётчики не сбрасываются; нельзя
    «доставить» ещё 64 блока после перезапуска.
14. **Обычный мир**: установка любых блоков вне конструкций не блокируется,
    TPS/MSPT не проседает.

## Замечания

- Тестовый harness лежит в `tools/headless-test/` и не входит в релизный jar.
- Логи сервера на Windows могут отображать русский текст как mojibake из-за
  кодировки консоли; сам JSON-конфиг и сообщения в игре — UTF-8.
