Feature: spool deploys a pipeline into a system, whose shared files are the ones it includes

  With --system, spool deploy puts the descriptor in that system of the Spool API as a pipeline, and sends the files
  it includes as files the system shares, so other pipelines of the system can include them too (ADR-036). The system
  is created if it does not exist. spool systems lists the systems and spool withdraw --system takes a pipeline, or the
  whole system, out.

  Background:
    Given a Spool API that accepts the secret "deploy-secret" and has the live node "node-a"
    And the environment has SPOOL_API_SECRET set to "deploy-secret"
    And the descriptor "shared/infrastructure.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
      """
    And the descriptor "boe-crawler.spool.yaml":
      """
      include: [shared/infrastructure.yaml]
      modules: []
      """

  @DEV-146
  Scenario: A pipeline is deployed into a system, with what it includes as shared files
    When I run "spool deploy boe-crawler.spool.yaml --system boe --api {api}"
    Then the exit code is 0
    And the output says "boe-crawler of boe is deployed on node-a, in its version 1"
    And the API has the system "boe" with the shared file "shared/infrastructure.yaml"
    And the API has the pipeline "boe-crawler" of "boe" with the main file "boe-crawler.spool.yaml"

  @DEV-146
  Scenario: The systems are listed with their pipelines
    Given I ran "spool deploy boe-crawler.spool.yaml --system boe --api {api}"
    When I run "spool systems --api {api}"
    Then the exit code is 0
    And the output says "boe  1 pipeline"
    And the output says "  boe-crawler  version 1  node-a  STARTING"

  @DEV-146
  Scenario: A whole system is withdrawn
    Given I ran "spool deploy boe-crawler.spool.yaml --system boe --api {api}"
    When I run "spool withdraw --system boe --api {api}"
    Then the exit code is 0
    And the output says "boe is withdrawn, with 1 pipeline"
