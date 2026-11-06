# Observatório Espacial 🛰️

**Versão 1.2.5** — aplicativo Android de exploração espacial.

## Compatibilidade
- Android 8.0 (API 26) ou superior.
- ABIs Android NDK: `armeabi-v7a` (ARM 32 bits), `arm64-v8a` (ARM 64 bits), `x86` (Intel 32 bits) e `x86_64` (Intel 64 bits).
- OpenGL ES 1.0/1.1: renderizador alternativo GL10 com catálogo estelar e figuras das 88 constelações.
- OpenGL ES 2.0: renderizador nativo C++ com shaders e cena 3D; aparelhos ES 3.x usam o caminho ES 2.0 compatível.
- Kotlin 2.4.0, Android SDK, CMake 3.22.1 e JDK 17 para compilação.

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
- O código de cena, geração de geometria, cálculo orbital e preparação dos comandos roda em C++ na CPU. Em ES 1.x, a cena celeste usa a API fixa GL10; em ES 2.0 e superiores, usa shaders GLES2.
- As chamadas OpenGL ES são encaminhadas pelo driver Android à GPU, que executa o trabalho gráfico compatível com o hardware.
- C++ não acessa diretamente registradores ou controla livremente clocks/voltagens da CPU/GPU; isso é gerenciado pelo sistema operacional e pelo driver.
- A implementação oferece um caminho GL10 para OpenGL ES 1.0/1.1 e um caminho GLES2 para ES 2.0 ou superior. Em dispositivos ES 3.x, o app usa contexto ES 2.0 por compatibilidade; não ativa automaticamente recursos exclusivos de ES 3.0/3.1/3.2. A versão ES 1.x é uma cena celeste de compatibilidade, sem os planetas 3D avançados do caminho GLES2.
- Dispositivos ES 1.0/1.1 usam o renderizador GL10 de compatibilidade; aparelhos ES 2.0 ou superiores usam o renderizador nativo GLES2.

## Limites e precisão
A cena é uma base visual/educativa inspirada em planetários como o Stellarium. As posições orbitais dos oito planetas usam vetores do JPL Horizons, calculados numericamente para a janela consultada e animados por interpolação. A escala e o tamanho visual dos planetas são ajustados para a visualização, portanto não estão em escala física. A galeria usa imagens do NASA Image and Video Library. As imagens são registros de missões e observatórios, não vídeo ao vivo; a Via Láctea e o campo estelar ainda são procedurais e não um catálogo astrométrico. O catálogo integrado contém 725 estrelas e as 676 ligações estelares das 88 constelações ocidentais oficiais; os traços são figuras de referência do Stellarium associadas a posições do catálogo HYG/Hipparcos. A orientação astronômica ainda não é corrigida dinamicamente para precessão, nutação, refração ou horizonte topocêntrico. Nenhum aplicativo consegue exibir literalmente todo o Universo em transmissão ao vivo: observações têm limites de cobertura, resolução e atraso.

A localização é processada no aparelho e exibida na tela; esta versão não envia coordenadas a servidores.

## Catálogo de constelações
- 88 constelações ocidentais, com abreviação IAU, identificação em lista e desenhos conectados por segmentos estelares.
- 725 estrelas com posições e magnitudes catalogadas; 676 segmentos de ligação.
- As fontes e licenças dos dados estão documentadas em `THIRD_PARTY_NOTICES.md`.

## C# e linguagens
C# ainda não foi incorporado. O aplicativo usa Kotlin para integração Android, C e C++ para cálculo/renderização nativa. Adicionar .NET para Android apenas para misturar linguagens aumentaria a complexidade e não aceleraria automaticamente a GPU.

## Compilação
O workflow do GitHub Actions executa `gradle assembleDebug` com JDK 17 e Gradle 8.11.1 a cada push na branch `main`. Quando a compilação terminar, o APK de depuração será publicado como artefato `observatorio-espacial-debug` na execução do workflow.


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
- A versão atual é 1.2.5 (versionCode 3).


## Catálogo local do céu (inspirado em planetários)
- Pesquisa local de estrelas brilhantes e objetos de céu profundo, incluindo objetos Messier como M31, M42, M45, M13, M8, M20, M57 e M51.
- Cada resultado apresenta identificação, tipo, constelação, magnitude visual aproximada e coordenadas equatoriais J2000.
- Quando há localização disponível, o app calcula azimute e elevação aproximados para a hora atual usando tempo sideral local.
- O rótulo de visibilidade indica somente se o objeto está acima do horizonte geométrico; não é uma garantia de observação a olho nu.
- O catálogo é uma seleção inicial própria, não uma cópia do banco de dados do Stellarium e não pretende cobrir todos os objetos.
- O APK enviado foi inspecionado como referência de estrutura e recursos. Seus bancos de dados, imagens e demais arquivos não foram incorporados ao projeto sem verificação independente de licença e origem.

