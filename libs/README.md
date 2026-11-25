# libs

Сюда кладутся jar-файлы для компиляции (не коммитятся):

- `sable-neoforge-1.21.1-2.0.6.jar` — из `mods` сервера;
- `create-1.21.1-6.0.10.jar` — из `mods` сервера;
- `sable-companion-common-1.21.1-1.6.0.jar` — из `META-INF/jarjar` Sable;
- `veil-neoforge-1.21.1-4.3.2.jar` — из `META-INF/jarjar` Sable;
- `simulated-neoforge-1.21.1-1.3.2.jar` — из `META-INF/jarjar` bundle
  `create-aeronautics-bundled-...jar`.

Автоматически:

```powershell
powershell -ExecutionPolicy Bypass -File tools\prepare-libs.ps1 -ServerDir C:\path\to\server
```
