# Observatório Espacial 🛰️

**Versão 1.0.0** — aplicativo Android de exploração espacial.

## Compatibilidade
- Android 8.0 (API 26) ou superior.
- OpenGL ES 2.0 para renderização 3D.
- Kotlin, Android SDK e JDK 17 para compilação.

## Motor 3D — primeira etapa
- Cena renderizada com OpenGL ES 2.0 (não é mais o desenho 2D anterior).
- Malha esférica 3D para o Sol e cinco planetas ilustrativos.
- Iluminação difusa, profundidade, órbitas e campo de estrelas.
- Câmera com rotação por arraste e zoom por pinça.
- Geometria das órbitas armazenada em cache para reduzir alocações por quadro.
- Permissões de localização aproximada e precisa; latitude, longitude e precisão reportada são exibidas quando disponíveis.

## Próximas etapas
1. Aplicar texturas planetárias de alta resolução e mapas de relevo quando houver dados adequados.
2. Melhorar os modelos físicos: escalas, inclinações, rotação, luas e anéis.
3. Adicionar catálogo astronômico com fontes, datas e horários de referência.
4. Integrar serviços de dados astronômicos e cache offline, identificando claramente atrasos e limitações de cada fonte.

## Precisão e limites
A cena 3D atual é uma primeira base visual e educativa. As cores dos planetas são materiais ilustrativos, não texturas fotográficas reais; ainda não há relevo superficial nem dados astronômicos em tempo real. Nenhum aplicativo consegue mostrar literalmente todo o Universo em transmissão ao vivo: observações chegam com atrasos, coberturas e resoluções diferentes.

A localização é processada no aparelho e exibida na tela; esta versão não envia coordenadas a servidores.

## Compilação
O workflow do GitHub Actions executa `gradle assembleDebug` com JDK 17 e Gradle 8.9 a cada push na branch `main`. Quando a compilação terminar, o APK de depuração será publicado como artefato `observatorio-espacial-debug` na execução do workflow.
