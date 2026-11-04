# Observatório Espacial 🛰️

**Versão 1.0.0** — aplicativo Android de exploração espacial.

## Compatibilidade
- Android 8.0 (API 26) ou superior.
- ABI nativas Android NDK: `armeabi-v7a` (ARM 32 bits), `arm64-v8a` (ARM 64 bits), `x86` (Intel 32 bits) e `x86_64` (Intel 64 bits).
- OpenGL ES 2.0 como perfil gráfico mínimo. Dispositivos com OpenGL ES 3.x normalmente oferecem contexto ES 2.0 por compatibilidade retroativa.
- Kotlin, Android SDK, CMake 3.22.1 e JDK 17 para compilação.

## Motor 3D — primeira etapa
- Cena renderizada com OpenGL ES 2.0.
- Malha esférica 3D para o Sol e cinco planetas ilustrativos.
- Iluminação difusa, profundidade, órbitas e campo de estrelas.
- Câmera com rotação por arraste e zoom por pinça.
- Geometria das órbitas armazenada em cache para reduzir alocações por quadro.
- Permissões de localização aproximada e precisa; latitude, longitude e precisão reportada são exibidas quando disponíveis.

## Arquitetura nativa e desempenho
- Kotlin mantém a interface, ciclo de vida, permissões e sensores/localização.
- C++ via Android NDK/JNI executa o cálculo de posições orbitais e consulta o identificador do renderer gráfico quando o contexto OpenGL está ativo.
- C fornece a rotina matemática de órbitas chamada pelo módulo C++.
- OpenGL ES 2.0 envia os comandos gráficos ao driver da GPU; o driver e o Android decidem a execução na GPU disponível.
- O pacote nativo é compilado para as quatro ABIs oficiais listadas acima. A disponibilidade real depende do processador e da versão Android do aparelho.
- A implementação gráfica usa a API GLES 2.0; não significa que implemente todos os recursos específicos de ES 1.x, ES 3.x ou extensões de cada fabricante. ES 1.x não é suportado como caminho gráfico. Dispositivos sem suporte a OpenGL ES 2.0 não são compatíveis com a renderização 3D atual.
- O Android/driver pode impor limites de memória, temperatura, frequência e recursos gráficos; o app deve reduzir qualidade e carga quando necessário.
- C# não foi incorporado nesta etapa: este app é Android nativo Gradle/Kotlin. Adicionar .NET para Android só para misturar linguagens aumentaria o tamanho e o consumo sem acelerar automaticamente a GPU. Pode ser avaliado futuramente como módulo separado se houver uma função concreta que justifique o runtime .NET.

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
