# Headless test harness

Временный мод, который проверяет реальные пути Sable/Create без клиента:

- собирает структуру через `SubLevelAssemblyHelper.assembleBlocks`;
- ставит блок через настоящий `BlockItem.place` (срабатывает mixin);
- ломает блок и проверяет инкрементальный счётчик;
- проверяет общий лимит размера;
- отменяет сборку Sable через `SimAssemblyHelper.assembleFromSingleBlock`;
- отменяет сборку Create через `Contraption.searchMovedStructure`;
- проверяет изоляцию префиксов `C`/`*` и групповой лимит.

Команда в игре/консоли: `/slstest` (permission level 2).

## Сборка harness

Нужны собранный `build/libs/sablelimits-1.0.0.jar` и `libs/` (см. корневой README).

```powershell
$src = "$pwd\tools\headless-test"
$out = "$pwd\build\headless-test"
$libraries = "C:\моды\veritascreate\libraries"

$jars = Get-ChildItem $libraries -Recurse -Filter *.jar | Where-Object {
    $_.FullName -notmatch 'net\\minecraft\\server\\1\.21\.1\\' -and
    $_.Name -notmatch 'extra|slim|unpacked' -and
    $_.FullName -notmatch 'net\\minecraft\\client'
}
$cp = (($jars.FullName + (Get-ChildItem "$pwd\libs" -Filter *.jar).FullName +
        "$pwd\build\libs\sablelimits-1.0.0.jar") -join ';')

New-Item -ItemType Directory -Force $out | Out-Null
& "C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot\bin\javac.exe" `
    -proc:none -encoding UTF-8 -cp $cp -d $out "$src\src\com\middragonfly\sablelimitstest\TestMod.java"
Copy-Item "$src\resources\META-INF" $out -Recurse -Force
& "C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot\bin\jar.exe" --create `
    --file "$out\sablelimitstest.jar" -C $out .
```

Положить `sablelimitstest.jar` в `mods` тестового сервера рядом с `sablelimits`.
Harness не входит в релизный jar.
