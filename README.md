# Minimal Car Launcher (edición juanmonge10)

Launcher Android minimalista, estilo CarPlay, para radios de coche Android en horizontal (pensado para 1024×600 y adaptable a otras resoluciones). Basado en [Breakeridis/Minimal-Car-Launcher](https://github.com/Breakeridis/Minimal-Car-Launcher).

## Pantalla principal

- **Barra lateral:** hora y fecha, cambio de tema claro/oscuro, *Acerca de* con buscador de actualizaciones (aparece un punto cuando hay una nueva), *Ajustes* (si lo mantienes pulsado abre el diagnóstico) y el botón **Todas las apps**.
- **Cuadrícula de iconos:**
  - **CarPlay**: abre ZLINK; si no lo encuentra, prueba los paquetes conocidos de ZLINK.
  - **Mapas**, **Música** y **Dashcam**: si los mantienes pulsados, eliges qué app abre cada uno.
  - Hasta 6 **favoritos**. Si mantienes pulsado uno, puedes quitarlo o reemplazarlo. Con **Añadir** fijas uno nuevo.
- **Barra de radio:** muestra la emisora actual y tiene botones de anterior/siguiente y 4 presintonías (al tocar una, sintoniza; si la mantienes pulsada, guarda ahí la emisora actual). Al tocar la emisora se abre la radio del equipo; si la mantienes pulsada, ves el diagnóstico.

## Cajón de apps

Búsqueda instantánea y cuadrícula que se adapta al ancho de la pantalla. Si mantienes pulsada una app, puedes fijarla en favoritos.

## Compilar (GitHub Actions)

- Cada push a `main` compila `app-debug.apk` y publica una Release `v1.0.<n>`.
- Para compilar una rama sin publicar Release, usa *Actions → Build Car Launcher APK → Run workflow*.
- El APK de depuración se instala con el ID `com.juanmonge.carlauncher.debug`.

## Instalar en la radio

1. Descarga `app-debug.apk` desde [Releases](https://github.com/juanmonge10/Minimal-Car-Launcher/releases).
2. Instálalo desde el gestor de archivos de la radio.
3. Pulsa **Inicio**, elige **Car Launcher** y marca **Siempre**.
4. Las siguientes versiones se instalan desde **Acerca de → Buscar actualizaciones**.

La arquitectura está documentada en [docs/ANALISIS_ARQUITECTURA.md](docs/ANALISIS_ARQUITECTURA.md).
