# app_obiservatório_espacial 🛰️

**Versão 1.0.0** — aplicativo Android de exploração espacial.

## Compatibilidade
- Android 8.0 (API 26) ou superior.
- Kotlin, Android SDK e JDK 17 para compilação.
- Permissões de localização aproximada e precisa solicitadas durante o uso.

## Recursos implementados nesta versão
- Cena espacial ilustrativa com estrelas, Sol, planetas e órbitas.
- Zoom por pinça e rotação por arraste.
- Leitura da localização do aparelho enquanto o app está em primeiro plano.
- Exibição de latitude, longitude e precisão reportada pelo Android.
- O app continua funcionando sem conceder localização.

## Sobre o Universo em tempo real
Esta versão ainda não contém um catálogo astronômico completo nem transmissão ao vivo da NASA. A cena é uma representação educativa, não um mapa 3D completo nem uma efeméride precisa. Versões futuras poderão integrar catálogos e serviços astronômicos, exibindo fonte e data de atualização.

A localização é usada apenas no aparelho para exibir a posição nesta versão. O código atual não envia coordenadas a servidores.

## Compilação
Abra o projeto no Android Studio com JDK 17, sincronize o Gradle e execute o módulo `app` em um aparelho Android 8.0 ou superior.
