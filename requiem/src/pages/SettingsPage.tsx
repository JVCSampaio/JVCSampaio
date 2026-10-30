import { ArrowClockwise, CaretDown, CheckCircle, FolderOpen, FolderPlus, FolderSimple, WarningCircle, X, YoutubeLogo } from "@phosphor-icons/react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Button, IconButton } from "@/components/ui/Button";
import { Segmented, Skeleton, Switch } from "@/components/ui/Controls";
import { Dropdown } from "@/components/ui/Menu";
import { backend } from "@/lib/backend";
import { cn, count } from "@/lib/format";
import type { AccountTest, Settings } from "@/lib/types";
import { useLibrary } from "@/state/library";
import { toast } from "@/state/toasts";

const BROWSERS: Array<{ id: string; label: string }> = [
  { id: "firefox", label: "Firefox" },
  { id: "chrome", label: "Chrome" },
  { id: "edge", label: "Edge" },
  { id: "brave", label: "Brave" },
  { id: "opera", label: "Opera" },
  { id: "vivaldi", label: "Vivaldi" },
  { id: "chromium", label: "Chromium" },
  { id: "safari", label: "Safari" },
  { id: "whale", label: "Whale" },
];

const browserLabel = (id: string) => BROWSERS.find((b) => b.id === id)?.label ?? id;

