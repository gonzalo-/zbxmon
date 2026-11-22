# ZbxMon 📡

[English](README.md) · **Español** · [日本語](README.ja.md)

[![Build](https://github.com/gonzalo-/zbxmon/actions/workflows/build.yml/badge.svg)](https://github.com/gonzalo-/zbxmon/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

Un cliente Android pequeño y respetuoso con la privacidad para **Zabbix 7.x**.
Consultá el estado de los hosts, explorá los problemas por severidad, reconocelos y comentalos,
mirá los valores de los ítems y sus gráficos, y recibí notificaciones, **sin Google Play Services**
(funciona en Android estándar y en sistemas sin Google como GrapheneOS).

Hecho con Kotlin y Jetpack Compose (Material 3). Sin analíticas ni rastreadores.

## Características

- 🖥️ **Hosts** con emojis de estado: 🟢 OK · 🔥🔴🟠🟡🔵⚪ según la severidad del peor problema · 📵 interfaz caída · 🔧 mantenimiento · ⏸️ deshabilitado
- 🎛️ **Filtros persistentes** (se guardan solos): mostrar u ocultar hosts por estado (🟢 🚨 📵 🔧 ⏸️ con contadores), severidad del problema, solo no reconocidos
- 🏷️ Filtro por **grupo de hosts o etiqueta**, ↕️ **orden** (severidad / nombre / más reciente) y ⭐ **favoritos** fijados arriba
- 🚨 **Problemas** con reconocer, quitar el reconocimiento y **comentar**, con historial; 📤 **compartir** un problema como texto
- 🕘 **Historial** de los problemas resueltos en los últimos 7 días
- 📊 **Ítems** con últimos valores legibles y 📈 **gráficos** (1 h, 6 h, 24 h, 7 d)
- 🔔 **Notificaciones** de problemas nuevos (y opcionalmente de resueltos) con severidad mínima configurable,
  una notificación agrupada opcional, 🌙 **horario silencioso** y 😴 **posponer** / 🔕 **silenciar** por host o problema
- 🧩 **Widget de pantalla de inicio** con el último resumen
- 🗂️ **Varios servidores** (perfiles), cada uno con su propio modo de conexión
- 🎨 Dos estilos (✨ Minimal / 🖥️ Terminal), 📏 densidad compacta o cómoda, intervalo de actualización configurable
- 🌐 Idioma de la interfaz: inglés (por defecto), español o el del sistema

## Instalación

### Desde GitHub Releases (recomendado)

1. Abrí la página de [Releases](https://github.com/gonzalo-/zbxmon/releases) y descargá `zbxmon-<versión>.apk`
   y su `zbxmon-<versión>.apk.sha256` de la última versión.
2. Verificá la descarga (dejá los dos archivos en la misma carpeta):

   ```bash
   sha256sum -c zbxmon-1.0.0.apk.sha256      # debería imprimir: zbxmon-1.0.0.apk: OK
   ```

3. Instalalo en el teléfono (Android 8.0+, API 26). Hay dos opciones:
   - **En el teléfono:** abrí el APK desde el administrador de archivos o las descargas del navegador. Android te pide permitir
     la instalación de apps desde esa fuente (*Instalar apps desconocidas*): permitilo para la app con la que abriste el archivo y tocá *Instalar*.
   - **Desde una computadora** con la depuración USB activada: `adb install -r zbxmon-1.0.0.apk`
4. Actualizaciones: instalá el APK nuevo de Releases encima del anterior. Se conservan tus servidores y ajustes,
   siempre que ambos APK estén firmados con la misma clave (los oficiales siempre lo están).

El APK de Releases está firmado por el mantenedor. Un APK que compiles vos mismo se firma con otra clave,
así que no se puede instalar sobre el oficial (primero hay que desinstalar, lo que borra los datos de la app).

### Compilarlo vos mismo

Mirá [Compilar desde el código fuente](#compilar-desde-el-código-fuente) más abajo.

## Compilar desde el código fuente

Requisitos: **JDK 17** (funcionan de la 17 a la 21; Gradle 8.9 no corre en nada posterior a la 22),
el **Android SDK** (plataforma 35, build-tools 34.0.0) y conexión a internet en la primera compilación
(descarga Gradle 8.9 y las dependencias).

### Arch Linux (paso a paso)

```bash
# 1. Paquetes de los repositorios oficiales
#    (android-tools trae adb, solo hace falta para instalar el APK desde la computadora)
sudo pacman -S --needed git jdk17-openjdk unzip curl android-tools

# 2. Dejá JDK 17 como Java activo. El JDK por defecto de Arch es más nuevo de lo que soporta Gradle 8.9.
sudo archlinux-java set java-17-openjdk
java -version            # debería indicar 17.x

# 3. Herramientas de línea de comandos del Android SDK, desde Google
export ANDROID_HOME="$HOME/Android/Sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd /tmp
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
cd -

# 4. Entorno (agregá también estas dos líneas a ~/.bashrc o ~/.zshrc)
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

# 5. Aceptá las licencias e instalá los componentes del SDK (una sola vez)
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;34.0.0"

# 6. Bajá el código y compilá un APK de depuración
git clone https://github.com/gonzalo-/zbxmon.git
cd zbxmon
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# 7. Instalalo en un teléfono con la depuración USB activada (opcional)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Notas:

- Si la descarga del paso 3 devuelve 404, Google publicó una versión más nueva: copiá el enlace actual de
  "Command line tools only → Linux" desde <https://developer.android.com/studio#command-line-tools-only>.
- El paquete de AUR `android-sdk-cmdline-tools-latest` es una alternativa al paso 3 si preferís tu ayudante de AUR.
- El APK de depuración se firma automáticamente con la clave de depuración de Android, así que se instala enseguida.
  No hace falta ningún keystore para compilarlo.

### Otros sistemas

- **Android Studio** (cualquier SO): *File → Open* sobre la carpeta del proyecto, esperá la sincronización y después *Run* o *Build → Build APK(s)*.
- **macOS / otro Linux:** instalá JDK 17 y las herramientas de línea de comandos del SDK, definí `ANDROID_HOME`,
  ejecutá los comandos de `sdkmanager` del paso 5 y después `./gradlew assembleDebug`.
- **Windows:** usá Android Studio, o `gradlew.bat assembleDebug` después de definir `JAVA_HOME` y `ANDROID_HOME`.

### Solución de problemas

| Problema | Solución |
|----------|----------|
| `SDK location not found` | `export ANDROID_HOME="$HOME/Android/Sdk"` o `echo "sdk.dir=$HOME/Android/Sdk" > local.properties` |
| `licences have not been accepted` | `yes \| sdkmanager --licenses` |
| `Unsupported class file major version` / Gradle falla al arrancar | Usá JDK 17: `sudo archlinux-java set java-17-openjdk`, o `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk` |
| `./gradlew: Permission denied` | `chmod +x gradlew` |
| `Could not resolve ...` / timeouts | Revisá la conexión o el proxy; la primera compilación necesita descargar dependencias |
| `OutOfMemoryError` | Subí `org.gradle.jvmargs=-Xmx2048m` en `gradle.properties` |

### Compilación firmada para release

```bash
cp keystore.properties.example keystore.properties     # después editalo con los datos de tu keystore
./release.sh --no-tag                                    # → dist/zbxmon-<versión>.apk + .sha256
```

Sin `--no-tag`, el script exige un árbol de git limpio y crea la etiqueta local `v<versión>`.
Creá un keystore una sola vez con:

```bash
keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias zbxmon \
        -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=ZbxMon"
```

⚠️ Guardá bien tu keystore y **nunca lo subas al repositorio**. Una app instalada solo se puede actualizar con un APK firmado
con la misma clave, así que los APK que firmes vos no pueden actualizar la versión oficial (ni al revés).

La versión está en `gradle.properties` (`zbxmon.versionName`, `zbxmon.versionCode`).
Subí `versionCode` en cada release.

## GitHub Actions

| Workflow | Cuándo | Qué hace |
|----------|--------|----------|
| `.github/workflows/build.yml` | cada push a `main` y cada pull request | valida el Gradle wrapper, comprueba que las traducciones estén sincronizadas, compila un APK de depuración y lo sube como artefacto |
| `.github/workflows/release.yml` | cuando se sube una etiqueta como `v1.0.0` | compila un APK de release **firmado** y lo publica, junto con su `.sha256`, como un release de GitHub |

El APK de depuración de `build.yml` está en la página de la ejecución, en *Artifacts* (para descargarlo hace falta iniciar sesión en GitHub).
Es para pruebas, no para uso diario: usá el APK firmado de Releases.

`release.yml` necesita secretos de firma que solo tiene el dueño del repositorio (la lista está en el comentario al principio del archivo),
así que en un fork no sirve de nada. Los forks pueden compilar y firmar su propio APK de forma local (mirá más arriba).

## Configurar Zabbix

1. En Zabbix andá a **Users → API tokens → Create API token** (usá un usuario dedicado y con los mínimos privilegios).
2. En ZbxMon agregá un servidor con la URL del frontend (por ejemplo `https://zbx.example.com/zabbix`, sin `api_jsonrpc.php`) y el token.
3. Tocá **Probar conexión**.

Reconocer y comentar requiere permiso de escritura sobre los hosts afectados para el usuario del token.
La app se comunica con `<URL>/api_jsonrpc.php` usando `Authorization: Bearer <token>`.

### Modos de conexión (por servidor)

| Modo | Cómo |
|------|------|
| HTTPS válido | Por defecto. Validación normal de certificados del sistema. |
| HTTPS autofirmado | Activá *Certificado autofirmado*, tocá *Obtener huella del servidor*, verificala contra tu servidor y guardá. Se fija la huella SHA-256. |
| HTTP plano | Usá una URL `http://`. Solo para LAN/VPN: el token viaja sin cifrar. |

## Notificaciones sin servicios de Google

No hay dependencia de FCM ni de push. Hay dos mecanismos:

- **Chequeo periódico (WorkManager):** cada 15 / 30 / 60 minutos (el mínimo de Android es 15).
- **Servicio en primer plano (opcional):** consulta cada 30 s – 5 min y mantiene una notificación persistente con un resumen 🟢/🔴.
  Se reinicia después de reiniciar el teléfono.

En GrapheneOS y otros sistemas restrictivos, permití las notificaciones y poné el uso de batería de la app en *Sin restricciones*
si usás el servicio en primer plano (en Ajustes hay accesos directos para las dos cosas).
La primera consulta a un servidor solo registra el estado actual, así que los problemas existentes no te inundan de notificaciones.

Las posposiciones y los silencios son locales al teléfono: solo callan notificaciones y nunca cambian nada en Zabbix.
Los problemas que aparecen durante el horario silencioso se retienen y se notifican cuando termina
(los problemas Disaster igual pueden notificar si dejás esa opción activada).
El widget muestra el resultado del último chequeo en segundo plano, por lo que se actualiza con el chequeo periódico o con el servicio en primer plano.

## Idiomas

El inglés es el idioma por defecto y de respaldo. El español está incluido y se elige en **Ajustes → Idioma**.
Para agregar un idioma, mirá [CONTRIBUTING.md](CONTRIBUTING.md).

## Hacer tu propia versión

Hacé un fork del repositorio y después:

- cambiá `applicationId` / `namespace` en `app/build.gradle.kts` (y mové la carpeta de código `dev/zbxmon` y las líneas `package` para que coincidan)
  si querés publicarla como una app separada;
- creá tu propio keystore (mirá más arriba); para usar `release.yml` en tu fork, agregá los secretos que figuran en su encabezado;
- reemplazá la dirección de donaciones en `app/src/main/java/dev/zbxmon/Donate.kt` y en este README por la tuya (o quitala).

## Seguridad

Los tokens se cifran con una clave del Android Keystore y se excluyen de las copias de seguridad.
Mirá [SECURITY.md](SECURITY.md) para los detalles y para saber cómo reportar vulnerabilidades.

## Estructura del proyecto

```
app/src/main/java/dev/zbxmon/
  data/     modelos, almacenamiento, cifrado con Keystore, fijado TLS, cliente JSON-RPC de Zabbix
  notify/   notificaciones, worker de WorkManager, servicio en primer plano, receptor de arranque
  widget/   widget de pantalla de inicio
  ui/       pantallas Compose, tema, view model
scripts/    scripts auxiliares (chequeo de traducciones)
.github/    workflows de CI
```

## Apoyá el proyecto

ZbxMon es libre y de código abierto. Si te resulta útil, podés apoyar su desarrollo con Bitcoin.
La misma dirección está en la app, en **Ajustes → Apoyá el proyecto**.

₿ **Bitcoin (on-chain)**

```
bc1p9nq9peaf3qzvuptpavg7flacdh05pyzr2f8rnjuj6a623sk22aysspyssg
```

## Licencia

[MIT](LICENSE)
