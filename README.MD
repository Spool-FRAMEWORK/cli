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