export function SettingsPage({ section }: { section?: "account" | "folders" }) {
  const settings = useLibrary((s) => s.settings);
  const save = useLibrary((s) => s.saveSettings);
  const accountRef = useRef<HTMLElement>(null);
  const foldersRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const target = section === "account" ? accountRef : section === "folders" ? foldersRef : null;
    target?.current?.scrollIntoView({ block: "start" });
  }, [section]);

  if (!settings) return null;
  const set = (patch: Partial<Settings>) => void save({ ...settings, ...patch });

  return (
    <div className="max-w-3xl px-8 pb-16 pt-8">
      <h1 className="font-display text-5xl font-semibold text-foreground">Ajustes</h1>

      <Group title="Conta do YouTube" ref={accountRef}>
        <AccountSection settings={settings} set={set} />
      </Group>

      <Group title="O que baixar sozinho">
        <Row title="Sincronizar sozinho" description="Lê o YouTube de tempos em tempos e baixa o que for novo." htmlFor="auto-sync">
          <Switch id="auto-sync" label="Sincronizar sozinho" checked={settings.autoSync} onChange={(v) => set({ autoSync: v })} />
        </Row>
        <Row title="De quanto em quanto tempo">
          <Segmented<string>
            label="Intervalo da sincronização"
            value={String(settings.syncIntervalMinutes)}
            onChange={(v) => set({ syncIntervalMinutes: Number(v) })}
            options={[
              { value: "15", label: "15 min" },
              { value: "30", label: "30 min" },
              { value: "60", label: "1 h" },
              { value: "180", label: "3 h" },
            ]}
          />
        </Row>
        <Row title="Histórico" description="Tudo o que você assiste ou ouve no YouTube." htmlFor="sync-history">
          <Switch id="sync-history" label="Histórico" checked={settings.syncHistory} onChange={(v) => set({ syncHistory: v })} />
        </Row>
        <Row title="Vídeos curtidos" htmlFor="sync-likes">
          <Switch id="sync-likes" label="Vídeos curtidos" checked={settings.syncLikes} onChange={(v) => set({ syncLikes: v })} />
        </Row>
        <Row title="Músicas curtidas no YouTube Music" htmlFor="sync-music-likes">
          <Switch id="sync-music-likes" label="Músicas curtidas no YouTube Music" checked={settings.syncMusicLikes} onChange={(v) => set({ syncMusicLikes: v })} />
        </Row>
        <Row title="Na primeira leitura" description="Quantos itens recentes de cada lista baixar. Depois disso, só o que for novo.">
          <Segmented<string>
            label="Itens na primeira leitura"
            value={String(settings.firstSyncLimit)}
            onChange={(v) => set({ firstSyncLimit: Number(v) })}
            options={["10", "30", "100", "500"].map((v) => ({ value: v, label: v }))}
          />
        </Row>
        <Row title="Baixar músicas" htmlFor="dl-music">
          <Switch id="dl-music" label="Baixar músicas" checked={settings.downloadMusic} onChange={(v) => set({ downloadMusic: v })} />
        </Row>
        <Row title="Baixar vídeos" description="Tudo o que não for música, como aulas, podcasts e vídeos de canais." htmlFor="dl-videos">
          <Switch id="dl-videos" label="Baixar vídeos" checked={settings.downloadVideos} onChange={(v) => set({ downloadVideos: v })} />
        </Row>
        <Row title="Incluir Shorts" htmlFor="dl-shorts">
          <Switch id="dl-shorts" label="Incluir Shorts" checked={settings.downloadShorts} onChange={(v) => set({ downloadShorts: v })} />
        </Row>
        <Row title="Música dura até" description="Uma música mais longa que isso é guardada como vídeo, como um show inteiro.">
          <Segmented<string>
            label="Duração máxima de uma música"
            value={String(settings.maxMusicMinutes)}
            onChange={(v) => set({ maxMusicMinutes: Number(v) })}
            options={["10", "20", "40"].map((v) => ({ value: v, label: `${v} min` }))}
          />
        </Row>
        <Row title="Vídeos de até" description="Vídeos mais longos ficam de fora da sincronização. Pela busca, baixam sempre.">
          <Segmented<string>
            label="Duração máxima de um vídeo"
            value={String(settings.maxVideoMinutes)}
            onChange={(v) => set({ maxVideoMinutes: Number(v) })}
            options={[
              { value: "60", label: "1 h" },
              { value: "180", label: "3 h" },
              { value: "0", label: "Sem limite" },
            ]}
          />
        </Row>
      </Group>

      <Group title="Qualidade">
        <Row title="Vídeo" description="Mais resolução, arquivo maior.">
          <Segmented<string>
            label="Resolução dos vídeos"
            value={String(settings.videoHeight)}
            onChange={(v) => set({ videoHeight: Number(v) })}
            options={["480", "720", "1080", "1440"].map((v) => ({ value: v, label: `${v}p` }))}
          />
        </Row>
        <Row title="Áudio das músicas" description="M4A guarda o áudio do YouTube como veio. MP3 toca em qualquer aparelho antigo.">
          <Segmented<Settings["audioFormat"]>
            label="Formato do áudio"
            value={settings.audioFormat}
            onChange={(v) => set({ audioFormat: v })}
            options={[
              { value: "m4a", label: "M4A" },
              { value: "mp3", label: "MP3" },
            ]}
          />
        </Row>
      </Group>

      <Group title="Músicas do computador" ref={foldersRef}>
        <FoldersSection settings={settings} set={set} />
      </Group>

      <Group title="Onde guardar">
        <Row
          title="Pasta da biblioteca"
          description={
            <>
              <span className="block break-all text-foreground" data-selectable>
                {settings.libraryDir}
              </span>
              <span className="mt-1 block">Os arquivos novos vão para cá. Os que já existem ficam onde estão.</span>
            </>
          }
        >
          <div className="flex gap-2">
            <Button
              onClick={async () => {
                const picked = await (await backend()).pickFolder(settings.libraryDir);
                if (picked) set({ libraryDir: picked });
              }}
            >
              Trocar
            </Button>
            <Button variant="ghost" icon={<FolderOpen className="h-4 w-4" />} onClick={async () => (await backend()).openLibraryFolder().catch((e) => toast(String(e), "error"))}>
              Abrir
            </Button>
          </div>
        </Row>
      </Group>

      <Group title="Reprodução">
        <Row title="Tocar parecidas quando a fila acabar" description="Busca no YouTube músicas parecidas com a última que tocou e baixa duas por vez." htmlFor="autoplay">
          <Switch id="autoplay" label="Tocar parecidas quando a fila acabar" checked={settings.autoplay} onChange={(v) => set({ autoplay: v })} />
        </Row>
      </Group>

      <Group title="Comportamento">
        <Row title="Fechar a janela deixa tocando" description="O Requiem fica na bandeja do sistema e continua sincronizando." htmlFor="tray">
          <Switch id="tray" label="Fechar a janela deixa tocando" checked={settings.closeToTray} onChange={(v) => set({ closeToTray: v })} />
        </Row>
        <Row title="Abrir junto com o computador" description="Começa escondido na bandeja, pronto para sincronizar." htmlFor="autostart">
          <Switch id="autostart" label="Abrir junto com o computador" checked={settings.launchAtLogin} onChange={(v) => set({ launchAtLogin: v })} />
        </Row>
      </Group>

      <Group title="Motor">
        <EngineSection />
      </Group>

      <Group title="Sobre">
        <About />
      </Group>
    </div>
  );
}

