# cfucli, for an agent

Read this once and you can drive a real shell that a human can watch, on this machine or on someone else's, and query everything that happened afterwards. `cfucli guide` prints the same material as a manual in the terminal, and every verb answers `--help`.

## Why you would use this instead of your own console

Your built-in console floods the transcript, runs where the human cannot see it or type into it, cannot be given stdin, and is awkward to attach to from outside. A local cfucli console is the opposite of all four: the shell runs in a visible window the person can take over at any moment, `exec` gives back an exact exit code with stdout and stderr apart, `--stdin` feeds it input, `screen` shows what is on the screen instead of every byte it has printed, and the whole session is recorded so you can go back over it with `grep`, `jobs` and `summary`.

It costs nothing to run locally. Nothing goes near Upstash, no credentials are read, and a keystroke crosses in about a millisecond.

## The thirty-second version

```
cfucli console --node work            a shell in a window, on this machine only
cfucli exec    --node work "dir"      run it; its exit code becomes cfucli's
cfucli screen  --node work            what is on the screen right now
cfucli tail    --node work -n 40 -f   what it printed, and what it prints next
cfucli node stop --node work          done
```

`--node` names which console you are talking to, so you can keep several apart. Everything that reports state takes `--json`.

## Four things about the model

**A node is a resident process** holding one session and its recorded history. Any verb starts one if none is running. Two nodes cannot share a name, because the history store is held exclusively.

**The window is the node.** A node started headless has no window and cannot grow one later — `screen` will tell you so rather than pretend. If the human should be able to watch, start it with `console`, not `node start`.

**Anything longer than one line goes through `run-script`, not `exec`.** A command reaches the far shell as a *string*, so every layer between you and it gets a turn at the quotes — your shell, this cli, then the interpreter at the other end. A PowerShell one-liner with nested quotes is the most reliable way to lose an hour here, and the failure is rarely a clean error: it is a mangled command that runs and does something almost right. Write the script to a local file and hand over the file. `cfucli run-script build.ps1 arg "arg with space"` does put, exec and delete; the job shows up in `jobs` exactly as an `exec` would, and the script exit code is the command exit code. `--keep` leaves the file there, `--shell` overrides the interpreter, and when the session is on this machine nothing is staged at all because there is nowhere to send it.

**`exec` is not the shared shell.** It runs its own process beside the shell, which is the only way an exit code and separated streams can be exact — a shell sitting in a pager or at a password prompt can give you neither. What it ran is announced in the activity log so the human still sees it. When you actually want to drive the shell itself — change directory, answer a prompt — that is `send-keys`.

**Killing something is `cancel`, not Ctrl-C.** `send-keys --ctrl c` does deliver the control character, and at a prompt cmd.exe cancels the line, but whether a program already running is interrupted depends on its console mode — measured here, it did not stop a running `ping`. Anything you may need to kill should be started with `exec --detach`, so `cancel <jobId>` can take it and its children down.

**The terminal carries the shell and nothing else.** What the tool did — a command you ran, a viewer arriving, a refusal — goes to a separate activity channel: `F12` opens it as a pane under the terminal in the window, and it is the `control` stream in the recording. So `tail` gives you the program's output rather than the program's output with a log spliced through it, and nothing cfucli says can land in the middle of what the shell was drawing. `tail --streams all` asks for the tool's lines as well.

**If a human is going to watch you work, tell them to press `F12` before you start.** This is on you, not on them. The terminal pane carries the shell; the commands driving it are on the activity pane, which is shut by default. The legend counts what is waiting and names the key, but a person watching output scroll past has been handed the consequences of your commands and not the commands, and they will not know to look. One sentence at the start of the session — "press F12 to see what I am running" — is the whole fix. Where being seen matters more than an exact exit code, drive the shell with `send-keys` instead and it appears in the terminal as if typed.

**Everything is recorded as it happens**, tagged by stream (`input`, `output`, `exec_stdout`, `exec_stderr`, `control`, `error`) and numbered, which is why the history verbs need not ask the far end anything.

## Exit codes

`exec` and `wait` return the command's own exit code, unchanged — so `cfucli exec ...` behaves in a script exactly as the command would locally. Otherwise: `0` did what was asked, `4` the far end or the node refused it (reason on stderr), `5` nothing to talk to — no node running, or no screen to show, `70` something else went wrong or an exit code could not be determined.

## Habits worth having

