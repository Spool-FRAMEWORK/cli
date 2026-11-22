Feature: spool validate checks a descriptor without starting a node

  The descriptor is loaded as a node loads it, with its includes, its profile and its variables, and every module is
  built without starting it, with the classes of the project. A valid descriptor ends with exit code 0; an invalid
  one ends with exit code 1 and an error that names the file and the key.

  @DEV-99
  Scenario: A valid descriptor
    Given the descriptor "Spool.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
        inbox: {kind: INBOX, type: FILE_SYSTEM, configuration: {path: target/cli-test/inbox}}
        lake: {kind: DATA_LAKE, type: FILE_SYSTEM, configuration: {path: target/cli-test/lake}}
      modules:
        - ingester: {type: REACTIVE, id: orders-ingester}
      """
    When I run "spool validate Spool.yaml"
    Then the exit code is 0
    And the output says "Spool.yaml is valid: 1 module"

  @DEV-99
  Scenario: An invalid descriptor names the file and the key
    Given the descriptor "Spool.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
        inbox: {kind: INBOX, type: FILE_SYSTEM, configuration: {path: target/cli-test/inbox}}
        lake: {kind: DATA_LAKE, type: FILE_SYSTEM, configuration: {path: target/cli-test/lake}}
      modules:
        - ingester: {type: FLUSH, id: orders-ingester, configuration: {every: soon}}
      """
    When I run "spool validate Spool.yaml"
    Then the exit code is 1
    And the error says "Spool.yaml: modules[0].ingester.configuration.every"

  @DEV-99
  Scenario: A profile is applied as when a node starts
    Given the descriptor "Spool.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
        inbox: {kind: INBOX, type: FILE_SYSTEM, configuration: {path: target/cli-test/inbox}}
        lake: {kind: DATA_LAKE, type: FILE_SYSTEM, configuration: {path: target/cli-test/lake}}
      modules:
        - ingester: {type: FLUSH, id: orders-ingester, configuration: {every: 30s}}
      profiles:
        broken:
          modules:
            - ingester: {type: FLUSH, id: orders-ingester, configuration: {every: soon}}
      """
    When I run "spool validate Spool.yaml --profile broken"
    Then the exit code is 1
    And the error says "modules[0].ingester.configuration.every"

  @DEV-99
  Scenario: A variable of the environment is applied as when a node starts
    Given the descriptor "Spool.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
        inbox: {kind: INBOX, type: FILE_SYSTEM, configuration: {path: "${env:SPOOL_INBOX_PATH}"}}
        lake: {kind: DATA_LAKE, type: FILE_SYSTEM, configuration: {path: target/cli-test/lake}}
      modules:
        - ingester: {type: REACTIVE, id: orders-ingester}
      """
    When I run "spool validate Spool.yaml"
    Then the exit code is 1
    And the error says "SPOOL_INBOX_PATH"
    When the environment has SPOOL_INBOX_PATH set to "target/cli-test/inbox"
    And I run "spool validate Spool.yaml"
    Then the exit code is 0

  @DEV-99
  Scenario: The classes of the project are taken from the classpath given
    Given the descriptor "Spool.yaml" uses the aggregator "fixture.RunningTotal" of the project
    And the project classes are compiled into "project/classes"
    When I run "spool validate Spool.yaml"
    Then the exit code is 1
    And the error says "fixture.RunningTotal"
    When I run "spool validate Spool.yaml --classpath project/classes"
    Then the exit code is 0

  @DEV-99 @maven
  Scenario: Inside a Maven project, the classes of the project are found without --classpath
    Given a Maven project with the aggregator "fixture.RunningTotal" and the descriptor "Spool.yaml" that uses it
    And the project is compiled
    When I run "spool validate Spool.yaml" in the project
    Then the exit code is 0