function Group({ title, children, ref }: { title: string; children: ReactNode; ref?: React.Ref<HTMLElement> }) {
  return (
    <section ref={ref} className="mt-10 scroll-mt-6 border-t border-border pt-8 first-of-type:mt-8 first-of-type:border-t-0 first-of-type:pt-0">
      <h2 className="mb-2 text-xl font-bold text-foreground">{title}</h2>
      <div className="divide-y divide-border">{children}</div>
    </section>
  );
}

function Row({ title, description, htmlFor, children }: { title: string; description?: ReactNode; htmlFor?: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-8 py-4">
      <div className="min-w-0">
        {htmlFor ? (
          <label htmlFor={htmlFor} className="text-[15px] font-medium text-foreground">
            {title}
          </label>
        ) : (
          <p className="text-[15px] font-medium text-foreground">{title}</p>
        )}
        {description && <div className="mt-0.5 text-[13px] text-muted-foreground">{description}</div>}
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  );
}

/** The folders whose songs and videos join the library, read again whenever they change. */
function FoldersSection({ settings, set }: { settings: Settings; set: (patch: Partial<Settings>) => void }) {
  const overview = useLibrary((s) => s.overview);
  const local = useLibrary((s) => s.local);
  // Until the person edits the list, the engine reads the computer's Music folder.
  const folders = settings.musicFolders ?? (overview?.localFolders ?? []).filter((f) => f !== settings.libraryDir);

  return (
    <>
      <p className="pb-2 pt-1 text-[15px] text-muted-foreground">
        O Requiem mostra na biblioteca as músicas e os vídeos destas pastas, com capa, artista e álbum. Os arquivos ficam onde estão.
      </p>
      {folders.map((folder) => (
        <div key={folder} className="flex items-center gap-3 py-3">
          <FolderSimple className="h-5 w-5 shrink-0 text-muted-foreground" />
          <span className="min-w-0 flex-1 break-all text-[15px] text-foreground" data-selectable>
            {folder}
          </span>
          <IconButton label="Tirar esta pasta" size="sm" onClick={() => set({ musicFolders: folders.filter((f) => f !== folder) })}>
            <X className="h-4 w-4" />
          </IconButton>
        </div>
      ))}
      <div className="flex flex-wrap items-center gap-4 py-4">
        <Button
          icon={<FolderPlus className="h-4 w-4" />}
          onClick={async () => {
            const picked = await (await backend()).pickFolder(folders[0] ?? "");
            if (picked && !folders.includes(picked)) set({ musicFolders: [...folders, picked] });
          }}
        >
          Adicionar pasta
        </Button>
        <Button variant="ghost" icon={<ArrowClockwise className="h-4 w-4" />} disabled={local.running} onClick={async () => (await backend()).libraryRescan()}>
          Ler de novo
        </Button>
        <p className="text-[13px] text-muted-foreground" aria-live="polite">
          {local.running
            ? `Lendo as pastas, ${count(local.found, "arquivo encontrado", "arquivos encontrados")} até agora.`
            : local.found
              ? `${count(local.found, "música ou vídeo", "músicas e vídeos")} destas pastas na biblioteca.`
              : "Nenhuma música encontrada nestas pastas."}
        </p>
      </div>
    </>
  );
}

type TestState = { status: "idle" } | { status: "testing" } | { status: "done"; result: AccountTest };