## Ferramentas de compilação
- Android Gradle Plugin 8.10.1, Gradle 8.11.1 e compileSdk 36, necessários para atender aos metadados da dependência OrbitCore 0.1.0.
- targetSdk permanece 35, minSdk permanece 26 e a versão do app continua 1.2.5 (versionCode 3).


## Campo estelar 3D e constelações
- O renderizador OpenGL ES 2.0 agora desenha um campo de estrelas em coordenadas esféricas, com estrelas brilhantes posicionadas por ascensão reta e declinação J2000 aproximadas.
- Cor e tamanho aparente variam por estrela; pontos usam brilho circular suave no shader.
- Linhas de referência conectam estrelas de Órion, Cruzeiro do Sul, Ursa Maior, Leão e Cisne.
- O fundo estelar mantém uma camada procedural de estrelas fracas e a Via Láctea; o catálogo brilhante fica sobreposto a esse fundo.
- A visualização é uma esfera celeste didática em 3D, não uma simulação de distâncias reais entre estrelas. As posições e linhas de constelações são aproximadas e não substituem efemérides astrométricas de precisão.
- A quantidade de estrelas e partículas continua adaptada ao perfil de memória/GPU do aparelho.


## Campo estelar expandido — versão 1.2.5
- O perfil gráfico alto gera mais de 1 milhão de estrelas procedurais (1.000.000 pontos de fundo, além das estrelas brilhantes catalogadas no código).
- Perfis médio e econômico reduzem a quantidade desenhada para 300 mil e 100 mil pontos, respectivamente, para limitar uso de memória e carga da GPU em celulares modestos.
- As estrelas recebem classes de cor aproximadas (azul, branco, amarelo, laranja e vermelho) e tamanhos/brilhos aparentes variados. A distribuição é sintética e serve para visualização, não é um catálogo científico medido.
- As linhas de constelações continuam sendo padrões didáticos aproximados; esta versão não contém milhões de constelações. A União Astronômica Internacional reconhece 88 constelações oficiais. Um catálogo real com milhões de estrelas/objetos (meta futura de 7 milhões) exige dados astrométricos licenciados, armazenamento compacto e carregamento por setores, não a criação de pontos aleatórios.
- Versão Android: 1.2.5 (versionCode 3), Android mínimo 8.0 (API 26).

## Sondas e telescópios espaciais — NASA/JPL
- A pesquisa de objetos agora inclui uma categoria para sondas e observatórios espaciais, consultando efemérides do JPL Horizons por meio da API oficial.
- Catálogo inicial: Voyager 1, Voyager 2, New Horizons, Parker Solar Probe, Juno, Hubble, JUICE, Hayabusa2, SOHO e Lucy.
- O app usa identificadores NAIF/JPL de alvos espaciais e solicita posições observáveis para a localização GPS do usuário: ascensão reta, declinação, azimute e elevação, quando disponíveis.
- As consultas usam a API pública NASA/JPL Horizons; não exigem chave NASA para este serviço. A disponibilidade de cada missão depende do intervalo de dados existente no Horizons.
- Os dados representam efemérides calculadas pelo JPL, não telemetria de bordo em tempo real, comunicação de rádio, vídeo ao vivo ou controle remoto das sondas.
- Satélites artificiais em órbita terrestre continuam usando CelesTrak/NORAD + SGP4/SDP4; sondas interplanetárias e telescópios espaciais usam JPL Horizons.
- Documentação: https://ssd-api.jpl.nasa.gov/doc/horizons.html e https://naif.jpl.nasa.gov/pub/naif/toolkit_docs/FORTRAN/req/naif_ids.html.
- Esta evolução mantém a versão Android em 1.2.5 (versionCode 3).

## Monitoramento contínuo de satélites e missões
- Novo painel **Monitoramento Espacial** consulta o grupo público `ACTIVE` do CelesTrak (GP/OMM) e propaga elementos orbitais com SGP4/SDP4 no aparelho.
- O painel atualiza o catálogo/posições dos satélites a cada 60 segundos enquanto permanece aberto e o app está em primeiro plano.
- Para reduzir consumo de memória em celulares de entrada, cada ciclo avalia até 1.200 objetos do conjunto ACTIVE recebido. A tela informa o total catalogado e quantos foram efetivamente avaliados; portanto, não afirma que todos os registros foram propagados em cada ciclo.
- O painel consulta também as efemérides JPL Horizons das missões Voyager 1/2, New Horizons, Parker Solar Probe, Juno, Hubble, JUICE, Hayabusa2, SOHO e Lucy a cada 10 minutos.
- **Ao vivo significa atualização periódica de previsões orbitais**. Não é telemetria, comando, rastreamento por rádio nem vídeo de missão. Telemetria real só pode ser exibida quando uma agência publicar um feed de missão compatível.
- O monitoramento depende de internet e localização; ele para quando o painel é fechado ou o app vai para segundo plano.
- Compatibilidade mantida: Android 8.0+, OpenGL ES 1.x fallback e ES 2.0 renderer principal. Versão mantida em 1.2.5 / versionCode 3.
