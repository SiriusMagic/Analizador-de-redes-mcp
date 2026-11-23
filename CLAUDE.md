# Redes (analizador de redes MCP): reglas para agentes

- Lee `ARQUITECTURA.md` (y el contrato común `FAMILIA.md` del repo de Inicio) y abre solo los archivos que necesites.
- Pruebas: `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` (debe pasar todo).
  Nunca desactives ni saltes una prueba. Lint sin avisos nuevos. Las pruebas no usan internet (`FakeServer`).
- Cada versión: sube `versionCode` y `versionName`, copia el APK a `android/Redes-<versión>.apk` (borra el anterior)
  y anótalo en `CHANGELOG.md`.
- Reglas puras (canales, bandas, señal, seguridad, ocupación y consejo de canal, matemáticas de velocidad, para qué sirve,
  la prueba con `Transport`) en `data/`, sin Android y con pruebas. Las pantallas solo pintan y avisan.
- Internet (excepción aceptada por el dueño para esta app): solo `Cloudflare` y solo a `speed.cloudflare.com`, con datos
  de relleno. Nunca una dirección que mande la IA. Una sola prueba a la vez (`Runner`, compartido con MCP).
- Privacidad: los permisos de ubicación y dispositivos cercanos son solo para nombres de redes; nunca se lee la posición.
  Las pruebas viven solo en el teléfono (fuera de copias). Por MCP no salen los BSSID de las redes vecinas.
- Cada herramienta MCP nueva lleva su prueba en `McpTest`: lista de herramientas, llamada correcta, argumentos malos y
  «no encontrado». Las que cambian algo o gastan datos exigen token de lectura y escritura (`READ_ONLY` si no).
- Prohibido: `eval`, motores de scripts, SQL concatenado.
- Firma: la de depuración del entorno, igual que el resto de la familia y Gateway Hub.
