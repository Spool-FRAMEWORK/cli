Feature: spool deploy asks for the labels of the nodes a deployment can run on

  spool deploy --label key=value, once per label, deploys asking for nodes with every one of those labels (ADR-036).
  When no live node has them, the deployment waits, and spool says which labels it needs.

  Background:
    Given a Spool API that accepts the secret "deploy-secret" and has the live node "node-a" with the labels "cloud=aws"
    And the environment has SPOOL_API_SECRET set to "deploy-secret"
    And the descriptor "orders.spool.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
      modules: []
      """

  @DEV-143
  Scenario: A deployment asks for the labels given with --label
    When I run "spool deploy orders.spool.yaml --label cloud=aws --label region=eu-west-1 --api {api}"
    Then the API received the descriptor "orders.spool.yaml" as "orders", asking for the labels "cloud=aws,region=eu-west-1"

  @DEV-143
  Scenario: A deployment with no live node with its labels says which labels it needs
    When I run "spool deploy orders.spool.yaml --label cloud=azure --api {api}"
    Then the exit code is 0
    And the output says "orders is waiting for a node with the labels cloud=azure"

  @DEV-143
  Scenario: A label not written as key=value is refused before calling the API
    When I run "spool deploy orders.spool.yaml --label aws --api {api}"
    Then the exit code is not 0
    And the error output says "key=value"
    And the API received nothing
