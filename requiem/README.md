# Requiem

App de computador que guarda o que você ouve no YouTube. Ele lê o seu histórico e as suas
curtidas, baixa sozinho as músicas e os vídeos novos, e toca tudo offline, num player escuro,
rápido e sem anúncios. Qualquer outra música você busca e baixa dentro do próprio app.

Windows, macOS e Linux. Feito em Rust ([Tauri 2](https://tauri.app)) com a interface em React.

<p>
  <img src="docs/preview/02-playing-lyrics.jpg" width="880" alt="Tocando Danse Macabre com a letra sincronizada">
</p>

## O que ele faz

- **Baixa sozinho o que você ouve.** A cada 15 min, 30 min, 1 h ou 3 h, lê o histórico, os
  vídeos curtidos e as músicas curtidas no YouTube Music. O que for música vira áudio (M4A ou
  MP3), o resto vira vídeo (até 1440p). Shorts, transmissões ao vivo e vídeos longos demais ficam
  de fora, e cada regra é ajustável. O que ficou de fora aparece em Downloads com "Baixar assim
  mesmo".
- **Baixa qualquer música, e toca na hora.** A busca procura no YouTube e mostra artista e
  duração de cada resultado; as músicas que você já tem aparecem primeiro, em "Na sua
  biblioteca". Clique na capa de um resultado e ele toca assim que o arquivo chega (alguns
  segundos: ele passa na frente de tudo na fila). Também aceita um link de vídeo ou de playlist
  colado: uma playlist inteira vira playlist no Requiem com um clique.
- **Traz suas playlists do Spotify.** Cole o link de uma playlist, álbum ou música do Spotify
  na busca, ou cole uma lista de músicas (de qualquer app, um CSV do Exportify ou vários
  links). Cada música é procurada no YouTube pelo título, artista e duração; versões ao vivo,
  covers e remixes só entram quando a lista pede. O que não tiver um par bom fica listado para
  você buscar.
- **Rádio e músicas parecidas.** "Iniciar rádio" no menu de uma música monta uma estação a
  partir dela, e quando a fila acaba músicas parecidas continuam tocando (dá para desligar).
  O que já está na biblioteca toca de lá, sem baixar de novo.
- **Som melhor.** O volume fica igual entre músicas (o Requiem mede cada arquivo uma vez, no
  padrão EBU R128 usado pelos streamings, ou usa o ReplayGain que já estiver nele), por música
  ou por álbum. Equalizador de 10 bandas com predefinições, transição suave entre músicas (até
  12 s), faixas de um mesmo álbum emendadas sem silêncio, timer para dormir e velocidade para
  vídeos e aulas.
- **Toca tudo offline.** Fila que se arrasta para reordenar, aleatório, repetição, letras
  sincronizadas do [LRCLIB](https://lrclib.net), vídeos no painel ou em tela cheia, teclas de
  mídia do teclado e o painel de mídia do sistema, controles na bandeja, e continua de onde
  parou (vídeos longos e aulas voltam ao ponto em que você estava).
- **Lembra o que você ouviu.** Curtidas, tocadas recentemente e a Retrospectiva: tempo
  ouvindo, músicas e artistas mais tocados e o seu horário favorito, nos últimos 7 dias, 30
  dias, no ano ou desde sempre.
- **Organiza sem pedir nada.** "Artista - Música (Official Video)" vira título e artista
  limpos. Quando o YouTube Music informa faixa, artista e álbum, a biblioteca usa esses dados e
  monta as páginas de artista e de álbum.
- **Mostra o que já está no computador.** Lê a sua pasta Música (e as outras que você
  escolher em Ajustes) e põe na biblioteca as músicas e os vídeos que já estão lá, com capa,
  artista e álbum tirados das tags. Os arquivos ficam onde estão: tirar uma dessas músicas da
  biblioteca nunca apaga o arquivo.
- **Os arquivos são seus.** Ficam em `Música/Requiem` (ou na pasta que você escolher), em
  `Músicas` e `Vídeos`, com o nome "Artista - Título [id].m4a". Qualquer outro player abre.

<p>
  <img src="docs/preview/05-search-results.jpg" width="430" alt="Busca com resultados prontos para baixar">
  <img src="docs/preview/13-downloads.jpg" width="430" alt="Downloads com a sincronização e a fila">
</p>
<p>
  <img src="docs/preview/07-library-music.jpg" width="430" alt="Biblioteca de músicas">
  <img src="docs/preview/06-search-playlist.jpg" width="430" alt="Playlist do YouTube pronta para importar">
</p>

As imagens acima são da prévia no navegador, com dados de exemplo e capas geradas. No app de
verdade, as capas são as miniaturas do YouTube.

## Instalar

Os instaladores saem do GitHub Actions a cada mudança (workflow "Requiem Desktop") e vão para
a página de releases quando uma versão é aprovada.

- **Windows:** `Requiem-Windows.exe`. O app não tem assinatura paga, então o Windows pode
  avisar que ele é desconhecido: clique em **Mais informações** e depois em **Executar assim
  mesmo**.
- **macOS:** `Requiem-Mac.dmg` (Intel e Apple Silicon). Arraste para Aplicativos. Na primeira
  vez, clique com o botão direito no Requiem e escolha **Abrir**. Se o macOS disser que o app
  está danificado, rode `xattr -cr /Applications/Requiem.app` no Terminal.
- **Linux:** `Requiem-Linux.AppImage` (marque como executável e abra) ou `requiem-linux.deb`.

Na primeira abertura, o Requiem baixa o motor: [yt-dlp](https://github.com/yt-dlp/yt-dlp),
[Deno](https://deno.com) (que o yt-dlp usa para os desafios do YouTube) e
[FFmpeg](https://ffmpeg.org). São uns 150 MB de download, uma vez só. O yt-dlp se atualiza sozinho uma vez
por dia, porque o YouTube muda o tempo todo.

## Conectar o YouTube

A busca funciona sem conta. Para o Requiem ler o seu histórico e as suas curtidas, conecte o
YouTube em **Ajustes**, de um destes jeitos:

1. **Entrar pelo app** (o mais simples). Abre a página de login do Google numa janela do
   Requiem. A senha vai direto para o Google. O Requiem guarda só os cookies da sessão, num
   arquivo que só a sua conta do computador lê.
2. **Usar o login do navegador.** O yt-dlp lê a sessão já aberta no navegador a cada
   sincronização. Funciona melhor com o Firefox. No Windows, o Chrome e o Edge protegem os
   cookies e costumam falhar.
3. **Importar um `cookies.txt`** exportado por uma extensão do navegador.

O botão **Testar** lê os cinco vídeos mais recentes do seu histórico para provar que deu certo.
O histórico do YouTube precisa estar ativado na sua conta.

## Atalhos

| Tecla | Faz |
|---|---|
| Espaço | Tocar ou pausar |
| Ctrl + seta para a direita ou esquerda | Próxima ou anterior |
| Shift + seta para a direita ou esquerda | Avançar ou voltar 10 segundos |
| Ctrl + seta para cima ou para baixo | Volume |
| Ctrl + K | Buscar |
| L, Q | Letra, fila |
| F | Tela cheia (vídeos) |
| M | Silenciar |
| Alt + seta para a esquerda | Voltar |

## Como é feito

```
requiem/
  core/        motor em Rust, sem janela: banco SQLite, sincronização, fila de downloads,
               yt-dlp, servidor local de mídia, letras, leitura das pastas de música.
               97 testes, entre eles um que baixa com um yt-dlp de mentira e confere
               que a música chega à biblioteca e toca, e outro que transforma uma lista
               do Spotify numa playlist.
  src-tauri/   a casca do app: janela, bandeja, comandos, login do YouTube.
  src/         a interface em React (Vite, Tailwind, Radix, Phosphor).
```

- O motor conversa com o yt-dlp como processo separado. Primeiro lê as informações do vídeo,
  decide se é música ou vídeo, e então baixa a partir da mesma leitura.
- A mídia chega à interface por um servidor HTTP local, só em `127.0.0.1`, com um token
  aleatório por sessão e suporte a Range (para pular para qualquer ponto da música).
- Os vídeos tocam num elemento de vídeo e as músicas em dois elementos de áudio, para uma
  entrar enquanto a outra sai. Os três passam por uma cadeia de Web Audio (ganho de cada
  música, equalizador, limitador, volume). Se essa cadeia não funcionar num computador, o app
  percebe na primeira música e continua tocando sem ela.

### Rodar e testar

```bash
cd requiem
npm ci
npm run dev                 # prévia no navegador com dados de exemplo
npx tauri dev               # o app de verdade
cargo test -p requiem-core  # testes do motor
cargo test -p requiem-core -- --ignored online_   # instala o motor e baixa do YouTube de verdade
```

No Linux, o app precisa de `libwebkit2gtk-4.1-dev`, `libayatana-appindicator3-dev`,
`librsvg2-dev` e dos plugins do GStreamer (`gstreamer1.0-plugins-good`, `gstreamer1.0-libav`).

O CI instala o `.deb` numa máquina limpa e deixa o app percorrer as próprias telas
(`REQUIEM_TOUR=1`): primeira abertura, busca, uma música baixada tocando com letra, um vídeo,
biblioteca e ajustes. No caminho, ele mede o som de verdade (estado da cadeia de áudio, o
volume medido de cada música comparado com o FFmpeg do próprio CI, a transição entre duas
músicas e o título que o sistema vê) e escreve tudo no resumo da execução. Com
`[screenshots]` na mensagem do commit, as capturas vão para `docs/screenshots`.

## Uso responsável

Guarde só o que você tem direito de guardar, para ouvir você mesmo. Os Termos de Serviço do
YouTube restringem downloads, e as leis de direito autoral variam de país para país. Quem faz a
música agradece quando você também ouve nos canais oficiais.

## Licenças

- Código do Requiem: MIT.
- Fontes [Grenze Gotisch](https://fonts.google.com/specimen/Grenze+Gotisch) e
  [Alegreya Sans](https://fonts.google.com/specimen/Alegreya+Sans): SIL Open Font License
  (arquivos em `public/fonts`).
- Ícones [Phosphor](https://phosphoricons.com): MIT.
- O yt-dlp (Unlicense), o Deno (MIT) e o FFmpeg (LGPL/GPL) não vêm dentro do app: são baixados
  dos projetos oficiais na primeira abertura.
