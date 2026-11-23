# Redes (analizador de redes MCP)

Analizador de redes para Android en negro con acento verde. Parte de la familia de Stone, Luma, Calculadora, Notas, Filem,
Brújula, Nivel, Reloj, Calendario, Inicio, QR y Clima: tu IA (Claude, ChatGPT…) puede **ver tu conexión y tus pruebas de
velocidad** por MCP.

## Estado (0.1.0)

- **Red:** cómo estás conectado ahora (Wi‑Fi, datos móviles 4G/5G, cable o VPN), si hay internet de verdad, la señal del
  Wi‑Fi en dBm con su calidad, banda y canal, velocidad del enlace, Wi‑Fi 4/5/6/7, seguridad (avisa si es antigua o
  abierta), IP local, router, DNS, DNS privado y si la red tiene límite de datos. Se actualiza cada 2 segundos.
- **Velocidad:** prueba como las conocidas (latencia y jitter con 10 pings, luego 3 descargas y 3 subidas a la vez durante
  8 s cada una) contra el servidor público de Cloudflare. El medidor sube mientras mide. Resultado con **para qué sirve**
  (navegar, música, video HD y 4K, videollamadas, juegos en línea, subir archivos grandes) y el historial (hasta 200).
  **Ahorro de datos:** con datos móviles usa como mucho 30 MB (si no, 120 MB).
- **Wi‑Fi cercanas:** todas las redes alrededor con señal, banda, canal y seguridad; cuántas hay en cada canal de 2,4 GHz;
  y **a qué canal cambiar tu router** para que haya menos interferencia.
- **Ajustes:** ahorro de datos, borrar pruebas, **Modo MCP**, Temas, privacidad y versión.
- **Modo MCP:** `network_status`, `network_wifi_nearby`, `network_speed_history`, `network_speed_result` y, con token de
  lectura y escritura, `network_speed_test` (con datos móviles solo si la IA pasa `allow_mobile`, después de preguntarte).
  Tokens `red_…`; módulo `network_` para Gateway Hub. Las direcciones físicas (BSSID) de las redes vecinas no salen.
- **Resumen para Inicio:** «Tu conexión» (la red y la señal, y la última prueba de la semana) en la pantalla «Hoy».
- Los permisos de ubicación y de dispositivos cercanos solo sirven para que Android muestre nombres de redes: Redes no
  lee ni guarda dónde estás. Sin anuncios ni estadísticas.

APK: [`android/Redes-0.1.0.apk`](android/Redes-0.1.0.apk) (`com.zirakusa.redes`, firmado como el resto de la familia y
Gateway Hub).

## Compilar y probar

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

## Siguiente

Ver [`ROADMAP.md`](ROADMAP.md).
