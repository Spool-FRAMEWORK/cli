Feature: spool terraform writes the Terraform of a descriptor, and spool provision applies it only when told

  spool terraform writes the Terraform of the infrastructure of a descriptor for a target, as the provisioning
  library does (ADR-024). spool provision generates it and applies it with the Terraform installed on the machine, and
  only after an explicit confirmation, because in the cloud it creates resources that cost money.

  Background:
    Given the descriptor "Spool.yaml":
      """
      infrastructure:
        bus: {kind: EVENT_BUS, type: IN_MEMORY}
        inbox: {kind: INBOX, type: FILE_SYSTEM, configuration: {path: target/cli-test/inbox}}
        lake: {kind: DATA_LAKE, type: FILE_SYSTEM, configuration: {path: target/cli-test/lake}}
      modules:
        - ingester: {type: REACTIVE, id: orders-ingester}
      """

  @DEV-99
  Scenario: The Terraform is written to a folder
    When I run "spool terraform Spool.yaml --target local --out infrastructure"
    Then the exit code is 0
    And the folder "infrastructure" has the file "main.tf.json"
    And the output says "Terraform for local written to infrastructure"

  @DEV-99
  Scenario: A target that does not exist names the targets there are
    When I run "spool terraform Spool.yaml --target mars --out infrastructure"
    Then the exit code is 2
    And the error says "local, aws"

  @DEV-99
  Scenario: Nothing is applied without a confirmation
    Given Terraform is installed
    When I run "spool provision Spool.yaml --target local" and answer "n"
    Then the exit code is 1
    And Terraform was not run with "apply"
    And the output says "Nothing applied"

  @DEV-99
  Scenario: The confirmation applies what was generated
    Given Terraform is installed
    When I run "spool provision Spool.yaml --target local" and answer "y"
    Then the exit code is 0
    And Terraform was run with "init" and then with "apply" in the folder of the generated Terraform

  @DEV-99
  Scenario: --yes is the confirmation
    Given Terraform is installed
    When I run "spool provision Spool.yaml --target local --yes"
    Then the exit code is 0
    And Terraform was run with "apply"

  @DEV-99
  Scenario: Without Terraform installed, nothing is generated and the error says so
    Given Terraform is not installed
    When I run "spool provision Spool.yaml --target local --yes"
    Then the exit code is 1
    And the error says "Terraform is not installed"
