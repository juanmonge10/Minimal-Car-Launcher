# Análisis de arquitectura — Minimal Car Launcher (commit `c4131ee`)

## Stack
- Kotlin 1.9.23, Jetpack Compose (BOM 2024.04.01, Material3), AGP 8.3.2, Gradle 8.5, JDK 17.
- `minSdk 26`, `targetSdk 34`. Una sola Activity (`MainActivity`) con todo en Compose.
- Dependencia pesada: `osmdroid` (solo la usa el mapa circular).
- No hay tests (`app/src/test` y `androidTest` no existen). No hay `gradlew` en el repo: CI usa Gradle 8.5 instalado por `gradle/actions/setup-gradle`.

## Flujo de arranque
1. `CarLauncherApp` instala un handler de excepciones que abre `CrashActivity`.
2. `MainActivity` (HOME + LAUNCHER, `singleTask`, `sensorLandscape`):
   - modo inmersivo, pide permisos de ubicación (velocímetro GPS),
   - `setContent { CarLauncherTheme(isDarkMode) { DashboardScreen(viewModel) } }`,
   - `onResume`: `loadApps()`, `refreshRadio()`, arranca GPS; `onPause` lo detiene,
   - `onBackPressed` sólo cierra el cajón (el launcher nunca se cierra).

## Pantalla principal — `ui/dashboard/DashboardScreen.kt`
Fila de 3 columnas:
| Columna | Componentes |
|---|---|
| Izquierda | `ClockWidget`, `RadioStationWidget`, `LeftBottomDock` (botón cajón + favoritos + “+”) |
| Centro | `CircularMapPortal` (osmdroid, ruta OSRM, búsqueda de direcciones, velocímetro) |
| Derecha | `QuickLaunchCards` (Proyección ZLink, Dashcam, Música, Navegación), `RightBottomDock` (Acerca de, Ajustes; long-press = Diagnóstico) |

Además monta, como overlays, todos los diálogos: cajón, acciones del dock, 5 `AppPickerDialog`, `DrawerActionDialog`, `AboutDialog`, `DiagnosticsDialog`, `AddressSearchDialog`.

## Estado — `ui/viewmodel/LauncherViewModel.kt`
Un único `AndroidViewModel` con `StateFlow`s: apps, favoritos, búsqueda, apps por defecto (ZLink/nav/música/DVR), tema, reloj (tick 1 s), radio, GPS, navegación, updater, estado de cada diálogo. Toda la lógica de lanzamiento está aquí y en el repositorio, **no en la UI** → el rediseño puede ser sólo de capa visual.

## Cajón de aplicaciones — `ui/drawer/AppDrawerDialog.kt`
- `Dialog` a pantalla completa, `OutlinedTextField` de búsqueda + botón cerrar, `LazyVerticalGrid` fijo de 6 columnas.
- Búsqueda: `filteredApps = combine(allApps, searchQuery)` filtrando por `label`.
- Tap → `viewModel.launchApp()` (cierra cajón). Long-press → `DrawerActionDialog` → `pinDrawerAppToDock()`.
- Icono: `BitmapHelper.safeDrawableToImageBitmap(icon, 96, 96)` (se recalcula en cada recomposición; candidato a cachear).

## Favoritos (dock)
- `AppRepository`: `SharedPreferences("car_launcher_dock_prefs")`, clave `key_pinned_dock_packages` (JSON de paquetes), máx. `MAX_DOCK_APPS = 6`.
- Operaciones: añadir (“+” o long-press en cajón), quitar/reemplazar (long-press en dock → `DockActionDialog` → `AppPickerDialog`).

## Apps instaladas — `data/AppRepository.kt`
- `queryIntentActivities(MAIN/LAUNCHER)` (requiere `QUERY_ALL_PACKAGES`), excluye el propio paquete, ordena por nombre.
- Clasifica cada app por heurística de paquete/etiqueta: `isZLink`, `isNavigation`, `isMusic`, `isDvr`.
- Lanzamiento: `ComponentName(pkg, activity)` con fallback a `getLaunchIntentForPackage`.

## ZLINK / CarPlay
- No existe integración por API ni intents especiales: “CarPlay” = **lanzar la app ZLINK**.
- Detección: lista `zlinkPackages` + coincidencias `zlink|phonemirror|speedplay|autokit` o etiqueta “zlink/android auto”.
- `LauncherViewModel.launchZLink()`: usa la app detectada; si no, prueba `com.zjinnova.zlink`, `com.zjinnova.zlinkx`, `com.xyauto.zlink`, `com.carletter.zlink`, `com.suding.speedplay`; si nada funciona abre el cajón.
- ⚠️ `com.autonavi.amapauto` (AMap) está en `zlinkPackages`, y la palabra “android auto” en la etiqueta también marca como ZLink. Se conserva tal cual; anotado como posible falso positivo.
- La conexión CarPlay real depende del dongle/ZLINK y **sólo puede validarse en la unidad**.

## Tema claro/oscuro — `ui/theme/`
- `CarColors` (data class) con `DarkCarColors` / `LightCarColors`, expuestos por `LocalCarColors` y accesores `@Composable` (`CarBg`, `TextPrimary`, …) que usa todo el código.
- Persistencia: `key_dark_mode` en el mismo `SharedPreferences` (por defecto claro).
- El selector **sólo está dentro de `AboutDialog`** (`ui/dialogs/UpdateDialog.kt`), no en la pantalla principal.

## Servicios
- `RadioManager` (1477 líneas) + `RadioBroadcastReceiver`: escucha decenas de broadcasts de radio de fabricantes (NWD, QF, Allwinner, Microntek…) y envía intents de sintonía. No usa root.
- `SpeedometerManager`: GPS → velocidad, rumbo, ubicación.
- `NavigationService`: rutas OSRM por HTTP (para el mapa circular).
- `UpdateManager`: consulta `api.github.com/repos/Breakeridis/Minimal-Car-Launcher/releases` y descarga/instala el APK (FileProvider + `REQUEST_INSTALL_PACKAGES`).
- `DiagnosticsManager`: log de eventos de radio/sistema.

## Manifiesto
- Permisos: INTERNET, ACCESS_NETWORK_STATE, REQUEST_INSTALL_PACKAGES, FINE/COARSE_LOCATION, QUERY_ALL_PACKAGES. Ninguno requiere root ni es de sistema.
- `RadioBroadcastReceiver` exportado con ~60 acciones de radio.
- `FileProvider` para el updater.

## Compilación / CI — `.github/workflows/build.yml`
- Push/PR a `main`/`master` → JDK 17 → `gradle assembleDebug -PbuildNumber=<run>` → sube artefacto → en `main` publica Release `v1.0.<run>` con el APK.
- `versionCode` = número de ejecución de CI. Firma con `keystore/debug.keystore` del repo (debug y release). Debug añade sufijo `.debug` al applicationId.
