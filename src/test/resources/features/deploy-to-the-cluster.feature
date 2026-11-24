Feature: spool deploys, lists and withdraws descriptors in the Spool API

  spool deploy sends a descriptor to the Spool API under a name, the name of its file by default, and says where it
  went. spool deployments lists what is deployed, and spool withdraw takes a deployment out. The API is the one in
  --api, or in SPOOL_API. Every request is signed with the secret in SPOOL_API_SECRET, or carries the token in
  SPOOL_API_TOKEN; neither is ever an argument. An error of the API ends with exit code 1 and its message on the
  error output.

  Background:
    Given a Spool API that accepts the secret "deploy-secret" and has the live node "node-a"
    And the environment has SPOOL_API_SECRET set to "deploy-secret"

  @DEV-139
  Scenario: A descriptor is deployed under the name of its file
    Given the descriptor "orders.spool.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
      modules: []
      """
    When I run "spool deploy orders.spool.yaml --api {api}"
    Then the exit code is 0
    And the output says "orders is deployed on node-a, in its version 1"
    And the API received the descriptor "orders.spool.yaml" as "orders", signed with the secret

  @DEV-139
  Scenario: A descriptor is deployed under the name given, with the API of SPOOL_API
    Given the descriptor "Spool.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
      modules: []
      """
    And the environment variable "SPOOL_API" is the address of the API
    When I run "spool deploy Spool.yaml --name payments"
    Then the exit code is 0
    And the output says "payments is deployed on node-a, in its version 1"

  @DEV-139
  Scenario: A deployment with no live node says it waits
    Given the API has no live node
    And the descriptor "orders.spool.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
      modules: []
      """
    When I run "spool deploy orders.spool.yaml --api {api}"
    Then the exit code is 0
    And the output says "orders is deployed and waits for a live node"

  @DEV-139
  Scenario: A descriptor the API refuses ends with its error
    Given the descriptor "orders.spool.yaml":
      """
      infrastructure:
        lake: {type: FILE_SYSTEM, configuration: {path: /data/lake}}
      modules: []
      """
    When I run "spool deploy orders.spool.yaml --api {api}"
    Then the exit code is 1
    And the error says "infrastructure.lake.kind"

  @DEV-139
  Scenario: The deployments are listed with their node and their state
    Given the API has the deployment "orders" running on "node-a" and "payments" waiting for a node
    When I run "spool deployments --api {api}"
    Then the exit code is 0
    And the output says "orders  version 1  node-a  RUNNING"
    And the output says "payments  version 1  -  WAITING"

  @DEV-139
  Scenario: A deployment is withdrawn
    Given the API has the deployment "orders" running on "node-a" and "payments" waiting for a node
    When I run "spool withdraw orders --api {api}"
    Then the exit code is 0
    And the output says "orders is withdrawn"

  @DEV-139
  Scenario: Withdrawing a deployment that does not exist ends with the error of the API
    When I run "spool withdraw unknown --api {api}"
    Then the exit code is 1
    And the error says "unknown is not deployed"

  @DEV-139
  Scenario: Without credentials, nothing is sent
    Given the environment has neither "SPOOL_API_SECRET" nor "SPOOL_API_TOKEN"
    When I run "spool deployments --api {api}"
    Then the exit code is 1
    And the error says "SPOOL_API_SECRET or SPOOL_API_TOKEN"
    And the API received nothing