function AccountSection({ settings, set }: { settings: Settings; set: (patch: Partial<Settings>) => void }) {
  const [test, setTest] = useState<TestState>({ status: "idle" });
  const [waitingLogin, setWaitingLogin] = useState(false);
  const account = settings.account;
  const mode = account.mode;

  // A new connection is tested once, so the person sees it work.
  const previous = useRef(mode);
  useEffect(() => {
    if (previous.current === mode) return;
    previous.current = mode;
    setWaitingLogin(false);
    if (mode !== "none") void runTest();
    else setTest({ status: "idle" });
  }, [mode]);

  async function runTest() {
    setTest({ status: "testing" });
    const result = await (await backend()).accountTest().catch((e) => ({ ok: false, message: String(e), sample: [] }));
    setTest({ status: "done", result });
  }

  if (mode === "none") {
    return (
      <>
        <p className="pb-2 pt-1 text-[15px] text-muted-foreground">
          Com a conta conectada, o Requiem baixa sozinho o que você assiste e curte. A busca funciona sem conta.
        </p>
        <Row
          title="Entrar pelo app"
          description={
            waitingLogin
              ? "Termine o login na janela do Google. Ela fecha sozinha quando der certo."
              : "Abre a página do Google numa janela do Requiem. A senha vai direto para o Google."
          }
        >
          <Button
            variant="primary"
            icon={<YoutubeLogo weight="fill" className="h-4 w-4" />}
            onClick={async () => {
              setWaitingLogin(true);
              await (await backend()).accountLogin().catch((e) => {
                setWaitingLogin(false);
                toast(String(e), "error");
              });
            }}
          >
            Entrar no YouTube
          </Button>
        </Row>
        <Row title="Usar o login do navegador" description="Funciona melhor com o Firefox. No Windows, o Chrome e o Edge protegem a sessão e costumam falhar.">
          <Dropdown
            entries={BROWSERS.map((b) => ({ type: "item", label: b.label, onSelect: () => set({ account: { mode: "browser", browser: b.id } }) }))}
            trigger={<Button icon={<CaretDown weight="bold" className="h-3.5 w-3.5" />}>Escolher navegador</Button>}
          />
        </Row>
        <Row title="Importar um cookies.txt" description="Para quem já exporta os cookies com uma extensão do navegador.">
          <Button
            onClick={async () => {
              const api = await backend();
              const path = await api.pickCookieFile();
              if (path) await api.accountImport(path).catch((e) => toast(String(e), "error"));
            }}
          >
            Escolher arquivo
          </Button>
        </Row>
      </>
    );
  }

  const how = mode === "app" ? "pelo app" : mode === "browser" ? `pelo ${browserLabel(account.mode === "browser" ? account.browser : "")}` : "com um cookies.txt";
  return (
    <>
      <Row
        title={`Conectado ${how}`}
        description={
          mode === "browser"
            ? "A cada sincronização, o Requiem lê a sessão aberta no navegador."
            : "O Requiem guarda só a sessão. A senha nunca passa por aqui."
        }
      >
        <div className="flex gap-2">
          <Button icon={<ArrowClockwise className="h-4 w-4" />} disabled={test.status === "testing"} onClick={() => void runTest()}>
            Testar
          </Button>
          <Button variant="danger" onClick={async () => (await backend()).accountDisconnect().catch((e) => toast(String(e), "error"))}>
            Desconectar
          </Button>
        </div>
      </Row>
      {test.status !== "idle" && (
        <div className="py-4">
          {test.status === "testing" ? (
            <div className="space-y-2" aria-label="Testando a conexão">
              <Skeleton className="h-4 w-64" />
              <Skeleton className="h-4 w-48" />
            </div>
          ) : (
            <TestResult result={test.result} />
          )}
        </div>
      )}
    </>
  );
}

