# Observatório Espacial 🛰️

**Versão 1.1.0** — aplicativo Android de exploração espacial.

## Compatibilidade
- Android 8.0 (API 26) ou superior.
- ABIs Android NDK: `armeabi-v7a` (ARM 32 bits), `arm64-v8a` (ARM 64 bits), `x86` (Intel 32 bits) e `x86_64` (Intel 64 bits).
- OpenGL ES 2.0 como perfil gráfico mínimo. Em aparelhos com drivers ES 3.x, o aplicativo usa um contexto ES 2.0 compatível.
- Kotlin 2.2.20, Android SDK, CMake 3.22.1 e JDK 17 para compilação.

## Motor 3D nativo
- O desenho da cena 3D foi transferido para C++ no Android NDK.
- C++ cria malhas de esferas, buffers de vértices/índices, campo de estrelas e órbitas; envia comandos OpenGL ES 2.0 ao driver gráfico.
- Shaders de vértice e fragmento, profundidade, iluminação difusa, órbitas e planetas.
- Camada visual da Via Láctea gerada proceduralmente em C++: faixa galáctica de partículas com densidade concentrada e qualidade ajustada ao perfil gráfico.
- Campo estelar e camada galáctica são efeitos visuais procedurais; ainda não representam um catálogo astronômico medido nem a posição real do céu para data/local.
- Posições dos oito planetas consultadas no JPL Horizons (NASA/JPL), com vetores heliocêntricos em unidades astronômicas para uma janela de 360 dias.
- C++ interpola as amostras oficiais para animar os planetas e desenhar suas trajetórias previstas; a animação é acelerada e não representa uma transmissão ao vivo de telescópios.
- Imagens ilustrativas dos planetas são pesquisadas na NASA Image and Video Library; dependem de internet e da disponibilidade do catálogo.
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
A cena é uma base visual/educativa inspirada em planetários como o Stellarium. As posições orbitais dos oito planetas usam vetores do JPL Horizons, calculados numericamente para a janela consultada e animados por interpolação. A escala e o tamanho visual dos planetas são ajustados para a visualização, portanto não estão em escala física. A galeria usa imagens do NASA Image and Video Library. As imagens são registros de missões e observatórios, não vídeo ao vivo; a Via Láctea e o campo estelar ainda são procedurais e não um catálogo astrométrico. A orientação precisa do céu local, constelações científicas e texturas de superfície mapeadas aos modelos 3D ainda serão etapas futuras. Nenhum aplicativo consegue exibir literalmente todo o Universo em transmissão ao vivo: observações têm limites de cobertura, resolução e atraso.

A localização é processada no aparelho e exibida na tela; esta versão não envia coordenadas a servidores.

## C# e linguagens
C# ainda não foi incorporado. O aplicativo usa Kotlin para integração Android, C e C++ para cálculo/renderização nativa. Adicionar .NET para Android apenas para misturar linguagens aumentaria a complexidade e não aceleraria automaticamente a GPU.

## Compilação
O workflow do GitHub Actions executa `gradle assembleDebug` com JDK 17 e Gradle 8.9 a cada push na branch `main`. Quando a compilação terminar, o APK de depuração será publicado como artefato `observatorio-espacial-debug` na execução do workflow.


## Pesquisa de objetos celestes
- A interface permite pesquisar cometas, asteroides, planetas-anões e luas pelo NASA/JPL Horizons.
- Para corpos naturais, a consulta usa a localização GPS do aparelho e solicita coordenadas aparentes, azimute e elevação para o céu local, quando o objeto é reconhecido pelo Horizons.
- A opção de satélites artificiais consulta elementos GP/OMM do catálogo CelesTrak/NORAD e propaga as órbitas localmente com SGP4/SDP4 (WGS-72), usando a biblioteca OrbitCore (Apache-2.0).
- A tela apresenta azimute, elevação, distância inclinada, subponto terrestre, altitude orbital, velocidade, iluminação solar e previsão de passagens nas próximas 24 horas (elevação mínima de 10°).
- Um radar polar mostra a direção e a altura angular do satélite em relação ao observador GPS.
- A precisão depende da época e da qualidade dos elementos orbitais; dados antigos são sinalizados. TLE/OMM não é uma medição instantânea. A visualização topocêntrica de corpos naturais é calculada por efemérides e não é uma transmissão ao vivo.
- A pesquisa requer conexão com a internet. A localização é usada localmente para compor a consulta ao JPL.



