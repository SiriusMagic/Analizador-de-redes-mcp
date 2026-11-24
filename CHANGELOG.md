# Redes 0.2.2

- **Conectar todas** (Gateway Hub 0.0.21): con un toque en el Hub se activa aquí «Redireccionar a Hub» y la app queda
  vinculada, sin entrar en ella. Solo lo puede pedir una app firmada por el mismo desarrollador.
- **Lucía** (la voz del Hub) usa las herramientas de esta app en el teléfono, **sin internet**, con las mismas reglas que
  por MCP. Solo mientras «Redireccionar a Hub» esté activado.
- **Icono de la app**: 5 iconos para elegir (Original, Claro, Dorado, Azul y Verde) en Ajustes › Icono de la app. Tras esta
  actualización quizá tengas que volver a poner el icono en tu pantalla de inicio (una sola vez).

# Redes 0.2.1

- **Consejo de canal con 40 y 80 MHz**: en 5 GHz compara bloques enteros (con 80 MHz, 36–48 y 149–161), contando cada red
  por todo su ancho. Ya no propone un canal dentro del mismo bloque (157 → 161 no cambiaba nada).

# Redes 0.2.0

- Wi‑Fi cercanas: nuevo **espectro** por banda (2,4 GHz · 5 GHz · 6 GHz, con cuántas redes hay en cada una). Cada red se
  dibuja con su ancho real de canal (20, 40, 80, 160 o 320 MHz) y su señal en dBm, con un color propio; la tuya, resaltada.
  Debajo, tu red y la lista de la banda elegida con canal y ancho («36 · 80 MHz»). Reemplaza el gráfico de barras.
- Red: **Factores de velocidad del Wi‑Fi** con nota (Bueno / Justo / Malo): espectro (banda y ancho del canal), potencial de
  radio (estándar, señal y máximo del enlace si Android lo da) y salud del canal, cada uno con ✓ o ⚠ y qué hacer.
- Red: **Factores de velocidad celular**: una fila por SIM activa con operador, 4G / 4G+ / 5G y señal; avisa si es débil.
- Modo MCP: `network_status` da `width_mhz`, `max_link_mbps` y `factors` (nota y qué limita); `network_wifi_nearby` da
  `width_mhz` de cada red.
- Todo sale de lo que Android ya sabe: sin servidores nuevos.
- 17 pruebas.

# Redes 0.1.0

Primera versión: analizador de redes en negro con acento verde.
- Red: tipo de conexión, señal, banda, canal, estándar, seguridad, IP, router, DNS; cada 2 s.
- Velocidad: latencia, jitter, descarga y subida contra Cloudflare con 3 conexiones; para qué sirve; historial; ahorro de
  datos en móvil.
- Wi‑Fi cercanas: lista, redes por canal y a qué canal cambiar el router.
- Modo MCP (`red_`, módulo `network_`): `network_status`, `network_wifi_nearby`, `network_speed_history`,
  `network_speed_result`, `network_speed_test`.
- Resumen «Tu conexión» para la pantalla «Hoy» de Inicio.
- 11 pruebas.