function TestResult({ result }: { result: AccountTest }) {
  return (
    <div>
      <p className={cn("flex items-center gap-2 text-[15px]", result.ok ? "text-foreground" : "text-destructive")}>
        {result.ok ? <CheckCircle weight="fill" className="h-5 w-5 shrink-0" /> : <WarningCircle weight="fill" className="h-5 w-5 shrink-0" />}
        <span data-selectable>{result.message}</span>
      </p>
      {result.sample.length > 0 && (
        <ul className="mt-2 space-y-1 pl-7 text-[13px] text-muted-foreground">
          {result.sample.map((e) => (
            <li key={e.id} className="truncate">
              {e.title}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function EngineSection() {
  const tools = useLibrary((s) => s.tools);
  const [updating, setUpdating] = useState(false);
  const status = tools.status;
  const rows: Array<{ name: string; version: string | null | undefined; about: string }> = [
    { name: "yt-dlp", version: status?.ytdlp, about: "Lê e baixa do YouTube." },
    { name: "Deno", version: status?.deno, about: "Resolve o desafio de JavaScript que o YouTube pede." },
    { name: "FFmpeg", version: status?.ffmpeg, about: "Junta áudio e vídeo e converte para MP3." },
  ];
  return (
    <>
      {rows.map((r) => (
        <Row key={r.name} title={r.name} description={r.about}>
          {r.version ? (
            <span className="text-sm tabular-nums text-muted-foreground" data-selectable>
              {r.version}
            </span>
          ) : tools.installing ? (
            <Skeleton className="h-4 w-20" />
          ) : (
            <span className="text-sm text-destructive">Faltando</span>
          )}
        </Row>
      ))}
      <div className="flex items-center gap-4 py-4">
        {status?.ready ? (
          <Button
            icon={<ArrowClockwise className="h-4 w-4" />}
            disabled={updating}
            onClick={async () => {
              setUpdating(true);
              try {
                const next = await (await backend()).toolsUpdate();
                useLibrary.setState((s) => ({ tools: { ...s.tools, status: next } }));
                toast(`yt-dlp na versão ${next.ytdlp ?? "nova"}.`);
              } catch (e) {
                toast(String(e), "error");
              } finally {
                setUpdating(false);
              }
            }}
          >
            {updating ? "Atualizando o yt-dlp" : "Atualizar o yt-dlp"}
          </Button>
        ) : (
          <Button
            variant="primary"
            disabled={tools.installing}
            onClick={async () => {
              useLibrary.setState((s) => ({ tools: { ...s.tools, installing: true, error: null } }));
              await (await backend()).toolsInstall();
            }}
          >
            Instalar o que falta
          </Button>
        )}
        <p className="text-[13px] text-muted-foreground">O YouTube muda sempre. O Requiem atualiza o yt-dlp uma vez por dia sozinho.</p>
      </div>
    </>
  );
}

function About() {
  const overview = useLibrary((s) => s.overview);
  const update = useLibrary((s) => s.update);
  const [checking, setChecking] = useState(false);
  const check = async () => {
    setChecking(true);
    const api = await backend();
    try {
      const found = await api.updateCheck();
      if (found) {
        useLibrary.setState({ update: found });
        toast(`A versão ${found.version} está disponível.`, "info", { label: "Baixar", run: () => void api.updateOpen() });
      } else {
        toast("Você já está na versão mais nova.");
      }
    } catch (e) {
      toast(String(e), "error");
    } finally {
      setChecking(false);
    }
  };
  return (
    <div className="space-y-3 py-4 text-[15px] text-muted-foreground">
      <div className="flex flex-wrap items-center gap-4">
        <p>
          <span className="font-display text-2xl font-semibold text-foreground">Requiem</span>
          <span className="ml-2 text-sm tabular-nums">versão {overview?.version}</span>
        </p>
        {update ? (
          <Button variant="primary" size="sm" onClick={async () => (await backend()).updateOpen()}>
            Baixar a versão {update.version}
          </Button>
        ) : (
          <Button size="sm" icon={<ArrowClockwise className="h-4 w-4" />} disabled={checking} onClick={() => void check()}>
            {checking ? "Procurando" : "Procurar atualização"}
          </Button>
        )}
      </div>
      <p>Feito com yt-dlp, FFmpeg e Deno. As letras vêm do LRCLIB, um acervo aberto e comunitário.</p>
      <p>Guarde só o que você tem direito de guardar. Quem faz a música agradece quando você também ouve nos canais oficiais.</p>
      {overview?.dataDir && (
        <p className="text-[13px]">
          Dados do app em <span className="break-all text-foreground" data-selectable>{overview.dataDir}</span>
        </p>
      )}
      <p className="text-[13px]">Fontes Grenze Gotisch e Alegreya Sans sob a licença OFL. Ícones Phosphor sob a licença MIT.</p>
    </div>
  );
}
