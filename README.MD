# Spool — CLI

[![License](https://img.shields.io/badge/License-AGPL%203.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)

The `spool` command checks a **Spool** descriptor without starting a node, writes the Terraform of its
infrastructure and applies it. It loads the descriptor as a node does, with its includes, profile and variables, and
with the classes of the project: event classes, aggregators, normalizers and the plugins among its dependencies.

Decided in [ADR-029](https://linear.app/javier-castilla/document/adr-029-la-cli-de-spool-es-java-usa-el-classpath-del-proyecto-y-se-47a76a188eb6).

> Requires **Java 21+**. `spool provision` also requires [Terraform](https://developer.hashicorp.com/terraform/install)
> and, for the local target, Docker.

---

## Install

```sh
curl -fsSL https://spoolframework.dev/install.sh | sh          # Linux and macOS
```

```powershell
irm https://spoolframework.dev/install.ps1 | iex               # Windows
```

The script downloads `spool.zip` of the latest release and its SHA-256, stops without installing anything when they do
not match, unpacks it into `~/.spool` and puts `~/.spool/bin` on the PATH of the user, with no administrator rights.
Without Java 21 it says so and offers a Temurin runtime inside `~/.spool/jre`, only for `spool`. Running it again
installs the latest version over the old one. Both scripts can be read before running them, in `install/`.

| Variable | Effect |
|---|---|
| `SPOOL_HOME` | Where to install, `~/.spool` by default |
| `SPOOL_NO_MODIFY_PATH` | Leaves the PATH alone and says what to add |
| `SPOOL_INSTALL_JRE` | `yes` or `no`, to answer about the Java runtime without being asked |
| `SPOOL_DOWNLOAD_URL` | Where `spool.zip` and `spool.zip.sha256` are, the latest release by default |

Without the scripts, unpack `spool.zip` of a release anywhere and run `bin/spool` or `bin\spool.cmd`.

### How a release reaches the scripts

Every tag `vX.Y.Z` attaches `spool.zip`, `spool.zip.sha256`, `install.sh` and `install.ps1` to its GitHub release
(`.github/workflows/distribution.yml`), so `releases/latest/download/` always serves the newest. The web downloads
the two scripts from there when it deploys, and the release asks it to deploy again. That needs, once:

1. In GitHub, *Settings → Developer settings → Fine-grained tokens*, a token for the repository `web` with
   *Contents: Read and write*.
2. In the repository `cli`, *Settings → Secrets and variables → Actions*, a secret `WEB_DISPATCH_TOKEN` with it.

Without the secret the release still publishes everything, and the web serves the scripts of its last deploy.

---

## Commands

| Command | What it does |
|---|---|
| `spool validate DESCRIPTOR` | Loads the descriptor and builds every module without starting it. Exit code 0 when it is valid, 1 with the file and the key when it is not. |
| `spool terraform DESCRIPTOR --target TARGET [--out FOLDER]` | Writes the Terraform of its infrastructure for `local` or `aws` into `infrastructure`, or the folder given. Nothing is applied. |
| `spool provision DESCRIPTOR --target TARGET [--out FOLDER] [--yes]` | Writes the Terraform into `.spool/terraform` and, after a yes, runs `terraform init` and `terraform apply`. `--yes` is the yes. |

Every command takes `--profile` (by default the one in `SPOOL_PROFILE`) and `--classpath`. Exit code 2 is a command
used wrongly, such as an unknown `--target`.

## The classes of the project

- With `--classpath`, the classes are taken from there: folders and jars separated by `;` on Windows and `:` elsewhere.
- Without it, in a folder with a `pom.xml`, the classes are `target/classes` and the dependencies Maven resolves, so the
  project must be compiled first (`mvn compile`).
- Otherwise only the classes of Spool itself are seen.

Programs are found in `<NAME>_BINARY` (`TERRAFORM_BINARY`, `MVN_BINARY`) or on the `PATH`.

---

## A complete example

`Spool.yaml`, in a Maven project whose aggregator `com.example.RunningTotal` is in `src/main/java`:

```yaml
infrastructure:
  bus: {kind: EVENT_BUS, type: IN_MEMORY}
  lake: {kind: DATA_LAKE, type: S3, configuration: {bucket: orders-lake, region: eu-west-1, endpoint: "http://localhost:4566"}}
  mart: {kind: DATA_MART, type: S3, configuration: {bucket: orders-mart, region: eu-west-1, endpoint: "http://localhost:4566"}}
  quarantine: {kind: QUARANTINE_REPOSITORY, type: S3, configuration: {bucket: orders-quarantine, region: eu-west-1, endpoint: "http://localhost:4566"}}
modules:
  - mounter:
      id: running-total
      type: POLL
      schedule: {every: 1m}
      mode: AGGREGATION
      aggregator: com.example.RunningTotal
      read: {partitions: ["source=orders"]}
      write: {dataMart: running-total, partitioning: {type: SAME_AS_SOURCE}}
```

```console
$ mvn -q compile
$ spool validate Spool.yaml
Spool.yaml is valid: 1 module
$ spool terraform Spool.yaml --target local --out infrastructure
Terraform for local written to infrastructure
$ spool provision Spool.yaml --target local
Apply the Terraform for local in .spool/terraform? [y/N] y
(the output of terraform init and terraform apply)
```

A mistake ends with exit code 1 and an error that starts with `Invalid descriptor`, the path of the file and the key
of the wrong value, as when a node starts.

---

## Adding a command

A command is a picocli `@Command` that implements `Callable<Integer>`, with a nested `Factory` that implements
`SpoolSubcommand`, listed in `META-INF/services/software.spool.cli.command.SpoolSubcommand`. It receives a
`CommandContext` with the working folder, the environment, the terminal and the installed programs, and reaches for
nothing else, so it runs the same in a test. Another build tool for the classes of the project is a `ClasspathSource`,
listed the same way.
