Feature: The spool command installs with one line

  install.sh (Linux and macOS) and install.ps1 (Windows) download the spool.zip of the latest release and its SHA-256,
  check it, unpack it into ~/.spool, or SPOOL_HOME, and put its bin folder on the PATH of the user, without asking for
  administrator rights. They never run an installer of their own, so the system does not flag anything (ADR-029).
  Each scenario runs with every script whose shell is on the machine, against a release in a local folder.

  @DEV-119
  Scenario Outline: The latest release is installed and answers
    Given a release with spool.zip and its SHA-256
    When <script> runs
    Then it ends with exit code 0
    And spool --version answers from the bin folder of the installation

    Examples:
      | script      |
      | install.sh  |
      | install.ps1 |

  @DEV-119
  Scenario: install.sh puts spool on the PATH of the user
    Given a release with spool.zip and its SHA-256
    When install.sh runs
    Then the profile of the user adds the bin folder of the installation to the PATH
    And running install.sh again does not add it twice

  @DEV-119
  Scenario Outline: With SPOOL_NO_MODIFY_PATH, the PATH is left alone and the script says what to add
    Given a release with spool.zip and its SHA-256
    And SPOOL_NO_MODIFY_PATH is set
    When <script> runs
    Then the PATH of the user is not changed
    And the script says to add the bin folder of the installation to the PATH

    Examples:
      | script      |
      | install.sh  |
      | install.ps1 |

  @DEV-119
  Scenario Outline: A zip that does not match its SHA-256 installs nothing
    Given a release whose spool.zip does not match its SHA-256
    When <script> runs
    Then it ends with an error that says "SHA-256"
    And nothing is installed

    Examples:
      | script      |
      | install.sh  |
      | install.ps1 |

  @DEV-119
  Scenario Outline: Without Java 21, the script says so and offers a JRE
    Given a release with spool.zip and its SHA-256
    And there is no Java on the PATH
    And SPOOL_INSTALL_JRE is "no"
    When <script> runs
    Then the script says "Java 21"
    And it offers to download a Temurin JRE into the installation
    And spool is installed all the same

    Examples:
      | script      |
      | install.sh  |
      | install.ps1 |
