# Observatório Espacial 🛰️

**Versão 1.0.0** — aplicativo Android de exploração espacial.

## Compatibilidade
- Android 8.0 (API 26) ou superior.
- ABIs Android NDK: `armeabi-v7a` (ARM 32 bits), `arm64-v8a` (ARM 64 bits), `x86` (Intel 32 bits) e `x86_64` (Intel 64 bits).
- OpenGL ES 2.0 como perfil gráfico mínimo. Em aparelhos com drivers ES 3.x, o aplicativo usa um contexto ES 2.0 compatível.
- Kotlin, Android SDK, CMake 3.22.1 e JDK 17 para compilação.

## Motor 3D nativo
- O desenho da cena 3D foi transferido para C++ no Android NDK.
- C++ cria malhas de esferas, buffers de vértices/índices, campo de estrelas e órbitas; envia comandos OpenGL ES 2.0 ao driver gráfico.
- Shaders de vértice e fragmento, profundidade, iluminação difusa, órbitas e planetas.
- C calcula as posições orbitais demonstrativas; C++ integra esse cálculo e executa a renderização.
- Kotlin fica responsável pela interface Android, ciclo de vida, permissões, localização e gestos de câmera.
- Arraste para girar a câmera e pinça para zoom.

## Detecção e qualidade adaptativa
- No contexto OpenGL ativo, o app consulta fabricante/renderer da GPU, versão OpenGL ES, tamanho máximo de textura e número máximo de atributos de vértice.
- Consulta a classe de memória disponível para o app e o número de núcleos lógicos reportados pela CPU.
- Escolhe automaticamente um perfil econômico, médio ou alto para densidade da malha e quantidade de estrelas.
- A qualidade não força o hardware: limites de memória, temperatura, consumo, driver e recursos da GPU continuam sob controle do Android e do fabricante.
- O perfil é uma heurística inicial baseada em limites reportados, não um benchmark completo nem uma garantia de FPS. O aparelho pode ter desempenho inferior devido a throttling térmico, drivers ou outras aplicações.

## CPU e GPU: o que realmente acontece
- O código de cena, geração de geometria, cálculo orbital e preparação dos comandos roda em C++ na CPU.
- As chamadas OpenGL ES são encaminhadas pelo driver Android à GPU, que executa o trabalho gráfico compatível com o hardware.
- C++ não acessa diretamente registradores ou controla livremente clocks/voltagens da CPU/GPU; isso é gerenciado pelo sistema operacional e pelo driver.
- A implementação atual usa OpenGL ES 2.0. Ela não ativa automaticamente recursos exclusivos de ES 3.0/3.1/3.2, nem implementa ES 1.x. Um aparelho ES 3.x pode executar este caminho ES 2.0 por compatibilidade, mas não aproveita ainda todos os recursos modernos.
- Dispositivos sem suporte a OpenGL ES 2.0 não são compatíveis com a renderização 3D atual.

## Limites e precisão
A cena é uma base visual/educativa. Os materiais e cores dos planetas são ilustrativos, não texturas fotográficas; ainda não há mapas reais de superfície, relevo ou dados astronômicos em tempo real. As posições orbitais atuais são demonstrativas, não efemérides científicas. Nenhum aplicativo consegue exibir literalmente todo o Universo em transmissão ao vivo: observações têm limites de cobertura, resolução e atraso.

A localização é processada no aparelho e exibida na tela; esta versão não envia coordenadas a servidores.

## C# e linguagens
C# ainda não foi incorporado. O aplicativo usa Kotlin para integração Android, C e C++ para cálculo/renderização nativa. Adicionar .NET para Android apenas para misturar linguagens aumentaria a complexidade e não aceleraria automaticamente a GPU.

## Compilação
O workflow do GitHub Actions executa `gradle assembleDebug` com JDK 17 e Gradle 8.9 a cada push na branch `main`. Quando a compilação terminar, o APK de depuração será publicado como artefato `observatorio-espacial-debug` na execução do workflow.
