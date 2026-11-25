# Redes 0.3.1

- **Iconos nuevos**: Lupa (el de fábrica), Neón, Burbuja y Píxel, en Ajustes › Icono de la app. Se quitan los anteriores;
  si tenías uno elegido, pasa solo al nuevo que ocupa su lugar (y quien tenía «Verde», a la Lupa).
- 32 pruebas.

# Redes 0.3.0

- Nueva pestaña **Señal**: el Wi‑Fi conectado con banda y ancho («5 GHz (80 MHz)»), canal, señal en dBm y velocidad PHY
  ↓/↑. Gráfico de la señal de los últimos 5 minutos (una lectura cada 2 s mientras Redes está abierta, solo en memoria).
- **Latencia** (en Señal): al router de tu red (un toque TCP corto, solo direcciones locales) y a Internet, medida solo
  contra speed.cloudflare.com; promedio, jitter y pérdida de paquetes, con gráfico de los últimos 3 minutos. No mide
  durante una prueba de velocidad; con datos móviles y ahorro de datos, Internet cada 10 s.
- **Roaming de punto de acceso**: registro de los cambios de punto de acceso de tu propia red (hora, canal y señal antes y
  después), guardado solo en el teléfono. Sin cambios: «Aún no se han registrado cambios en puntos de acceso».
- **Descubrir** (desde Red): pestaña Wi‑Fi con los dispositivos de tu red local (/24): «Portal» para el router, «Yo» para
  este teléfono y «Genérico» (o su nombre local) para el resto; contador y botón para ordenar. Pestaña Bluetooth con los
  dispositivos LE cercanos (nombre o «Genérico», «LE», dirección y dBm), solo mientras está abierta. Nada se guarda ni sale.
- **Velocidad**: diagrama del camino Internet (red del proveedor e IP pública si Cloudflare la dio en la última prueba, solo
  en memoria) → Punto de acceso → este teléfono (modelo e IP local).
- **Historial de prueba de velocidad**: cada prueba como tarjeta con fecha, tipo de conexión, barra azul de descarga y
  morada de subida a la misma escala, e «Internet → modelo | ms». Página completa con búsqueda.
- **Factores de velocidad celular**: el tipo de red (2G/3G/4G/4G+/5G) sale de una tabla pura con pruebas; el permiso de
  teléfono se pide aparte, explicando para qué, y si se niega se indica cómo darlo en Ajustes.
- Modo MCP: `network_signal` y `network_latency` (lectura) y `network_latency_test` (lectura y escritura). Nunca salen
  direcciones de hardware (BSSID ni Bluetooth) ni las listas de Descubrir.
- 31 pruebas.

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