## Modo Live e céu local
- O modo Live está integrado à tela principal. A cena 3D permanece animada e o botão alterna entre visão ampla do universo e exploração livre, sem um comando separado para acompanhar apenas um objeto.
- A visão global usa a cena 3D e as efemérides NASA/JPL quando disponíveis; não é uma transmissão de vídeo de todo o Universo.
- A bússola do céu usa o sensor de rotação do aparelho quando disponível. O GPS fornece latitude, longitude e altitude para cálculos topocêntricos.
- Na pesquisa de corpos naturais, o JPL Horizons consulta uma janela de 24 horas com amostras de uma hora. A interface mostra um rótulo azul com o limite previsto de visibilidade e um rótulo cinza com o nome e o início da janela prevista. Os horários são UTC e a previsão é aproximada.
- A previsão depende do horizonte geométrico e não considera nuvens, poluição luminosa, prédios ou árvores.
- Satélites artificiais usam elementos GP/OMM do CelesTrak e propagação SGP4/SDP4 local por meio da biblioteca OrbitCore.
- O indicador D2D/NTN observa se o Android 15 (API 35+) informa uma rede com transporte satelital ativa. Isso detecta a conectividade do próprio telefone, não todos os satélites no céu, e depende de modem, firmware, operadora e serviço compatíveis.
- A versão permanece 1.1.0 (versionCode 2).


## Catálogo local do céu (inspirado em planetários)
- Pesquisa local de estrelas brilhantes e objetos de céu profundo, incluindo objetos Messier como M31, M42, M45, M13, M8, M20, M57 e M51.
- Cada resultado apresenta identificação, tipo, constelação, magnitude visual aproximada e coordenadas equatoriais J2000.
- Quando há localização disponível, o app calcula azimute e elevação aproximados para a hora atual usando tempo sideral local.
- O rótulo de visibilidade indica somente se o objeto está acima do horizonte geométrico; não é uma garantia de observação a olho nu.
- O catálogo é uma seleção inicial própria, não uma cópia do banco de dados do Stellarium e não pretende cobrir todos os objetos.
- O APK enviado foi inspecionado como referência de estrutura e recursos. Seus bancos de dados, imagens e demais arquivos não foram incorporados ao projeto sem verificação independente de licença e origem.

## Ferramentas de compilação
- Android Gradle Plugin 8.10.1, Gradle 8.11.1 e compileSdk 36, necessários para atender aos metadados da dependência OrbitCore 0.1.0.
- targetSdk permanece 35, minSdk permanece 26 e a versão do app continua 1.1.0 (versionCode 2).


## Campo estelar 3D e constelações
- O renderizador OpenGL ES 2.0 agora desenha um campo de estrelas em coordenadas esféricas, com estrelas brilhantes posicionadas por ascensão reta e declinação J2000 aproximadas.
- Cor e tamanho aparente variam por estrela; pontos usam brilho circular suave no shader.
- Linhas de referência conectam estrelas de Órion, Cruzeiro do Sul, Ursa Maior, Leão e Cisne.
- O fundo estelar mantém uma camada procedural de estrelas fracas e a Via Láctea; o catálogo brilhante fica sobreposto a esse fundo.
- A visualização é uma esfera celeste didática em 3D, não uma simulação de distâncias reais entre estrelas. As posições e linhas de constelações são aproximadas e não substituem efemérides astrométricas de precisão.
- A quantidade de estrelas e partículas continua adaptada ao perfil de memória/GPU do aparelho.
