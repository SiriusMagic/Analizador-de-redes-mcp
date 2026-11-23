# Arquitectura de Redes

Carpeta = package (`android/app/src/main/java/com/zirakusa/redes/`):

| Carpeta | Qué hay |
|---|---|
| `data/` | Lógica pura, con pruebas. `Radio` (`Band`, canal y banda desde la frecuencia, calidad y barras; `SignalLevel`, `Security`, `AccessPoint`, `Channels`: ocupación por canal con solapamiento en 2,4 GHz y `ChannelAdvice`); `Speed` (`Link`, `Use`, `Result`, `SpeedMath`: Mbps, mediana, jitter, rendimiento sin el arranque; `Grade`; `History`); `SpeedTest` (`Transport`, `Phase`, la prueba con varias descargas y subidas a la vez y límite de datos; `Runner`: una prueba a la vez y al historial; `Busy`); `Status` (`WifiNow`, `Status`, `Settings` con el ahorro de datos, `KeyValues`). |
| `ui/` | `RedesState` (único dueño del estado: conexión, redes cercanas, prueba en curso y resultados, ajustes) y `RedesApp` (pestañas Red · Velocidad · Wi‑Fi cercanas; `AppActions`). |
| `ui/screens/` | `NetworkScreen` (`Meter`), `SpeedScreen` (medidor en arco logarítmico, resultado, para qué sirve, historial), `NearbyScreen` (consejo, gráfico por canal, lista con `Bars`), `SettingsScreen`, `McpScreen`, `Parts`, `Chrome` y `SettingsParts`. |
| `ui/theme/` | `Theme`: `ThemeMode` y `Accent` (verde de fábrica). |
| `mcp/` | Modo MCP, como las apps hermanas: `Tokens` (`red_…`), `NetTools` (`network_status`, `network_wifi_nearby`, `network_speed_history`, `network_speed_result`; con escritura `network_speed_test`; a través de `NetData`), `McpHandler`, `RelayLink`, `McpMode` (`LiveData`) y `HubLink` (módulo `network_`). |
| `Phone.kt` | Lo que dice Android: `ConnectivityManager`, `WifiManager` (conexión y búsqueda de redes), `TelephonyManager` (4G/5G). |
| `Cloudflare.kt` | `Transport` real: ping, `__down` y `__up` de speed.cloudflare.com con OkHttp, por trozos de 64 KiB y cancelable. |
| `Tests.kt` | El único `Runner` de la app (pantalla y MCP). |
| `Summary.kt` | `SummaryLines` (pura) y `SummaryProvider`: «Tu conexión» para Inicio, sin pruebas ni internet. |
| `MainActivity.kt` | Crea el estado, lee la conexión cada 2 s mientras está abierta, escucha nuevas búsquedas, pide permisos e «Ir al Hub». |
