# cfucli

RustDesk for the terminal. One real shell that two people on different machines can both watch and type into, met through a rendezvous both ends dial outbound to — no inbound port, no VPN, no admin rights on either side. Plus a cli surface so an agent can drive the far machine with exact exit codes and separated streams.

When both ends are on the same machine it skips the relay entirely and talks over a loopback socket instead, which makes it usable as an everyday local console: a shell in a window a person can watch, driven from the command line, with the whole session recorded and queryable.

## The pieces

Five maven modules under an aggregator. `relay` is the transport, framing, end-to-end crypto and session model — no UI, no PTY. `record` is the local ArcadeDB store and its query surface. `node` is the resident half: the PTY, the host and viewer sessions, and a loopback JSON protocol. `app` is the JavaFX window — host, viewer and tray manager in one exe. `cli` is picocli over the loopback protocol.

Two jars ship: `app/shade/cfucli-app.jar` is the window, `cli/shade/cfucli.jar` is the command line.

## Build

```
mvn install
```

JDK 25. pty4j comes from the JetBrains maven repository, declared in the root pom.

## Use it

On Windows the two launchers are `cfucli.exe` and `cfucliapp.exe` in `cmdtools` — jr.exe copies with `.jrc` files beside them pointing at the jars built here, so the examples below are what you actually type. Without them, `java -jar cli/shade/cfucli.jar ...` and `java -jar app/shade/cfucli-app.jar ...` are the same thing.

A shell on this machine, in a window, driven from the cli:

```
cfucli console --node work
cfucli exec --node work "dir"
```

Share this machine's shell with someone else — read them the id and the one-time password it prints:

```
cfucliapp --host
```

Join a session someone else is sharing, then drive it and move files across:

```
cfucli join <id> -p <password> --node far
cfucli exec --node far "dir"
cfucli put config.toml C:\Users\sam\project\ --node far
cfucli get C:\Users\sam\log.txt . --node far
```

Anything longer than one line goes as a file rather than as a command string — a command reaches the far shell as a string, so your shell, this cli and the interpreter over there each get a turn at its quotes:

```
cfucli run-script build.ps1 release "with a space" --node far
```

That is put, exec and delete. The job appears in `jobs` exactly as an `exec` would, the script exit code is the command exit code, `--keep` leaves the file behind, `--shell` overrides the interpreter chosen from the extension, and when the session is on this machine nothing is staged because there is nowhere to send it.

`cfucli guide` is the manual. `CLAUDE_CODE_USAGE.md` in this folder is the same material written for an agent.

## Moving files

`put` and `get` pick their route by size and say which they took. Two ends on one machine share a filesystem, so nothing is transferred. A file under `largeFileThresholdBytes` (256K) goes through the relay as encrypted 64K chunks. Anything larger goes through a folder both machines already have mounted — `largeFileExchangeDir` in settings on both ends, which is what a synced Google Drive folder is for — and only the relative path crosses the wire, so the two machines may mount it at different absolute paths. Arrival is decided by SHA-256 rather than by the file appearing, because a sync product gives no signal when it has finished writing.

With no such folder configured, a large file is refused with both ways out named rather than quietly spending a few hundred metered relay messages; `--via relay` overrides that. Nothing is overwritten without `--force`.

## Credentials

Only needed for sessions that cross machines. Put the values from the Upstash console into `~/cfucli/settings.toml` — `REDIS_URL` from the Redis tab, `UPSTASH_REDIS_REST_URL` and `UPSTASH_REDIS_REST_TOKEN` from the REST tab. Native TLS on 6379 is preferred and REST over 443 is the fallback for networks that block it; `status` says which one a session got and why.

Windows paths in that file go in **single** quotes — `largeFileExchangeDir = 'C:\some\folder'`. In double quotes TOML treats each backslash as an escape, the file fails to parse, and every node then refuses to start.

Nothing secret is compiled into the jars, and a local session reads no credentials at all. Verified rather than assumed: the three credential fields in `Settings` carry no defaults, and a scan of both shaded jars finds the key NAMES only.

`cfucli relay` is the one-command way to see or change this, so that rotating a credential is something you can ask a person to do rather than talking them through editing TOML:

```
cfucli relay show               what is configured, masked; --reveal for the real values
cfucli relay set --from f.txt   point this machine at a rendezvous ( - reads stdin )
cfucli relay clear --yes        forget it; this machine becomes local-only
```

`set` takes a file and there is deliberately **no** option that takes a token as an argument: a secret on a command line lands in shell history and in any agent transcript of that shell, permanently, where nobody thinks to look for it. The parser is forgiving about what you paste -- console lines, a whole `settings.toml`, either quoting style, `=` or `:` -- because the input is a human copying between two windows.

### How a distributed copy gets its credentials

The distribution folder ships with `settings.toml` already filled in, and `SettingsStore` seeds the home copy from the one beside the jar on first run. So the person at the other end configures nothing. **That zip is therefore the one artifact that carries live credentials** -- it is never published, never attached to a release, and hand-carried only. `dist/` and `settings.toml` are both gitignored, the latter by name as well as by location, because the file is read from beside whatever jar is running and the next copy of it will appear somewhere nobody predicted.

The credential is shared by every machine that folder reached, so it is worth keeping the account used for distributed copies separate from the one your own machines use: then losing a laptop means rotating the field credential and re-shipping, without moving your own setup. `relay set` is what makes that re-pointing a single step at the far end.

## Security

A one-time password per session authenticates an X25519 exchange rather than being the key, so each visit has its own traffic key and a password recovered later opens nothing recorded from the relay. Frames are AES-256-GCM. The rendezvous carries routing and liveness only — never command text and never output.

The host prompts before admitting a viewer, refuses on silence, and can lock or restrict the remote end at any moment without ending the session. Closing the window ends it for good. Everything either side does is written to a local recording the owner of the machine can read, scrub or delete.

A shared session also ends on its own: fifteen minutes idle, four hours absolute, both configurable. A channel into someone's machine that never closes itself is a backdoor whatever it was built for. A local-only console is exempt, because nothing about it is reachable from off that machine. Recordings are swept on a timer too, so a transcript is not left indefinitely as somewhere for a password to sit.
