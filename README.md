<h1 align="center">SII Rol Consulta & TGR Descargas — Android App</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kotlin-0095D5?&style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Min_SDK-24-blue?style=for-the-badge"/>
</p>

<p align="center">
  Una aplicación Android nativa diseñada para consultar ágilmente el <strong>ROL de Avalúo (Catastral)</strong> de una propiedad en Chile utilizando la dirección, y que automatiza la conexión con la <strong>Tesorería General de la República (TGR)</strong> para descargar el <strong>Certificado de Deudas de Contribuciones</strong> en formato PDF directamente en tu dispositivo.
</p>

<hr/>

## ✨ Características Principales

- 🔍 **Búsqueda Precisa por Dirección**: Encuentra el ROL de Avalúo introduciendo la comuna, nombre de la calle y numeración, conectando con el mapa del Servicio de Impuestos Internos (SII).
- 📄 **Descarga Automática de Certificados**: Genera y descarga el *Certificado de Deuda de Contribuciones* desde el portal de la TGR en un solo paso hacia el almacenamiento seguro del teléfono y sin salir de la app.
- 🚀 **100% Nativo y Limpio**: Construido únicamente en Kotlin utilizando componentes nativos (Material View, Corrutinas, y API HTTP estándar) sin depender de librerías de terceros pesadas como Retrofit o Glide.
- 📱 **Soporte Moderno**: Compatible desde Android 7.0 (Nougat) hasta las últimas versiones (Android 14+), haciendo uso de la API `MediaStore` para descargas seguras sin solicitar permisos intrusivos.

## 🛠️ Stack Tecnológico

- **Lenguaje**: [Kotlin 1.9+](https://kotlinlang.org/)
- **UI Toolkit**: XML Views + Material Components for Android
- **Async/Threading**: Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Main`)
- **Networking**: Peticiones HTTP Nativas (`HttpURLConnection` + Session Cookies Management)
- **File System**: `MediaStore` API (Android 10+) y `FileProvider` (Soporte Legacy)

## 📸 Capturas de Pantalla

*(Añade aquí las capturas de pantalla de la aplicación funcionando. Por ejemplo: una imagen de la consulta, una del resultado y una del PDF abierto)*
<p align="center">
  <img src="https://via.placeholder.com/250x500.png?text=Pantalla+Buscador" width="200"/>
  &nbsp;&nbsp;&nbsp;
  <img src="https://via.placeholder.com/250x500.png?text=Resultados+y+ROL" width="200"/>
  &nbsp;&nbsp;&nbsp;
  <img src="https://via.placeholder.com/250x500.png?text=Descarga+TGR" width="200"/>
</p>

## ⚙️ Cómo ejecutar el proyecto en local

Sigue estos pasos para compilar y ejecutar el proyecto en tu máquina:

1. Clona el repositorio:
  ```bash
  git clone https://github.com/TU_USUARIO/SIIRol-Android.git
  ```
2. Inicia **Android Studio** (se recomienda la versión Hedgehog o superior).
3. Selecciona `Open an Existing Project` y navega hasta la carpeta descargada.
4. Espera a que la **sincronización de Gradle** termine con éxito.
5. Conecta un dispositivo físico por USB o inicia el emulador integrado.
6. Pulsa sobre el botón verde ▶ **Run `app`** en la barra superior.

> **💡 Nota Técnica:**
> La consulta del SII emplea el endpoint público `getPrediosDireccion` que retorna datos catastrales generales. Si tu búsqueda no devuelve valor, prueba utilizando la calle sin tildes y todo en minúsculas. 

## ⚖️ Aviso Legal / Disclaimer

Esta aplicación fue desarrollada con fines educativos. Consume servicios y endpoints web públicos tanto del **Servicio de Impuestos Internos (SII)** como de la **Tesorería General de la República (TGR)** del Gobierno de Chile.
- Los datos mostrados son de carácter público por naturaleza catastral y tributaria en Chile.
- El autor principal de este repositorio no tiene vinculación gubernamental ni se hace responsable del cambio de estructura en los APIs u origen de datos que pudieran dejar algunas características temporalmente inoperantes.

---
⭐ Si el proyecto te ha servido o interesado, no dudes en dejarle una estrella en GitHub.
