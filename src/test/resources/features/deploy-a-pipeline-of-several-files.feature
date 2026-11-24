Feature: spool deploy sends a descriptor with every file it includes

  A descriptor can include other files, which include others in turn (ADR-016). spool deploy follows its includes and
  sends the main file with each of them, by its path relative to the main file, so the API and the node see the
  pipeline as it is in the folder (ADR-036).

  Background:
    Given a Spool API that accepts the secret "deploy-secret" and has the live node "node-a"
    And the environment has SPOOL_API_SECRET set to "deploy-secret"

  @DEV-145
  Scenario: The main file is sent with the files it includes, and with the ones those include
    Given the descriptor "shared/bus.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
      """
    And the descriptor "shared/infrastructure.yaml":
      """
      include: [bus.yaml]
      """
    And the descriptor "orders.spool.yaml":
      """
      include: [shared/infrastructure.yaml]
      modules: []
      """
    When I run "spool deploy orders.spool.yaml --api {api}"
    Then the exit code is 0
    And the API received "orders" with the main file "orders.spool.yaml" and the files "shared/infrastructure.yaml" and "shared/bus.yaml"

  @DEV-145
  Scenario: An include of a file that does not exist stops before sending anything
    Given the descriptor "orders.spool.yaml":
      """
      include: [shared/missing.yaml]
      modules: []
      """
    When I run "spool deploy orders.spool.yaml --api {api}"
    Then the exit code is 1
    And the error says "shared/missing.yaml"
    And the API received nothing