Look at `screen` before `tail`, and at `summary` or `jobs` before either — the high-level view is usually enough to know where to look, and it is a few lines rather than a few thousand.

**The recording outlives the session, and reading it needs no live session.** After a session has ended its every byte is still in that node's local store, so `jobs`, `job`, `summary`, `tail`, `events` and `grep` all still answer. They start a node on demand if none is running, the same as every other verb; with no live session they ask for `--session` and list the ids they hold, so `sessions` first is optional rather than required. `job <id>` needs no session at all, because a job id is unique across the store — `--session` there only checks that the job is the one you meant.

Use `exec --detach` for anything long: it returns a job id at once, `wait` picks it up, `tail -f --for 30` watches it meanwhile, and `cancel` kills it. Note that `--detach` plus `wait` is also how you keep a build's output out of your context until you actually want it.

`exec` cuts each stream at 200000 bytes and says on stderr what it cut and which job holds the rest; `--max-bytes 0` turns that off and `--quiet` leaves only the exit code.

Pass `--stdin "text"` or `--stdin-file F` (`-` reads your own stdin) when a command wants input. The pipe is always closed afterwards, so a command that reads stdin and is given none finishes rather than hanging to the timeout.

## Driving a machine that is not this one

The far end runs the window app and reads out a nine-digit id and a one-time password. Then `cfucli join <id> -p <password> --node far`, and everything above works the same. If that id turns out to be a session hosted on this machine, the relay is skipped automatically and the loopback is used instead — `status` says which transport a session actually got.

**A joined session is not yet an admitted one.** If the far end is running the window, a human there has to approve the connection; until they do, `status` shows the session but the host has not attached. `put` and `get` say so within three seconds rather than blocking; `exec`'s answer is its timeout.

The host can lock you out (`lock`) or make you read-only (`view-only`) at any moment; when that happens `exec` exits 4 with the reason rather than looking like a success. `status` distinguishes no session, session open but the far end silent, and far end answering, and never collapses those into an empty result.

**A shared session ends on its own** — fifteen minutes idle, four hours absolute, both from settings and both shown by `status` as `expires`. A job still running counts as activity, so a long build is not cut off underneath you. A local console is exempt entirely: there is no remote party to time out, so it will wait as long as you think.

## Moving files

`put` and `get` are the joining end's verbs, and they choose their route by size rather than making you choose:

```
cfucli put config.toml C:\Users\sam\project\ --node far           relay: small, encrypted chunks
cfucli put ccls.jar  --node far                                   over the threshold - see below
cfucli get C:\Users\sam\log.txt . --node far                      a directory destination is fine
```

- **Two ends on one machine** — nothing is transferred; the far end copies the file. Instant, no size limit.
- **Under `largeFileThresholdBytes`** (256K by default) — through the relay as encrypted 64K chunks.
- **Over it** — through a folder both machines already have mounted, named by `largeFileExchangeDir` in settings **on both ends**; a synced Google Drive folder is exactly what this is for. Only the relative path crosses the wire, so the two machines may mount it at different absolute paths. Arrival is decided by SHA-256, not by the file appearing, because a sync product gives no signal when it has finished writing.
- **Over it with no such folder** — refused, with both ways out named. `--via relay` pushes it through anyway; the refusal tells you how many metered messages that is. This is deliberate: a 12M jar is ~190 relay messages, and doing that silently is worse than declining.

Every transfer is checksummed, nothing is overwritten without `--force`, and the route taken is in the answer. `locked` and `view-only` refuse both verbs, the same as `exec`. `--json` gives `{ok, route, path, bytes, millis}` or `{ok:false, error}`; exit code 0 or 4.

## Where things are

Settings, including credentials and `appJar`, are in `~/cfucli/settings.toml`. Recordings are one store per node under `~/cfucli/db/`. Node ports, local session endpoints and node logs are in `~/cfucli/run/` — that last directory is the first place to look when something started but did not come up.

**A Windows path in that file goes in single quotes** — `largeFileExchangeDir = 'C:\some\folder'`. In double quotes TOML reads every backslash as an escape, the file fails to parse, and every node on the machine then refuses to start.

Recordings hold whatever the shell printed, which can include a password someone typed. `scrub` blanks the text while keeping the shape and exit codes, `forget` deletes a session's recording, and `retain` drops anything older than the window in settings — which a node now also does by itself every six hours, never touching a session it is currently recording. `status` reports what the last sweep did.
